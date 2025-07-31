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
            isOpenInBrowserAndroid: Boolean? = null,
            isOpenInAndroidApp: Boolean? = null,
            androidFallbackUrl: String? = null,
            isOpenInBrowserApple: Boolean? = null,
            isOpenInIosApp: Boolean? = null,
            iosFallbackUrl: String? = null,
        ): JSONObject {
            // Some of the params are not used as we keep it for future user will remove if not needed in future i.e [customParams, analytics, isShortLink]
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (!isNetworkConnected) {
                return JSONObject(mapOf("error" to StringConst.NetworkError)) // Exit early if JSON creation fails
            }
            if (appsOnAirAppId.isEmpty()) return JSONObject(mapOf("error" to StringConst.AppIdMissing))

            val appLinkURL = BuildConfig.BASE_URL + StringConst.AppLinkCreate // Your API endpoint
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
                            socialMeta?.let {
                                put("socialMetaTags", JSONObject().apply {
                                    put("title", it["title"] ?: JSONObject.NULL)
                                    put("description", it["description"] ?: JSONObject.NULL)
                                    put("imageUrl", it["imageUrl"] ?: JSONObject.NULL)
                                })
                            }
                            androidFallbackUrl?.let { put("customUrlForAndroid", it) }
                            iosFallbackUrl?.let { put("customUrlForIos", it) }
                            isOpenInBrowserApple?.let { put("isOpenInBrowserApple", it) }
                            isOpenInAndroidApp?.let { put("isOpenInAndroidApp", it) }
                            isOpenInIosApp?.let { put("isOpenInIosApp", it) }
                            isOpenInBrowserAndroid?.let { put("isOpenInBrowserAndroid", it) }
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

            val data = jsonObject.getJSONObject("data")
            val invalidKeys = mutableListOf<String>()


            fun checkValidUrlKey(key: String) {
                val value = data.optString(key)
                if (value.isNotBlank() && !value.isValidUrl()) {
                    invalidKeys.add(key)
                }
            }

            checkValidUrlKey("link")
            checkValidUrlKey("customUrlForIos")
            checkValidUrlKey("customUrlForAndroid")

            data.optJSONObject("socialMetaTags")?.let { meta ->
                if (meta.has("imageUrl") && !meta.isNull("imageUrl")) {
                    val imageUrl = meta.optString("imageUrl")
                    if (imageUrl.isNotBlank() && !imageUrl.isValidUrl()) {
                        invalidKeys.add("imageUrl")
                    }
                }
            }

            if (invalidKeys.isNotEmpty()) {
                message = when (val firstInvalidKey = invalidKeys.first()) {
                    "link" -> "${StringConst.ValidUrlMessage} in url field!"
                    "customUrlForAndroid" -> "${StringConst.ValidUrlMessage} in androidFallbackUrl field!"
                    "customUrlForIos" -> "${StringConst.ValidUrlMessage} in iosFallbackUrl field!"
                    else -> {
                        "${StringConst.ValidUrlMessage} in $firstInvalidKey field!"
                    }
                }
                return JSONObject(mapOf("error" to message))
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

        private fun String.isValidUrl(): Boolean {
            return (startsWith("http://") || startsWith("https://"))
        }

        @JvmStatic
        fun handleLinkCount(
            linkId: String,
            domain: String,
            isClicked: Boolean = true,
            isFirstOpen: Boolean = false,
            isInstall: Boolean = false,
        ) {
            val isNetworkConnected = NetworkWatcherService.isNetworkConnected
            if (appsOnAirAppId.isEmpty()) {
                Log.e("AppLink", StringConst.AppIdMissing)
                return
            }

            if (!isNetworkConnected) {
                Log.e("error", StringConst.NetworkError)
                return
            }

            Thread {
                try {
                    val json = "application/json; charset=utf-8".toMediaType()
                    val appLinkURL = BuildConfig.BASE_URL + StringConst.LinkAnalytics
                    val client = OkHttpClient()
                    val jsonObject = JSONObject().apply {
                        put("domain", domain)
                        put("shortId", linkId)
                        put("isClicked", isClicked)
                        put("isFirstOpen", isFirstOpen)
                        put("isInstalled", isInstall)
                        put("isReOpen", !isInstall)
                    }
                    val body = jsonObject.toString().toRequestBody(json)
                    val request = Request.Builder()
                        .url(appLinkURL)
                        .addHeader(StringConst.ApplicatonKey, appsOnAirAppId)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        Log.d("AppLink", "Analytics Added")
                    } else {
                        Log.e("AppLink", "Analytics failed: ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e("AppLink", "API call exception: ${e.localizedMessage}")
                }
            }.start()
        }
    }
}