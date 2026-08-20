package com.appsonair.applink.interfaces

import android.net.Uri
import org.json.JSONObject

/**
 * overriding onAttributionListener is optional you can call it as per your requirement for install tracking
 */
interface AppLinkListener {
    /**
     * Called when a deep link is successfully processed.
     *
     * @param uri The deep link URI that was processed.
     * @param result The extracted data from the server for the link.
     */
    fun onDeepLinkProcessed(uri: Uri, result: JSONObject) // appLinkRetrieved

    /**
     * Called when a deep link fails to process.
     *
     * @param uri The deep link URI that caused the failure.
     * @param error The error message or exception.
     */
    fun onDeepLinkError(uri: Uri?, error: String) // appLinkError

    /**
     * Fires at most twice: when an attribution is detected, then once more on the return to the
     * foreground that follows `isFirstLaunch` turning `false`, re-delivering the persisted payload
     * without refetching so the listener sees that flip. Later foreground returns are silent.
     * Gate one time logic on [isFirstLaunch].
     *
     * @param result The attribution data along with [isFirstLaunch], [firstInstallTime],
     * [isConsumed] and [attributionStatus].
     */
    fun onAttributionListener(result: JSONObject) {}

    /**
     * Called when a referral link is detected, carrying the referral payload in its original
     * shape: no `appsFlyer` object and none of the attribution fields.
     *
     * Detection only — unlike [onAttributionListener] it is not re-delivered when the app returns
     * to the foreground.
     *
     * Provided here so one listener can serve both surfaces, for wrappers that still read the
     * referral payload.
     *
     * @param result The referral data, without the newer attribution surface.
     */
    @Deprecated(message = "Use onAttributionListener() instead")
    fun onReferralLinkDetected(result: JSONObject) {}
}
