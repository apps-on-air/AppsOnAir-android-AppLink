package com.appsonair.applink.interfaces

import android.net.Uri
import org.json.JSONObject

interface AppLinkListener {
    /**
     * Called when a deep link is successfully processed.
     *
     * @param uri The deep link URI that was processed.
     * @param params The extracted query parameters from the URI.
     */
    fun onDeepLinkProcessed(uri: Uri, result: JSONObject) // appLinkRetrieved

    /**
     * Called when a deep link fails to process.
     *
     * @param uri The deep link URI that caused the failure.
     * @param error The error message or exception.
     */
    fun onDeepLinkError(uri: Uri?, error: String) // appLinkError

//    fun onReferralLinkDetected(uri: Uri, params: Map<String, String>)
}