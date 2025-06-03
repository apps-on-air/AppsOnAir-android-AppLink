package com.appsonair.applink.services

import android.content.Context
import com.appsonair.core.services.NetworkService

internal class NetworkWatcherService {
    companion object {
        var isNetworkConnected = false

        fun checkNetworkConnection(context: Context) {
            NetworkService.checkConnectivity(
                context
            ) { isAvailable: Boolean ->
                isNetworkConnected = isAvailable
            }
        }
    }

}
