package com.appsonair.applink.services

import android.content.Context
import com.appsonair.core.services.NetworkService

internal class NetworkWatcherService {
    companion object {
        var isNetworkConnected = true //For connectivity issue fixes changed default to true

        fun checkNetworkConnection(context: Context) {
            NetworkService.checkConnectivity(
                context
            ) { isAvailable: Boolean ->
                isNetworkConnected = isAvailable
            }
        }
    }

}
