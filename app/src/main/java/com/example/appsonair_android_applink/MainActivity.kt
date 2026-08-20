package com.example.appsonair_android_applink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsonair.applink.interfaces.AppLinkListener
import com.appsonair.applink.services.AppLinkService
import com.example.appsonair_android_applink.ui.theme.AppLinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject


class MainActivity : ComponentActivity() {

    private lateinit var appLinkService: AppLinkService

    // Each section keeps its own response so the deprecated and attribution
    // results are never shown in the same place.
    private var deepLinkResult by mutableStateOf("")
    private var deprecatedListenerResult by mutableStateOf("")
    private var deprecatedApiResult by mutableStateOf("")
    private var attributionListenerResult by mutableStateOf("")
    private var attributionApiResult by mutableStateOf("")
    private var createLinkResult by mutableStateOf("")
    private var isLoading by mutableStateOf(false)

    // createAppLink inputs, mirroring the React Native example's form.
    private var linkName by mutableStateOf("AppsOnAir")
    private var linkUrl by mutableStateOf("https://appsonair.com")
    // urlPrefix shouldn't contain http or https
    private var urlPrefix by mutableStateOf("")
    private var shortId by mutableStateOf("")
    private var androidFallbackUrl by mutableStateOf("")
    private var iosFallbackUrl by mutableStateOf("")
    private var attributionTtl by mutableStateOf("")
    private var isOpenInAndroidApp by mutableStateOf(true)
    private var isOpenInBrowserAndroid by mutableStateOf(false)
    private var isOpenInIosApp by mutableStateOf(true)
    private var isOpenInBrowserApple by mutableStateOf(false)

    // The SDK forwards this dictionary to the API untouched, so it is edited as raw JSON rather
    // than fixed fields — any keys beyond the documented ones are passed through as-is.
    private var appsFlyerJson by mutableStateOf(
        """
        {
          "channel": "appsonair",
          "campaignId": "01",
          "campaign": "test",
          "subs": ["sub1", "sub2", "sub3", "sub4", "sub5"],
          "metaTitle": "metaTitle",
          "metaDescription": "metaDescription"
        }
        """.trimIndent()
    )

    // Called when the activity is created. Initializes AppLinkService and sets up deep link handling.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize deeplink service and set listener for deep link and attribution events
        appLinkService = AppLinkService.getInstance(this)

        // AppLinkListener is the only listener, and it serves both surfaces: it carries the
        // deprecated onReferralLinkDetected() alongside onAttributionListener(), and the SDK gives
        // each its own payload.
        appLinkService.initialize(this, intent, object : AppLinkListener {
            override fun onDeepLinkProcessed(uri: Uri, result: JSONObject) {
                // Store the processed deep link URL and log the parameters
                Log.d("DeepLinkListener", "Deep link Result -->$result")
                Log.d("DeepLinkListener", "Deep link Link -->$uri")
                deepLinkResult = result.toString() // Update UI with the deep link result
            }

            override fun onDeepLinkError(uri: Uri?, error: String) {
                // Handle error when deep link processing fails
                Log.e("DeepLinkListener", "Failed to process deep link: $uri, Error: $error")
            }

            override fun onAttributionListener(result: JSONObject) {
                attributionListenerResult = result.toString()
            }

            // Deprecated, kept here only to compare its payload with onAttributionListener():
            // the referral only, with no appsFlyer object and none of the attribution fields.
            @Deprecated("Use onAttributionListener instead")
            override fun onReferralLinkDetected(result: JSONObject) {
                deprecatedListenerResult = result.toString()
            }
        })

        enableEdgeToEdge()
        setContent { AppLinkScreen() }
    }

    // Called when the app is resumed with a new intent (deep link).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appLinkService.handleDeepLink(
            intent,
            "com.example.appsonair_android_applink"
        ) // Handle deep link
    }

    /** Reads the AppsFlyer JSON box. Returns null when it is blank or cannot be parsed. */
    private fun parseAppsFlyer(): Map<String, Any>? {
        val trimmed = appsFlyerJson.trim()
        if (trimmed.isEmpty()) return null
        return try {
            jsonToMap(JSONObject(trimmed))
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonToMap(json: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            map[key] = when (val value = json.get(key)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonToList(value)
                else -> value
            }
        }
        return map
    }

    private fun jsonToList(array: JSONArray): List<Any> =
        (0 until array.length()).map { index ->
            when (val value = array.get(index)) {
                is JSONObject -> jsonToMap(value)
                is JSONArray -> jsonToList(value)
                else -> value
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppLinkScreen() {
        AppLinkTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                // Main Scaffold content
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.White),
                    topBar = {
                        CenterAlignedTopAppBar(
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = Color.Black,
                            ),
                            title = {
                                Text(
                                    getString(R.string.simulate_deep_link),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                        )
                    },
                ) { innerPadding ->
                    // Column for the main content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(color = Color.White)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ResponseSection("Deep Link", deepLinkResult)

                        Divider()

                        ResponseSection(
                            "Deprecated — onReferralLinkDetected()",
                            deprecatedListenerResult
                        )
                        ResponseSection(
                            "Deprecated — getReferralDetails() / getReferralInfo()",
                            deprecatedApiResult
                        )

                        // Button to get the referral details (deprecated)
                        ElevatedButton(onClick = {
                            val referral = appLinkService.getReferralDetails()
                            deprecatedApiResult = referral.toString()
                        }) {
                            Text("Get Referral Details (Deprecated)")
                        }

                        // Button to get the referral info (deprecated)
                        ElevatedButton(onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                val referral = appLinkService.getReferralInfo()
                                deprecatedApiResult = referral.toString()
                            }
                        }) {
                            Text("Get Referral Info (Deprecated)")
                        }

                        Divider()

                        ResponseSection(
                            "Attribution — onAttributionListener()",
                            attributionListenerResult
                        )
                        ResponseSection(
                            "Attribution — getAttributionInfo()",
                            attributionApiResult
                        )

                        // Button to get the attribution info
                        ElevatedButton(onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                val attribution = appLinkService.getAttributionInfo()
                                attributionApiResult = attribution.toString()
                            }
                        }) {
                            Text("Get Attribution Info")
                        }

                        Divider()

                        SectionTitle("Create AppLink")

                        LabeledTextField("Name", linkName) { linkName = it }
                        LabeledTextField("URL", linkUrl) { linkUrl = it }
                        LabeledTextField("URL Prefix", urlPrefix) { urlPrefix = it }
                        LabeledTextField("Short ID", shortId) { shortId = it }
                        LabeledTextField("Android Fallback URL", androidFallbackUrl) {
                            androidFallbackUrl = it
                        }
                        LabeledTextField("iOS Fallback URL", iosFallbackUrl) {
                            iosFallbackUrl = it
                        }
                        LabeledTextField(
                            "Attribution TTL (seconds)",
                            attributionTtl,
                            keyboardType = KeyboardType.Number
                        ) { attributionTtl = it }
                        LabeledTextField(
                            "AppsFlyer params (JSON, any keys allowed)",
                            appsFlyerJson,
                            singleLine = false,
                            minLines = 6
                        ) { appsFlyerJson = it }

                        LabeledSwitch("Open in Android App", isOpenInAndroidApp) {
                            isOpenInAndroidApp = it
                        }
                        LabeledSwitch("Open in Android Browser", isOpenInBrowserAndroid) {
                            isOpenInBrowserAndroid = it
                        }
                        LabeledSwitch("Open in iOS App", isOpenInIosApp) { isOpenInIosApp = it }
                        LabeledSwitch("Open in iOS Browser", isOpenInBrowserApple) {
                            isOpenInBrowserApple = it
                        }

                        ResponseSection("Create Link", createLinkResult)

                        // Button to trigger API call
                        ElevatedButton(
                            onClick = {
                                // Parsed before the call so malformed JSON is reported on its own
                                // rather than as an API failure.
                                val appsFlyer = parseAppsFlyer()
                                if (appsFlyer == null && appsFlyerJson.isNotBlank()) {
                                    createLinkResult =
                                        "Invalid AppsFlyer JSON — fix the JSON and try again."
                                    return@ElevatedButton
                                }

                                isLoading = true // Show loader

                                val socialMeta = mapOf(
                                    "title" to "link title",
                                    "description" to "link description",
                                    "imageUrl" to "https://image.com"
                                )

                                CoroutineScope(Dispatchers.Main).launch {
                                    val result = appLinkService.createAppLink(
                                        name = linkName,
                                        url = linkUrl,
                                        urlPrefix = urlPrefix,
                                        shortId = shortId.takeIf { it.isNotBlank() },
                                        socialMeta = socialMeta,
                                        isOpenInAndroidApp = isOpenInAndroidApp,
                                        isOpenInBrowserAndroid = isOpenInBrowserAndroid,
                                        androidFallbackUrl = androidFallbackUrl,
                                        isOpenInIosApp = isOpenInIosApp,
                                        isOpenInBrowserApple = isOpenInBrowserApple,
                                        iosFallbackUrl = iosFallbackUrl,
                                        appsFlyer = appsFlyer,
                                        attributionTtl = attributionTtl.trim().toIntOrNull()
                                    )
                                    Log.d("API response==>", result.toString())
                                    createLinkResult = result.toString()
                                    isLoading = false // Hide loader
                                }
                            },
                            Modifier.padding(16.dp)
                        ) {
                            Text("Create Link")
                        }
                    }
                }

                if (isLoading) {
                    // Overlay for loader
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x80000000)) // Semi-transparent background
                            .pointerInput(Unit) {} // Blocks all touch interactions
                            .clickable(enabled = false) {}, // Prevent clicks
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 16.dp),
                            color = Color.Blue
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
    }

    @Composable
    private fun LabeledTextField(
        label: String,
        value: String,
        keyboardType: KeyboardType = KeyboardType.Text,
        singleLine: Boolean = true,
        minLines: Int = 1,
        onValueChange: (String) -> Unit
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Black,
                cursorColor = Color.Black,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    @Composable
    private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 14.sp, color = Color.Black)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    private fun ResponseSection(title: String, value: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            // Selectable so the response can be long pressed and copied off the device
            SelectionContainer {
                Text(
                    text = value.ifEmpty { "No data yet" },
                    fontSize = 12.sp,
                    color = Color.Black
                )
            }
        }
    }
}
