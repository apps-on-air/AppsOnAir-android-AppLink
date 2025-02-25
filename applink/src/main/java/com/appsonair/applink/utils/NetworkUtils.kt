package com.appsonair.applink.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL


internal class NetworkUtils {

    companion object {
        @JvmStatic
        fun getActiveNetworkIpAddress(context: Context): JSONObject {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val result = JSONObject()

            // Get the active network
            val activeNetwork: Network = connectivityManager.activeNetwork
                ?: return result.put("error", "No active network") // No active network

            // Get LinkProperties for the active network
            val linkProperties: LinkProperties =
                connectivityManager.getLinkProperties(activeNetwork)
                    ?: return result.put(
                        "error",
                        "No link properties for the active network"
                    ) // No link properties

            // Lists to store IPv4 and IPv6 addresses

            // Iterate through all link addresses
            linkProperties.linkAddresses.forEach { linkAddress ->
                val address = linkAddress.address
                when {
                    address.hostAddress?.contains(":") == true -> result.put(
                        "ipv6",
                        address.hostAddress
                    )

                    else -> result.put(
                        "ipv4",
                        address.hostAddress
                    )

                }
            }

            return result
        }


        //Method for getting public IP address
        @JvmStatic
        suspend fun getPublicIp(): String? {
            return try {
                withContext(Dispatchers.IO) {
                    URL("https://api.ipify.org").readText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}