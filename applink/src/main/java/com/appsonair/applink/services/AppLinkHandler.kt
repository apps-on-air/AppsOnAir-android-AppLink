package com.appsonair.applink.services

import android.content.Context
import android.util.Log
import com.appsonair.applink.BuildConfig
import com.appsonair.applink.utils.StringConst
import com.appsonair.core.services.CoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject


internal class AppLinkHandler {

    companion object {

        internal lateinit var appsOnAirAppId: String

        @JvmStatic
        suspend fun fetchAppLink(
            linkId: String
        ): JSONObject {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
            }
            val appLinkURL = BuildConfig.BASE_URL + StringConst.AppLinkCreate + linkId
            val client = OkHttpClient()

            val request = Request.Builder()
                .url(appLinkURL).addHeader(StringConst.ApplicatonKey, appsOnAirAppId)
                .get()
                .build()

            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        JSONObject(responseBody)  // Return the response directly
                    } else {
                        val errorBody = response.body?.string().orEmpty()
                        if (response.code != 429) {
                            JSONObject(errorBody)
                        } else {
                            JSONObject(mapOf("error" to errorBody))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NetworkError", "Request failed: ${e.message}")
                    // Handle failure
                    JSONObject(mapOf("error" to StringConst.SomethingWentWrong))
                }
            }
        }

        /**
         * If [isOpenInAndroidApp] and [isOpenInIosApp] are false,
         * then [isOpenInBrowserAndroid] and [isOpenInBrowserApple] must be true.
         * Otherwise, an error will be thrown.
         */
        @JvmStatic
        suspend fun createAppLink(
            url: String,
            name: String,
            urlPrefix: String,
            prefixId: String? = null,
            customParams: Map<String, Any>? = null,//For future use
            socialMeta: Map<String, Any>? = null,
            analytics: Map<String, Any>? = null,//For future use
            isOpenInBrowserAndroid: Boolean = false,
            isOpenInAndroidApp: Boolean = true,
            androidFallbackUrl: String? = null,
            isOpenInBrowserApple: Boolean = false,
            isOpenInIosApp: Boolean = true,
            iOSFallbackUrl: String? = null,
        ): JSONObject {
            // Some of the params are not used as we keep it for future user will remove if not needed in future i.e [customParams, analytics, isShortLink]
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
            }
            if (appsOnAirAppId.isEmpty()) return JSONObject(mapOf("error" to StringConst.AppIdMissing))
//            val dynamicLinkConfigId = getLinkConfigId()
//                .optJSONArray("data")
//                ?.optJSONObject(0)
//                ?.optString("id")
//                .orEmpty()

            val appLinkURL = BuildConfig.BASE_URL + StringConst.AppLinkCreate // Your API endpoint

            // val appLinkURL = "https://jsonplaceholder.typicode.com/posts/1" // Test
            val json = "application/json; charset=utf-8".toMediaType() // Media type for JSON
            val client = OkHttpClient() // OkHttp client instance
            var message: String
            // Build JSON object for the request body
            val jsonObject = try {
                JSONObject().apply {
                    put(
                        "data",
                        JSONObject().apply {
                            put("name", name)
                            // Add optional parameters if they are not null
                            put("link", url)
                            prefixId?.let { put("shortId", it) }
                            // customParams?.let { put("customParams", JSONObject(it)) }
                            socialMeta?.let { put("socialMetaTags", JSONObject(it)) }
                            //  analytics?.let { put("analytics", JSONObject(it)) }
                            //  put("isShortLink", isShortLink)
                            androidFallbackUrl?.let { put("customUrlForAndroid", it) }
                            iOSFallbackUrl?.let { put("customUrlForIos", it) }
                            put("isOpenInBrowserApple", isOpenInBrowserApple)
                            put("isOpenInAndroidApp", isOpenInAndroidApp)
                            put("isOpenInIosApp", isOpenInIosApp)
                            put("isOpenInBrowserAndroid", isOpenInBrowserAndroid)
                        },
                    )
                    put("where", JSONObject().apply {
                        put("urlPrefix", urlPrefix)
                    })
                }
            } catch (e: JSONException) {
                Log.e("JSONError", "Failed to construct JSON object: ${e.message}")
                message = StringConst.SomethingWentWrong // Handle failure
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
                .url(appLinkURL).addHeader(StringConst.ApplicatonKey, appsOnAirAppId)
                .post(body)
                .build()

            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        JSONObject(responseBody) // Return the response directly
                    } else {
                        val errorBody = response.body?.string().orEmpty()
                        if (response.code != 429) {
                            JSONObject(errorBody)
                        } else {
                            JSONObject(mapOf("error" to errorBody))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NetworkError", "Request failed: ${e.message}")
                    JSONObject(mapOf("error" to StringConst.SomethingWentWrong)) // Handle failure
                }
            }
        }


//        @JvmStatic
//        suspend fun getLinkConfigId(
//        ): JSONObject {
//            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
//            if (appsOnAirAppId.isEmpty()) return JSONObject(mapOf("error" to StringConst.AppIdMissing))
//            if (!isNetworkConnected) {
//                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
//            }
//            if (appsOnAirAppId.isEmpty()) return JSONObject(mapOf("error" to StringConst.AppIdMissing))
//            val appLinkURL =
//                BuildConfig.BASE_URL + StringConst.Config + appsOnAirAppId
//
//            val client = OkHttpClient() // OkHttp client instance
//
//            val request = Request.Builder()
//                .url(appLinkURL).addHeader(StringConst.ApplicatonKey, appsOnAirAppId)
//                .get()
//                .build()
//
//            return withContext(Dispatchers.IO) {
//                try {
//                    val response = client.newCall(request).execute()
//                    if (response.isSuccessful) {
//                        val responseBody = response.body?.string().orEmpty()
//                        JSONObject(responseBody) // Return the response directly
//                    } else {
//                        val errorBody = response.body?.string().orEmpty()
//                        JSONObject(errorBody) // Handle error response
//                    }
//                } catch (e: Exception) {
//                    JSONObject(mapOf("error" to StringConst.SomethingWentWrong)) // Handle failure
//                }
//            }
//        }


        @JvmStatic
        suspend fun fetchAndUpdateDeviceInfo(
            context: Context? = null,
            linkId: String
        ): JSONObject {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
            }
            if (appsOnAirAppId.isEmpty()) return JSONObject(mapOf("error" to StringConst.AppIdMissing))

            val appLinkURL =
                BuildConfig.BASE_URL + StringConst.FetchAndUpdate // To be changed
            val json = "application/json; charset=utf-8".toMediaType() // Media type for JSON
            val client = OkHttpClient() // Avoid using `.newBuilder()` unnecessarily
            var message: String
            val jsonObject = try {
                val additionalInfo = mapOf("appLinkVersion" to BuildConfig.VERSION_NAME)
                val deviceInfoWithAdditionalInfo =
                    context?.let { CoreService.getDeviceInfo(it, additionalInfo) }

                ////For storing the data in single object as remark need to comment out below code if need to do.
                context?.let {
                    val additionalInfo = mapOf("appLinkVersion" to BuildConfig.VERSION_NAME)
                    val deviceInfoWithAdditionalInfo = CoreService.getDeviceInfo(it, additionalInfo)

                    val finalMap =
                        deviceInfoWithAdditionalInfo.getJSONObject("deviceInfo").toMap() +
                                deviceInfoWithAdditionalInfo.getJSONObject("appInfo").toMap()
                    JSONObject().apply { put("deviceInfo", finalMap) }
                }

//                JSONObject().apply {
//                    put("where", JSONObject().put("linkId", linkId))
//                    put(
//                        "data",
//                        JSONObject().apply {
//                            if (deviceInfoWithAdditionalInfo != null) {
//                                put(
//                                    "deviceInfo",
//                                    deviceInfoWithAdditionalInfo.getJSONObject("deviceInfo")
//                                )
//                            }
//                            if (deviceInfoWithAdditionalInfo != null) {
//                                put(
//                                    "appInfo",
//                                    deviceInfoWithAdditionalInfo.getJSONObject("appInfo")
//                                )
//                            }
//                        }
//                    )
//                }
            } catch (e: JSONException) {
                Log.e("JSONError", "Failed to construct JSON object: ${e.message}")
                message = "Error to be return"// Exit early if JSON creation fails
                return JSONObject(mapOf("error" to message))
            }

            val body = jsonObject.toString().toRequestBody(json)
            val request = Request.Builder()
                .url(appLinkURL).addHeader(StringConst.ApplicatonKey, appsOnAirAppId)
                .post(body)
                .build()


            return withContext(Dispatchers.IO) {
                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string().orEmpty()
                        JSONObject(responseBody)  // Return the response directly
                    } else {
                        val errorBody = response.body?.string().orEmpty()
                        if (response.code != 429) {
                            JSONObject(errorBody)
                        } else {
                            JSONObject(mapOf("error" to errorBody))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NetworkError", "Request failed: ${e.message}")
                    // Handle failure
                    JSONObject(mapOf("error" to StringConst.SomethingWentWrong))
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