package com.appsonair.applink.utils

internal class StringConst {
    companion object {
        ///API
        const val AppLinkCreate = "dynamic-link/"
        const val Config = AppLinkCreate + "config/"
        const val ApplicatonKey = "x-application-key"

        /// Sent on every API request so the backend can attribute behaviour to an SDK release.
        /// The value comes from BuildConfig.VERSION_NAME, wired up in applink/build.gradle.kts.
        const val SdkVersionKey = "x-sdk-version"
        const val Referrer = AppLinkCreate + "referral/details"
        const val LinkAnalytics = "dynamic-link-analytics/"

        ///Attribution
        const val Organic = "organic"
        const val NonOrganic = "non-organic"

        ///Common
        const val NetworkError = "No Network Available!"
        const val AppIdMissing = "App id missing!"
        const val SomethingWentWrong = "Something went wrong please try again!"
        const val ValidUrlMessage = "Enter a valid URL"
    }
}