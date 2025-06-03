package com.appsonair.applink.services

import android.util.Log
import com.appsonair.applink.BuildConfig
import com.appsonair.applink.utils.StringConst
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
            linkId: String,
            domain: String
        ): JSONObject {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
            }
            val appLinkURL =
                BuildConfig.BASE_URL + StringConst.AppLinkCreate + linkId + "?domain=$domain"
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
            shortId: String? = null,
            socialMeta: Map<String, Any>? = null,
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
                            shortId?.let { put("shortId", it) }
                            socialMeta?.let { put("socialMetaTags", JSONObject(it)) }
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