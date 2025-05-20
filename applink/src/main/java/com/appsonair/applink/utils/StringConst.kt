package com.appsonair.applink.utils

internal class StringConst {
    companion object {
        ///API
        const val AppLinkCreate = "dynamic-link/"
        const val Config = AppLinkCreate + "config/"
        const val FetchAndUpdate = "to be added/"
        const val ApplicatonKey = "x-application-key"

        ///Common
        const val NetworkError = "No Network Available!"
        const val AppIdMissing = "App id missing!"
        const val SomethingWentWrong = "Something went wrong please try again!"
    }
}