package com.appsonair.applink.services

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.appsonair.applink.interfaces.AppLinkListener
import com.appsonair.applink.utils.StringConst
import com.appsonair.core.services.CoreService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeUnit

class AppLinkService private constructor(private val context: Context) {


    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AppLinkService? = null

        fun getInstance(context: Context): AppLinkService {
            return instance ?: synchronized(this) {
                instance ?: AppLinkService(context.applicationContext).also { instance = it }
            }
        }
    }

    private var appLinkListener: AppLinkListener? = null
    private var referralLink = JSONObject()

    /**
     * The most recent successful post-expiry IP lookup. Held in memory only — like the response
     * [currentReferral] returns, it describes the lookup that ran, not the install, so it must
     * not replace the stored referral later launches read.
     */
    @Volatile
    private var refreshedReferral: JSONObject? = null

    @Volatile
    private var refreshInFlight = false
    private var referralDeferred: CompletableDeferred<JSONObject>? = null
    private var isFirstLaunch = false
    private var installReferrerParams = JSONObject()
    private var clickTime = 0L
    private var attributionStatus = StringConst.Organic
    private var attributionTtlSeconds: Long? = null
    private var installReported = false
    private var launchStateResolved = false
    private var startedActivityCount = 0
    private var foregroundObserverRegistered = false
    private var initialized = false
    private var isBackgrounded = false

    /**
     * Set when backgrounding ends the first launch, and cleared by the return that follows.
     * One-shot, so the attribution payload is re-delivered exactly once.
     */
    private var firstLaunchExpired = false

    /**
     * The single entry point. [AppLinkListener] carries every callback: [
     * AppLinkListener.onDeepLinkProcessed], [AppLinkListener.onDeepLinkError],
     * [AppLinkListener.onReferralLinkDetected] (deprecated, detection-only, `appsFlyer`
     * removed) and [AppLinkListener.onAttributionListener], so one registration serves both
     * the referral and the attribution surface.
     */
    fun initialize(context: Context, intent: Intent, listener: AppLinkListener) {
        this.appLinkListener = listener
        startInitialization(context, intent)
    }

    /**
     * Runs the one-time setup behind [initialize].
     *
     * Idempotent, because [initialize] can legitimately be called more than once: a cross-platform
     * wrapper re-registers its listener on reload, and a host app may call it from more than one
     * place. The listener assignment in [initialize] still takes effect, but repeating this work
     * would re-deliver the same intent and fire onDeepLinkProcessed twice, as well as reconnect the
     * install referrer client. iOS guards its deep link subscription the same way.
     *
     * A new intent arriving later is delivered through [handleDeepLink], not through here.
     */
    private fun startInitialization(context: Context, intent: Intent) {
        if (initialized) return
        initialized = true

        NetworkWatcherService.checkNetworkConnection(context)
        AppLinkHandler.appsOnAirAppId = CoreService.getAppId(context)
        resolveLaunchState(context)

        // Fetch install referrer (if needed for initialization)
        fetchInstallReferrer {
            Log.d("AppLinkService", "Install Referrer fetched successfully!!")
        }
        // Handle deep link processing immediately
        handleDeepLink(intent, "com.example.appsonair_android_applink")
    }

    /**
     * Resolves the launch state used by the attribution fields.
     *
     * [isFirstLaunch] is true on the very first launch after installation and stays true only
     * while the app remains in the foreground. See [observeForegroundState].
     */
    private fun resolveLaunchState(context: Context) {
        // Resolved once per process: reading isFirstOpen also writes it, so a second
        // initialize() call would otherwise report isFirstLaunch as false.
        if (launchStateResolved) return
        launchStateResolved = true

        ServerTimeService.initialize(context)

        val prefs = context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
        isFirstLaunch = !prefs.getBoolean("isFirstOpen", false)
        if (isFirstLaunch) {
            prefs.edit().putBoolean("isFirstOpen", true).apply()
        }
        installReferrerParams = getJsonFromPrefs(context, "install_referrer_params") ?: JSONObject()
        clickTime = prefs.getLong("click_time", 0L)
        attributionStatus = prefs.getString("attribution_status", StringConst.Organic)
            ?: StringConst.Organic
        attributionTtlSeconds = prefs.getLong("attribution_ttl", -1L).takeIf { it >= 0L }
        installReported = prefs.getBoolean("install_reported", false)
        observeForegroundState(context)
    }

    /**
     * Tracks the foreground state to mirror the AppsFlyer session model.
     *
     * Leaving the foreground (backgrounded or phone locked) ends the first launch, so
     * [isFirstLaunch] turns false without waiting for the process to restart. Coming back
     * re-delivers the attribution payload, so the listener sees the current value instead of
     * the one captured on the first launch.
     */
    private fun observeForegroundState(context: Context) {
        if (foregroundObserverRegistered) return

        val application = context.applicationContext as? Application
        if (application == null) {
            Log.d("AppLinkService", "Application unavailable, first launch ends with the process")
            return
        }
        foregroundObserverRegistered = true

        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount++
                // Only a real return from the background counts, not the first activity start.
                if (startedActivityCount != 1 || !isBackgrounded) return

                isBackgrounded = false

                // One-shot: only the return that follows isFirstLaunch expiring re-delivers the
                // payload. Later returns read the same persisted state, so they carry nothing new.
                if (!firstLaunchExpired) return
                firstLaunchExpired = false
                notifyAttributionOnForeground()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount--
                // A configuration change stops the activity too, but it is restarted right
                // after, so it does not count as the app leaving the foreground.
                if (startedActivityCount > 0 || activity.isChangingConfigurations) return

                startedActivityCount = 0
                isBackgrounded = true
                if (isFirstLaunch) {
                    isFirstLaunch = false
                    firstLaunchExpired = true
                    Log.d(
                        "AppLinkService",
                        "App left the foreground, isFirstLaunch is now false, " +
                            "will notify on next return"
                    )
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Re-delivers the cached attribution payload when the app returns to the foreground.
     *
     * Goes straight to the listener instead of [notifyAttributionDetected], so the persisted
     * attribution status is not recomputed from cached data and the deprecated
     * onReferralLinkDetected() keeps its detection-only behaviour.
     */
    private fun notifyAttributionOnForeground() {
        val listener = appLinkListener ?: return
        val cached = referralLink.takeIf { it.length() > 0 }
            ?: getJsonFromPrefs(context, "referral_details")
            ?: JSONObject()
        Log.d("AppLinkService", "App returned to the foreground, isFirstLaunch=$isFirstLaunch")
        listener.onAttributionListener(withAttribution(cached))
    }

    /**
     * Resolves the click time from the install referrer.
     *
     * When the referrer carries the AppsFlyer params (pid appsonair, af_ad and any af_sub*),
     * af_ad_id holds the click time. Otherwise it comes from applink_click_time on the
     * appsonair_app_link itself.
     */
    private fun resolveClickTime(referrerUri: Uri, appsOnAirAppLink: String): Long {
        val pid = referrerUri.getQueryParameter("pid").orEmpty()
        val afAd = referrerUri.getQueryParameter("af_ad").orEmpty()
        val hasAfSub = referrerUri.queryParameterNames.any {
            it.startsWith("af_sub") && !referrerUri.getQueryParameter(it).isNullOrEmpty()
        }

        val rawClickTime = if (pid == "appsonair" && afAd.isNotEmpty() && hasAfSub) {
            referrerUri.getQueryParameter("af_ad_id")
        } else {
            // applink_click_time may sit on the appsonair_app_link itself or alongside it
            // as a referrer param, so check both.
            appsOnAirAppLink.takeIf { it.isNotEmpty() }?.let {
                val appLinkUri = Uri.parse(if (it.startsWith("http")) it else "https://$it")
                appLinkUri.getQueryParameter("applink_click_time")
            }?.takeIf { it.isNotEmpty() }
                ?: referrerUri.getQueryParameter("applink_click_time")
        }
        return parseTimestamp(rawClickTime)
    }

    /**
     * Parses an epoch timestamp given in either seconds or milliseconds and returns it in
     * milliseconds, so it can be compared against [getFirstInstallTime]. Returns 0 when missing
     * or not a valid timestamp.
     */
    private fun parseTimestamp(value: String?): Long {
        val epoch = value?.trim()?.toLongOrNull()
        if (epoch == null || epoch <= 0L) {
            Log.d("AppLinkService", "Click time missing or invalid: $value")
            return 0L
        }
        return if (epoch < 1_000_000_000_000L) epoch * 1000L else epoch
    }

    /**
     * Reads attributionTtl (in seconds) from the referral response, checking the data object
     * first and then the top level, and remembers it.
     *
     * Only the link response carries the ttl. Once the stored referral is the IP lookup result
     * — or an error object — it is no longer in the payload, so the remembered value stands in
     * for it and the window keeps being measured. Returns null when no response has carried it.
     */
    private fun attributionTtlFrom(result: JSONObject): Long? {
        val source = result.optJSONObject("data")?.takeIf { it.has("attributionTtl") } ?: result
        val ttlSeconds = source.optLong("attributionTtl", -1L)
            .takeIf { source.has("attributionTtl") && it >= 0L }
            ?: return attributionTtlSeconds

        if (ttlSeconds != attributionTtlSeconds) {
            attributionTtlSeconds = ttlSeconds
            context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
                .edit().putLong("attribution_ttl", ttlSeconds).apply()
        }
        return ttlSeconds
    }

    /**
     * An install is non-organic when it happened within attributionTtl of the click.
     * Any missing or invalid input falls back to organic.
     */
    private fun resolveAttributionStatus(result: JSONObject): String {
        if (clickTime <= 0L) return StringConst.Organic

        val firstInstallTime = getFirstInstallTime()
        if (firstInstallTime <= 0L) {
            Log.d("AppLinkService", "First install time unavailable, defaulting to organic")
            return StringConst.Organic
        }

        val ttlSeconds = attributionTtlFrom(result)
        if (ttlSeconds == null) {
            Log.d("AppLinkService", "attributionTtl unavailable, defaulting to organic")
            return StringConst.Organic
        }

        val differenceSeconds = (firstInstallTime - clickTime) / 1000
        return if (differenceSeconds <= ttlSeconds) {
            StringConst.NonOrganic
        } else {
            StringConst.Organic
        }
    }

    /**
     * True when [result] is a referral the server actually matched, rather than an error or an
     * empty body. Both identifiers are required, since they are what names the link this install
     * came from — [getReferralUsingIp] counts the install off the same pair.
     */
    private fun hasReferralMatch(result: JSONObject): Boolean {
        if (result.has("error")) return false
        val data = result.optJSONObject("data") ?: return false
        return data.optString("shortId").isNotEmpty() &&
            data.optString("referralLink").isNotEmpty()
    }

    /**
     * The status for a referral matched by IP instead of by the install referrer.
     *
     * The referrer carried no usable click time — it named no link, or the click param was missing
     * or unparseable — so [resolveAttributionStatus] has nothing to measure and would report
     * organic on that basis alone. The IP lookup can still name the link this install came from,
     * so the match decides it instead: attributed while it is inside attributionTtl, organic once
     * it is not.
     *
     * There is no click to measure from, so [isAttributionTtlExpired] runs the window from the
     * install. It also treats a ttl that has never been seen, and a clock that has been wound
     * back, as expired — both leave the match unverifiable, which is the organic answer either way.
     */
    private fun resolveIpAttributionStatus(result: JSONObject): String {
        if (!hasReferralMatch(result)) {
            Log.d("AppLinkService", "IP lookup matched no referral, defaulting to organic")
            return StringConst.Organic
        }
        return if (isAttributionTtlExpired(result)) {
            Log.d("AppLinkService", "IP referral matched but attributionTtl has elapsed")
            StringConst.Organic
        } else {
            StringConst.NonOrganic
        }
    }

    private fun updateAttributionStatus(result: JSONObject, matchedByIp: Boolean = false) {
        attributionStatus = if (matchedByIp) {
            resolveIpAttributionStatus(result)
        } else {
            resolveAttributionStatus(result)
        }
        Log.d("AppLinkService", "Attribution status resolved: $attributionStatus")
        context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
            .edit().putString("attribution_status", attributionStatus).apply()
    }

    /**
     * Stores every install referrer param except appsonair_app_link, so they stay available
     * on later launches when the referrer is no longer fetched.
     */
    private fun storeInstallReferrerParams(referrerUri: Uri) {
        val params = JSONObject()
        referrerUri.queryParameterNames
            .filter { it != "appsonair_app_link" }
            .forEach { params.put(it, referrerUri.getQueryParameter(it).orEmpty()) }
        installReferrerParams = params
        saveJsonToPrefs(context, "install_referrer_params", params)
    }

    private fun storeClickTime(referrerUri: Uri, appsOnAirAppLink: String) {
        clickTime = resolveClickTime(referrerUri, appsOnAirAppLink)
        context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
            .edit().putLong("click_time", clickTime).apply()
    }

    /**
     * Reports the install to the link count API and waits up to the timeout for its result.
     *
     * [installReported] turns true only here, and only when the call succeeds. It is persisted
     * so the install is counted once and later launches do not report it again.
     */
    private suspend fun countInstall(linkId: String, domain: String) {
        val counted = CompletableDeferred<Boolean>()
        AppLinkHandler.handleLinkCount(
            linkId,
            domain,
            isClicked = false,
            isFirstOpen = true,
            isInstall = true,
        ) { isSuccessful ->
            if (isSuccessful && !installReported) {
                installReported = true
                context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
                    .edit().putBoolean("install_reported", true).apply()
            }
            counted.complete(isSuccessful)
        }
        withTimeoutOrNull(2_000L) { counted.await() }
    }

    /**
     * The install referrer keys exposed in the attribution payload.
     *
     * With AppsFlyer enabled the link carries a copy of its detail (`pid`, `c` and the `af_`
     * prefixed params). That copy is dropped, so the `appsFlyer` object returned by the API stays
     * the only place the AppsFlyer detail is read from.
     */
    private fun exposedInstallReferrerKeys(): List<String> {
        val keys = installReferrerParams.keys().asSequence().toList()
        if (keys.none { it.startsWith("af_") }) return keys
        return keys.filterNot { it.startsWith("af_") || it == "pid" || it == "c" }
    }

    /**
     * Returns a copy of [result] with the attribution fields added, leaving the
     * original untouched so the deprecated referral APIs keep their existing payload.
     */
    private fun withAttribution(result: JSONObject): JSONObject {
        return JSONObject(result.toString()).apply {
            val referralData = optJSONObject("data")
            val data = referralData ?: JSONObject().also { put("data", it) }
            data.put("isFirstLaunch", isFirstLaunch)
            data.put("firstInstallTime", getFirstInstallTime())
            // Consumed means the click was attributed to this install. An install outside
            // attributionTtl is organic, so nothing was claimed.
            data.put("isConsumed", attributionStatus == StringConst.NonOrganic)
            data.put("attributionStatus", attributionStatus)
            // The click time behind attributionStatus: af_ad_id with AppsFlyer enabled, and
            // applink_click_time on the appsonair_app_link otherwise. Normalised to epoch
            // milliseconds by resolveClickTime, so it lines up with firstInstallTime. Left out
            // when there is no click to report.
            if (clickTime > 0L) {
                data.put("applink_click_time", clickTime)
            }
            // Install referrer params, without overriding the link details from the response
            exposedInstallReferrerKeys().forEach { key ->
                if (!data.has(key)) {
                    data.put(key, installReferrerParams.get(key))
                }
            }
        }
    }

    private fun getFirstInstallTime(): Long {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            Log.e("AppLinkService", "Failed to read first install time: ${e.message}")
            0L
        }
    }

    private fun notifyDeepLinkProcessed(uri: Uri, result: JSONObject) {
        appLinkListener?.onDeepLinkProcessed(uri, result)
    }

    private fun notifyDeepLinkError(uri: Uri?, error: String) {
        appLinkListener?.onDeepLinkError(uri, error)
    }

    /**
     * Returns a copy of [result] without the `appsFlyer` object, leaving the original untouched.
     * The API returns `appsFlyer` on the dynamic-link and referral/details endpoints, but it is
     * part of the newer attribution surface: only [getAttributionInfo] and
     * [AppLinkListener.onAttributionListener] expose it, so the deprecated referral APIs and
     * the deprecated [AppLinkListener.onReferralLinkDetected] callback keep their original
     * payload shape.
     * Removed at both levels because the key may sit at the root or inside `data`.
     */
    private fun withoutAppsFlyer(result: JSONObject): JSONObject {
        return JSONObject(result.toString()).apply {
            remove("appsFlyer")
            optJSONObject("data")?.remove("appsFlyer")
        }
    }

    @Suppress("DEPRECATION")
    private fun notifyAttributionDetected(result: JSONObject, matchedByIp: Boolean = false) {
        updateAttributionStatus(result, matchedByIp)
        appLinkListener?.onReferralLinkDetected(withoutAppsFlyer(result))
        appLinkListener?.onAttributionListener(withAttribution(result))
    }

    suspend fun createAppLink(
        url: String,
        name: String,
        urlPrefix: String,
        shortId: String? = null,
        socialMeta: Map<String, Any>? = null,
        isOpenInBrowserAndroid: Boolean? = null,
        isOpenInAndroidApp: Boolean? = null,
        androidFallbackUrl: String? = null,
        isOpenInBrowserApple: Boolean? = null,
        isOpenInIosApp: Boolean? = null,
        iosFallbackUrl: String? = null,
        appsFlyer: Map<String, Any>? = null,
        attributionTtl: Int? = null,
    ): JSONObject {

        return AppLinkHandler.createAppLink(
            name = name,
            url = url,
            urlPrefix = urlPrefix,
            shortId = shortId,
            socialMeta = socialMeta,
            isOpenInBrowserAndroid = isOpenInBrowserAndroid,
            isOpenInAndroidApp = isOpenInAndroidApp,
            androidFallbackUrl = androidFallbackUrl,
            isOpenInBrowserApple = isOpenInBrowserApple,
            isOpenInIosApp = isOpenInIosApp,
            iosFallbackUrl = iosFallbackUrl,
            appsFlyer = appsFlyer,
            attributionTtl = attributionTtl,
        )
    }

    /**
     * Serves the freshest referral already held: the last post-expiry lookup when one has run,
     * the stored referral otherwise.
     *
     * This call does not suspend, so it cannot wait for a lookup the way [getReferralInfo] and
     * [getAttributionInfo] do. Once attributionTtl has elapsed it starts one in the background
     * instead, which leaves the first expired call returning the stored referral and later calls
     * returning the refreshed one.
     */
    @Deprecated(
        message = "Use getAttributionInfo() instead",
        replaceWith = ReplaceWith("getAttributionInfo()")
    )
    fun getReferralDetails(): JSONObject {
        val latest = refreshedReferral ?: referralLink
        if (isAttributionTtlExpired(latest)) {
            refreshReferralInBackground()
        }
        return withoutAppsFlyer(latest)
    }

    @Deprecated(
        message = "Use getAttributionInfo() instead",
        replaceWith = ReplaceWith("getAttributionInfo()")
    )
    suspend fun getReferralInfo(): JSONObject {
        return withoutAppsFlyer(currentReferral())
    }

    /**
     * True when attributionTtl has elapsed since the click, measured against the clock right now
     * rather than against the install time. This is a live check, so it is independent of
     * [attributionStatus]: an install attributed at first launch still expires once enough time
     * passes.
     *
     * Unknown inputs count as expired: a ttl that has never been seen, or neither a click time nor
     * an install time to measure the window from. Expiry cannot be demonstrated in either case, so
     * the referral is refreshed rather than trusted. A device clock that predates the click or the
     * install also counts as expired: see the comment below.
     */
    private fun isAttributionTtlExpired(referral: JSONObject): Boolean {
        val ttlSeconds = attributionTtlFrom(referral) ?: return true

        val firstInstallTime = getFirstInstallTime()
        // The window runs from the click. An organic install has none, and neither does a
        // referrer that carried no click time, so the install stands in for it: the stored
        // referral stops being current once the ttl has passed either way.
        val windowStart = if (clickTime > 0L) clickTime else firstInstallTime
        if (windowStart <= 0L) return true

        // Corrected by the last server Date header, so a device clock that is merely wrong still
        // measures the window correctly. See ServerTimeService for what this does and does not stop.
        val now = ServerTimeService.now()

        // Time only moves forward. Corrected now falling behind something already observed means
        // the clock was wound back between observations.
        if (ServerTimeService.hasClockRewound()) {
            Log.d("AppLinkService", "Clock moved backwards, treating attribution as expired")
            return true
        }

        // clickTime is stamped by the link service and firstInstallTime by PackageManager, so
        // neither can be edited from Settings. A now that predates either one is impossible on an
        // honest clock, and would otherwise shrink the elapsed time and hold the window open.
        // Treat it as expired so the backend decides, rather than trusting a clock that has lied.
        if (now < windowStart || (firstInstallTime > 0L && now < firstInstallTime)) {
            Log.d(
                "AppLinkService",
                "Clock ($now) predates click ($clickTime) or install ($firstInstallTime), " +
                    "treating attribution as expired"
            )
            return true
        }

        ServerTimeService.observe(now)

        val elapsedSeconds = (now - windowStart) / 1000
        return elapsedSeconds > ttlSeconds
    }

    /**
     * The referral that describes this install right now.
     *
     * Once attributionTtl has elapsed since the click — or since the install, when there is no
     * click — the stored referral no longer describes this install, so it is re-read from the IP
     * lookup on every call and that response is returned in its place. This depends on the ttl
     * alone, not on [attributionStatus]: an organic install refreshes on the same schedule.
     * Inside the window the stored referral is served as-is.
     *
     * The refreshed response is deliberately not stored: it describes the lookup that just ran,
     * not the install, so it must not replace the referral later launches read.
     *
     * A failed lookup falls back to the stored referral rather than surfacing the error object
     * to the host app.
     */
    private suspend fun currentReferral(): JSONObject {
        // The ttl lives inside the stored referral, so it has to be read before the check.
        val storedReferral = awaitReferralInfo()
        if (!isAttributionTtlExpired(storedReferral)) return storedReferral
        return lookupReferralByIP() ?: storedReferral
    }

    /**
     * Runs the IP lookup and caches it in [refreshedReferral], so [getReferralDetails] can serve
     * it without suspending. Returns null when the lookup fails, leaving callers to fall back to
     * the stored referral.
     */
    private suspend fun lookupReferralByIP(): JSONObject? {
        // getUserAgent() instantiates a WebView, which must happen on the main thread.
        val ipResult = AppLinkHandler.getReferralLinkByIP(
            withContext(Dispatchers.Main) { getUserAgent() }
        )
        if (ipResult.length() > 0 && !ipResult.has("error")) {
            refreshedReferral = ipResult
            return ipResult
        }

        Log.d(
            "AppLinkService",
            "IP referral lookup failed after attributionTtl expiry, falling back to stored referral"
        )
        return null
    }

    /**
     * Starts a lookup for [getReferralDetails], which cannot suspend to wait for one. At most one
     * runs at a time, so repeated calls past expiry do not pile up requests.
     */
    private fun refreshReferralInBackground() {
        if (refreshInFlight) return
        refreshInFlight = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                lookupReferralByIP()
            } finally {
                refreshInFlight = false
            }
        }
    }

    /**
     * Returns the attribution details — [isFirstLaunch], [firstInstallTime], applink_click_time,
     * [isConsumed] and [attributionStatus] — on top of the referral [currentReferral] resolves.
     *
     * [attributionStatus] is deliberately left alone: it stays as it resolved at install time, so
     * it and [isConsumed] keep reporting what was attributed even once the payload carries the IP
     * based referral.
     */
    suspend fun getAttributionInfo(): JSONObject {
        return withAttribution(currentReferral())
    }

    private suspend fun awaitReferralInfo(): JSONObject {
        // Case 1: already in memory
        if (referralLink.length() > 0) {
            return referralLink
        }

        // Case 2: check shared prefs
        val cached = getJsonFromPrefs(context, "referral_details")
        if (cached != null && cached.length() > 0) {
            referralLink = cached
            return cached
        }

        // Case 3: wait until referralLink is set by API call
        referralDeferred = CompletableDeferred()
        return referralDeferred!!.await()
    }

    /**
     * Processes the deep link intent.
     *
     * 1. Extracts query parameters from the deep link.
     * 2. Handles success or error internally and notifies the listener.
     *
     * @param intent The intent received in the activity or fragment.
     * @param fallbackPackageName The package name to redirect to the Play Store if the app isn't found.
     */
    fun handleDeepLink(
        intent: Intent,
        fallbackPackageName: String,
        source: String? = null,
        fallbackUrl: String? = null
    ) {
        val uri = intent.data
        if (uri != null) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    delay(500)
                    onDeepLinkProcessed(uri)
                } catch (e: Exception) {
                    onDeepLinkError(uri, "Error processing deep link: ${e.message}")
                    handleFallback(fallbackPackageName, fallbackUrl, source)
                }
            }

        }
    }

    private fun handleFallback(
        packageName: String,
        fallbackUrl: String?,
        source: String? = null
    ) {
        if (!fallbackUrl.isNullOrEmpty()) {
            openFallbackUrl(fallbackUrl)
        } else {
            Log.d("HANDLE_FALLBACK", "package==>$packageName source==>${source.toString()}")
        }
    }

    /**
     * Retrieves referrer details from the Google Play Install Referrer API.
     */
    private fun fetchInstallReferrer(callback: (String) -> Unit) {
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        val prefs = context.getSharedPreferences("AnalyticsData", Context.MODE_PRIVATE)

        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        val response: ReferrerDetails = referrerClient.installReferrer
                        val referrerUrl = response.installReferrer
                        // Handle isInstall and isFirstOpen below
                        val uriPlaceHolder =
                            Uri.parse("https://appsonair.com?$referrerUrl") // creating placeholder uri to extract query params
                        val appsOnAirAppLink =
                            uriPlaceHolder.getQueryParameter("appsonair_app_link")
                                .orEmpty()
                        storeInstallReferrerParams(uriPlaceHolder)
                        storeClickTime(uriPlaceHolder, appsOnAirAppLink)
                        val schemeUri = Uri.parse(
                            if (appsOnAirAppLink.startsWith("http")) appsOnAirAppLink else "https://$appsOnAirAppLink"
                        )//Appending https if not exist in url to get accurate data
                        val linkId = schemeUri.lastPathSegment.orEmpty()
                        val domain = schemeUri.host.orEmpty()
                        val isAppInstalled = prefs.getBoolean("isAppInstalled", false)

                        //Added below condition to track install only once as this method always call till 90 days
                        if (!isAppInstalled) {
                            prefs.edit()
                                .putBoolean("isAppInstalled", true)
                                .apply()
                            if (linkId.isNotEmpty() && domain.isNotEmpty()) {
                                if (schemeUri.toString().isNotEmpty()) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        countInstall(linkId, domain)

                                        val referrerResult = getFullReferralDetails(
                                            domain,
                                            linkId,
                                            schemeUri.toString()
                                        )

                                        // The install referrer link only attributes this install
                                        // while the click sits inside attributionTtl. Once the click
                                        // falls outside it, that link is not this install's referral,
                                        // so its data is replaced by the IP based lookup.
                                        // resolveAttributionStatus also reports Organic when the
                                        // click time, install time or ttl are missing — the same
                                        // conclusion, since the link cannot be attributed either way.
                                        if (resolveAttributionStatus(referrerResult) == StringConst.Organic) {
                                            delay(750) // Wait for sometime to trigger listener

                                            val publicUserAgent = getUserAgent()
                                            val ipResult =
                                                AppLinkHandler.getReferralLinkByIP(publicUserAgent)

                                            // Overwrite what getFullReferralDetails cached, so the
                                            // referral APIs and later launches read the IP result.
                                            referralLink = ipResult
                                            saveJsonToPrefs(context, "referral_details", ipResult)
                                            referralDeferred?.complete(ipResult)
                                            referralDeferred = null

                                            notifyAttributionDetected(ipResult, matchedByIp = true)
                                        } else {
                                            notifyAttributionDetected(referrerResult)
                                        }
                                    }
                                }
                            } else {
                                // Fetch the referral link using IP if play referral API doesn't detect it
                                CoroutineScope(Dispatchers.Main).launch {
                                    getReferralUsingIp()
                                }
                            }

                        } else {
                            // For backward compatibility need to remove below code once remove deprecated referral method
                            val storedResult = getJsonFromPrefs(context, "referral_details")
                            referralLink = storedResult ?: JSONObject()
                        }

                        callback(schemeUri.toString())
                        referrerClient.endConnection()
                    }

                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        callback("Install referrer API not supported on this device.")
                    }

                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        callback("Install referrer service unavailable.")
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                callback("Install referrer service disconnected.")
            }
        })
    }

    private fun getUserAgent(): String {
        val webView = WebView(context)
        // Get the User-Agent that WebView will use
        val publicUserAgent = webView.settings.userAgentString ?: ""
        return publicUserAgent
    }

    suspend fun getReferralUsingIp() {
        val publicUserAgent = getUserAgent()
        val data = AppLinkHandler.getReferralLinkByIP(publicUserAgent)
        val dataObject = data.optJSONObject("data")
        referralLink = data
        saveJsonToPrefs(context, "referral_details", data)
        delay(500) // Wait for sometime to trigger listener
        referralDeferred?.complete(data) //  notify waiter

        if (dataObject != null) {
            val shortId = dataObject.optString("shortId", "")
            val referralLink = dataObject.optString("referralLink", "")

            if (shortId.isNotEmpty() && referralLink.isNotEmpty()) {
                val domain = URI(referralLink).host
                countInstall(shortId, domain)
            }
        }
        notifyAttributionDetected(data, matchedByIp = true)
    }

    private fun saveJsonToPrefs(context: Context, key: String, jsonObject: JSONObject) {
        val sharedPref = context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString(key, jsonObject.toString()) // Store JSON as String
            apply()
        }
    }

    private fun getJsonFromPrefs(context: Context, key: String): JSONObject? {
        val sharedPref = context.getSharedPreferences("Referral", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString(key, null)

        return if (!jsonString.isNullOrEmpty()) {
            try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                e.printStackTrace() // Log the error
                null
            }
        } else {
            null
        }
    }

    private suspend fun getFullReferralDetails(
        domain: String,
        linkId: String,
        referLink: String
    ): JSONObject {
        return try {
            // getUserAgent() instantiates a WebView, which must happen on the main thread.
            val result = AppLinkHandler.fetchAppLink(
                linkId,
                domain,
                withContext(Dispatchers.Main) { getUserAgent() }
            )
            if (result.has("data") && !result.isNull("data")) {
                val dataObj = result.getJSONObject("data")
                dataObj.put("referralLink", referLink)
                result.put("message", "Referral link fetched successfully!")
                referralLink = result
                saveJsonToPrefs(context, "referral_details", result)
                referralDeferred?.complete(result) //  notify waiter
                referralDeferred = null
            } else {
                // Handle missing data object
                result.put("message", "AppLink referral does not exist!")
                referralDeferred?.complete(result) //  notify waiter
                referralDeferred = null
                saveJsonToPrefs(context, "referral_details", result)
            }
            result
        } catch (e: Exception) {
            // Handle unexpected errors gracefully
            val errorObj = JSONObject()
            errorObj.put("message", "AppLink referral does not exist!")
            referralDeferred?.complete(errorObj) //  notify waiter
            referralDeferred = null
            saveJsonToPrefs(context, "referral_details", errorObj)
            errorObj
        }
    }

    /**
     * Handles successful deep link processing.
     */
    private fun onDeepLinkProcessed(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            val uriScheme = uri.scheme.orEmpty()
            val isHttpLink = uriScheme.startsWith("http")
            val linkId: String
            val domain: String
            var isClick = false
            if (isHttpLink && !uri.lastPathSegment.isNullOrEmpty()) {
                linkId = uri.lastPathSegment.orEmpty()
                domain = uri.host.orEmpty()
                val containLink = uri.getQueryParameter("link").orEmpty()
                if (containLink.isEmpty()) {
                    isClick = true
                }

            } else {
                // Count api will not be trigger here as it will goes to browser every time for uri scheme.
                val rawLink = uri.getQueryParameter("link").orEmpty()
                val schemeUri = Uri.parse(
                    if (rawLink.startsWith("http")) rawLink else "https://$rawLink"
                )
                linkId = schemeUri.lastPathSegment.orEmpty()
                domain = schemeUri.host.orEmpty()
            }
            if(linkId.isEmpty()){
                notifyDeepLinkProcessed(uri, JSONObject())
                return@launch
            }
            AppLinkHandler.handleLinkCount(linkId, domain, isClick)

            // The link the deep link points at, fetched fresh, matching what iOS delivers here.
            // Already on the main dispatcher, which getUserAgent() requires for its WebView.
            val result = AppLinkHandler.fetchAppLink(linkId, domain, getUserAgent())
            notifyDeepLinkProcessed(uri, result.optJSONObject("data") ?: result)
        }
    }

    /**
     * Handles deep link processing errors.
     */
    private fun onDeepLinkError(uri: Uri?, error: String) {
        // Handle deep link error logic here if needed (e.g., logging, analytics)
        notifyDeepLinkError(uri, error)
    }


    /**
     * Opens the fallback URL in the browser.
     */
    private fun openFallbackUrl(fallbackUrl: String) {
        val fallbackUri = fallbackUrl.toUri()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            onDeepLinkError(fallbackUri, "Failed to open fallback URL: ${e.message}")
        }
    }

}