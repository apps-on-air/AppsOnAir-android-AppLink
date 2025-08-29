package com.appsonair.applink.interfaces

import android.net.Uri
import org.json.JSONObject

/**
 * overriding onReferralLinkDetected is optional you can call it as per your requirement for install tracking
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
     * Called when a deep link is successfully processed.
     * @param result The extracted data from the server for the link.
     */
    fun onReferralLinkDetected(result: JSONObject) {}
}