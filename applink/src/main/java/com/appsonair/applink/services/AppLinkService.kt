package com.appsonair.applink.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.appsonair.applink.interfaces.AppLinkListener

class AppLinkService private constructor(private val context: Context) {


    companion object {
        @Volatile
        private var instance: AppLinkService? = null

        fun getInstance(context: Context): AppLinkService {
            return instance ?: synchronized(this) {
                instance ?: AppLinkService(context.applicationContext).also { instance = it }
            }
        }
    }

    private lateinit var listener: AppLinkListener
    private var referralLink = ""

    fun initialize(intent: Intent, listener: AppLinkListener) {
        this.listener = listener

        // Fetch install referrer (if needed for initialization)
        fetchInstallReferrer {
            Log.d("AppLinkService", "Install Referrer fetched successfully.")
        }

        // Handle deep link processing immediately
        handleDeepLink(intent, "com.example.appsonair_android_applink")
    }

    /**
     * Registers a DeepLinkListener.
     */
    fun setListener(listener: AppLinkListener) {
        this.listener = listener
    }

    fun getReferralLink(): String {
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
            Log.d("Deeplink====>", uri.toString())
            try {
                val params = extractQueryParameters(uri)
                if (isReferralLink(params)) {
                    onReferralLinkDetected(uri, params)
                } else {
                    onDeepLinkProcessed(uri, params)
                }
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
     * Checks if the deep link contains referral parameters.
     */
    private fun isReferralLink(params: Map<String, String>): Boolean {
        return params.containsKey("referrer") || params.containsKey("source")
    }

    /**
     * Handles a referral link.
     */
    private fun onReferralLinkDetected(uri: Uri, params: Map<String, String>) {
        listener.onReferralLinkDetected(uri, params)
    }


    /**
     * Retrieves referrer details from the Google Play Install Referrer API.
     */
    private fun fetchInstallReferrer(callback: (String) -> Unit) {
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        val response: ReferrerDetails = referrerClient.installReferrer
                        // Connection established.
                        val referrerUrl0 = response.installReferrer
                        val referrerClickTime = response.referrerClickTimestampSeconds
                        val appInstallTime = response.installBeginTimestampSeconds
                        Log.d("InstallReferrer-->", "Referrer Url: $referrerUrl0")
                        Log.d("Referrer Click Time-->", "Referrer Url: $referrerClickTime")
                        Log.d("appInstallTime Click Time-->", "Referrer Url: $appInstallTime")
                        val referrerUrl = response.installReferrer
                        referralLink = referrerUrl
                        callback(referrerUrl)
                        // listener.onDeepLinkProcessed(Uri.parse(referrerUrl0), emptyMap())
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
    private fun onDeepLinkProcessed(uri: Uri, params: Map<String, String>) {
        // Handle deep link success logic here if needed (e.g., logging, analytics)
        listener.onDeepLinkProcessed(uri, params)
    }

    /**
     * Handles deep link processing errors.
     */
    private fun onDeepLinkError(uri: Uri?, error: String) {
        // Handle deep link error logic here if needed (e.g., logging, analytics)
        listener.onDeepLinkError(uri, error)
    }


    /**
     * Extracts query parameters from a URI.
     */
    private fun extractQueryParameters(uri: Uri): Map<String, String> {
        return uri.queryParameterNames.associateWith { uri.getQueryParameter(it) ?: "" }
    }

    /**
     * Opens the fallback URL in the browser.
     */
    private fun openFallbackUrl(fallbackUrl: String) {
        Log.d("fallback url==========>", fallbackUrl)
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