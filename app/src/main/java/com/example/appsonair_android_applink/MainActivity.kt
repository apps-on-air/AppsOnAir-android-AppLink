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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsonair.applink.interfaces.AppLinkListener
import com.appsonair.applink.services.AppLinkService
import com.example.appsonair_android_applink.ui.theme.AppLinkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


class MainActivity : ComponentActivity() {

    private lateinit var deeplinkService: AppLinkService
    private var deepLinkUrl = ""

    // Called when the activity is created. Initializes AppLinkService and sets up deep link handling.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize deeplink service and set listener for deep link and referral link events
        deeplinkService = AppLinkService.getInstance(this)
        // Initialize the app link to track the deeplink and referral tracking.
        deeplinkService.initialize(this, intent, object : AppLinkListener {
            override fun onDeepLinkProcessed(uri: Uri, result: JSONObject) {
                // Store the processed deep link URL and log the parameters
                deepLinkUrl = uri.toString()
                Log.d(
                    "DeepLinkListener",
                    "Deep link Result -->$result"
                )
                setUI(result.toString()) // Update UI with the deep link URL
            }

            override fun onDeepLinkError(uri: Uri?, error: String) {
                // Handle error when deep link processing fails
                Log.e("DeepLinkListener", "Failed to process deep link: $uri, Error: $error")
            }

//            override fun onReferralLinkDetected(uri: Uri, params: Map<String, String>) {
//                // Handle referral link detection
//                Log.d("DeepLinkListener", "Referral link uri-->$uri, Parameters: $params")
//            }
        })

        // Update UI with the current deep link URL
        setUI(deepLinkUrl)
    }

    // Called when the app is resumed with a new intent (deep link).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplinkService.handleDeepLink(
            intent,
            "com.example.appsonair_android_applink"
        ) // Handle deep link
        setUI(deepLinkUrl) // Update UI with the deep link URL
    }

    // Sets the UI based on the provided URL, displaying either the deep link or a fallback message.
    @OptIn(ExperimentalMaterial3Api::class)
    private fun setUI(url: String) {
        enableEdgeToEdge()
        setContent {
            AppLinkTheme {
                var isLoading by remember { mutableStateOf(false) }
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
                                    titleContentColor = MaterialTheme.colorScheme.primary,
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
                                .background(color = Color.White),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Display deep link or fallback message
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Link==> ${url.ifEmpty { "No link found!!" }}",
                                    fontSize = 18.sp,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .padding(30.dp)
                                        .background(Color(0xFFFFFFFF))
                                )
                            }

                            // Button to get the referral link
//                            ElevatedButton(onClick = {
//                                val referral = deeplinkService.getReferralDetails()
//                                setUI(referral.toString()) // Update UI with referral link
//                            }) {
//                                Text("Get Referral Link")
//                            }

                            // Button to trigger API call
                            ElevatedButton(
                                onClick = {
                                    isLoading = true // Show loader
                                    val socialMeta = mapOf(
                                        "title" to "link title",
                                        "description" to "link description",
                                        "imageUrl" to "your meta image url"
                                    )

                                    CoroutineScope(Dispatchers.Main).launch {
                                        val result = deeplinkService.createAppLink(
                                            name = "AppsOnAir",
                                            url = "https://appsonair.com",
                                            urlPrefix = "your url prefix",
                                            socialMeta = socialMeta,
                                            androidFallbackUrl = "www.playstore/app.com",
                                            iOSFallbackUrl = "www.appstore/app.com",
                                        )
                                        Log.d("API response==>", result.toString())
                                        setUI(result.toString())
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
    }
}
