package com.appsonair.applink.services

import android.content.Context
import android.util.Log
import com.appsonair.applink.utils.Utils
import com.appsonair.core.services.CoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject


class AppLinkHandler {

    companion object {

        @JvmStatic
        suspend fun fetchAppLink(
            context: Context? = null,
            linkId: String
        ): JSONObject {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to "No Network Available!!")) // Exit early if JSON creation fails
            }
            /// val appsOnAirAppId = CoreService.getAppId(context)
            val appLinkURL = "https://www.google.com"
            val json = "application/json; charset=utf-8".toMediaType() // Media type for JSON
            val client = OkHttpClient() // Avoid using `.newBuilder()` unnecessarily
            var message: String
            val jsonObject = try {
                val additionalInfo = mapOf("appLinkVersion" to "To be done")
                val deviceInfoWithAdditionalInfo =
                    context?.let { CoreService.getDeviceInfo(it, additionalInfo) }

                ////For storing the data in single object as remark need to comment out below code if need to do.
                /*          val additionalInfo = mapOf("appLinkVersion" to "To be done")
                            val deviceInfoWithAdditionalInfo =
                                CoreService.getDeviceInfo(context, additionalInfo)
                            val dInfo = deviceInfoWithAdditionalInfo.getJSONObject("deviceInfo").toMap()
                            val aInfo = deviceInfoWithAdditionalInfo.getJSONObject("appInfo").toMap()
                            val finalMap = dInfo + aInfo */

                JSONObject().apply {
                    put("where", JSONObject().put("linkId", linkId))
                    put(
                        "data",
                        JSONObject().apply {
                            if (deviceInfoWithAdditionalInfo != null) {
                                put(
                                    "deviceInfo",
                                    deviceInfoWithAdditionalInfo.getJSONObject("deviceInfo")
                                )
                            }
                            if (deviceInfoWithAdditionalInfo != null) {
                                put(
                                    "appInfo",
                                    deviceInfoWithAdditionalInfo.getJSONObject("appInfo")
                                )
                            }
                        }
                    )
                }
            } catch (e: JSONException) {
                Log.e("JSONError", "Failed to construct JSON object: ${e.message}")
                message = "Error to be return"// Exit early if JSON creation fails
                return JSONObject(mapOf("error" to message))
            }
            val body = jsonObject.toString().toRequestBody(json)
            val request = Request.Builder()
                .url(appLinkURL)
                .post(body)
                .build()


            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        Log.d("ResponseSuccess", "Response: $responseBody")

                        JSONObject(responseBody)  // Return the response directly
                    } else {
                        if (context != null) {
                            Utils.showToastMsg(context, response.message)
                        }
                        Log.e(
                            "ResponseError",
                            "Error code: ${response.code}, message: ${response.message}"
                        )
                        JSONObject(mapOf("error" to "ResponseError: ${response.code} ${response.message}"))
                        // Handle error response
                    }
                } catch (e: Exception) {
                    e.message?.let {
                        if (context != null) {
                            Utils.showToastMsg(context, it)
                        }
                    }
                    Log.e("NetworkError", "Request failed: ${e.message}")
                    // Handle failure
                    JSONObject(mapOf("error" to "Request failed: ${e.message}"))
                }
            }
        }


        @JvmStatic
        suspend fun createAppLink(
            url: String? = null,
            prefixUrl: String? = null,
            customParams: Map<String, Any>? = null,
            socialMeta: Map<String, Any>? = null,
            analytics: Map<String, Any>? = null,
            isShortLink: Boolean = true,
            androidFallbackUrl: String? = null,
            iOSFallbackUrl: String? = null,
            context: Context
        ): JSONObject {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to "No Network Available!!")) // Exit early if JSON creation fails
            }
            val appLinkURL = "https://www.google.com" // Your API endpoint
            val json = "application/json; charset=utf-8".toMediaType() // Media type for JSON
            val client = OkHttpClient() // OkHttp client instance
            var message: String
            val appsOnAirAppId = CoreService.getAppId(context)
            // Build JSON object for the request body
            val jsonObject = try {
                JSONObject().apply {
                    put(
                        "data",
                        JSONObject().apply {
                            put("appsonairId", appsOnAirAppId)
                            // Add optional parameters if they are not null
                            url?.let { put("url", it) }
                            prefixUrl?.let { put("prefixUrl", it) }
                            customParams?.let { put("customParams", JSONObject(it)) }
                            socialMeta?.let { put("socialMeta", JSONObject(it)) }
                            analytics?.let { put("analytics", JSONObject(it)) }
                            put("isShortLink", isShortLink)
                            androidFallbackUrl?.let { put("androidFallbackUrl", it) }
                            iOSFallbackUrl?.let { put("iOSFallbackUrl", it) }
                        }
                    )
                }
            } catch (e: JSONException) {
                Log.e("JSONError", "Failed to construct JSON object: ${e.message}")
                message = "Failed to create JSON: ${e.message}"
                return JSONObject(mapOf("error" to message)) // Exit early if JSON creation fails
            }

            val body = try {
                jsonObject.toString().toRequestBody(json)
            } catch (e: Exception) {
                Log.e("RequestError", "Failed to create request body: ${e.message}")
                message = "Failed to create request body"
                return JSONObject(mapOf("error" to message)) // Exit early if request body creation fails
            }

            val request = Request.Builder()
                .url(appLinkURL)
                .post(body)
                .build()

            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        Log.d("ResponseSuccess", "Response: $responseBody")
                        JSONObject(responseBody) // Return the response directly
                    } else {
                        Log.e(
                            "ResponseError",
                            "Error code: ${response.code}, message: ${response.message}"
                        )
                        Utils.showToastMsg(
                            context,
                            "Error code: ${response.code}, message: ${response.message}"
                        )
                        JSONObject(mapOf("error" to "ResponseError: ${response.code} ${response.message}")) // Handle error response
                    }
                } catch (e: Exception) {
                    e.message?.let { Utils.showToastMsg(context, it) }
                    Log.e("NetworkError", "Request failed: ${e.message}")
                    JSONObject(mapOf("error" to "Request failed: ${e.message}")) // Handle failure
                }
            }
        }

        // Convert JSONObject to Map<String, Any>
        private fun JSONObject.toMap(): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = when (val value = this.get(key)) {
                    is JSONObject -> value.toMap()  // Convert nested JSON
                    is org.json.JSONArray -> value  // Convert JSON array if needed
                    else -> value
                }
            }
            return map
        }
    }
}