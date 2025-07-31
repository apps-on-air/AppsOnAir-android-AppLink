package com.appsonair.applink.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.appsonair.applink.interfaces.AppLinkListener
import com.appsonair.core.services.CoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    private lateinit var listener: AppLinkListener
    private var referralLink = JSONObject()

    fun initialize(context: Context, intent: Intent, listener: AppLinkListener) {
        this.listener = listener
        AppLinkHandler.appsOnAirAppId = CoreService.getAppId(context)

        // Fetch install referrer (if needed for initialization)
        fetchInstallReferrer {
            Log.d("AppLinkService", "Install Referrer fetched successfully----> $it")
        }

        // Handle deep link processing immediately
        handleDeepLink(intent, "com.example.appsonair_android_applink")

        NetworkWatcherService.checkNetworkConnection(context)
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
        )
    }

    fun getReferralDetails(): JSONObject {
        return referralLink
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
            try {
                onDeepLinkProcessed(uri)
            } catch (e: Exception) {
                onDeepLinkError(uri, "Error processing deep link: ${e.message}")
                handleFallback(fallbackPackageName, fallbackUrl, source)
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
                                AppLinkHandler.handleLinkCount(
                                    linkId,
                                    domain,
                                    isClicked = false,
                                    isFirstOpen = true,
                                    isInstall = true
                                )
                            }

                        }
                        var referrerData: Map<String, Any> =
                            if (linkId.isNotEmpty() && appsOnAirAppLink.isNotEmpty()) {
                                mapOf(
                                    "data" to JSONObject().apply {
                                        put("shortId", linkId)
                                        put("referralLink", schemeUri)
                                    },
                                )
                            } else {
                                mapOf(
                                    "message" to "Referral not found!",
                                )
                            }

                        referralLink = JSONObject(referrerData)
                        callback(referrerData.toString())
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
            AppLinkHandler.handleLinkCount(linkId, domain, isClick)
            val result = AppLinkHandler.fetchAppLink(linkId, domain)
            listener.onDeepLinkProcessed(uri, result.optJSONObject("data") ?: result)
        }
    }

    /**
     * Handles deep link processing errors.
     */
    private fun onDeepLinkError(uri: Uri?, error: String) {
        // Handle deep link error logic here if needed (e.g., logging, analytics)
        listener.onDeepLinkError(uri, error)
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