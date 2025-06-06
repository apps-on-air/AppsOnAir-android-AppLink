package com.appsonair.applink.utils

internal class StringConst {
    companion object {
        ///API
        const val AppLinkCreate = "dynamic-link/"
        const val Config = AppLinkCreate + "config/"
        const val ApplicatonKey = "x-application-key"
        const val Referrer = AppLinkCreate + "referral/details"
        const val LinkAnalytics = "dynamic-link-analytics/"

        ///Common
        const val NetworkError = "No Network Available!"
        const val AppIdMissing = "App id missing!"
        const val SomethingWentWrong = "Something went wrong please try again!"
    }
}