## ![pub package](https://appsonair.com/images/logo.svg)
# AppsOnAir-android-AppLink

**AppsOnAir-android-AppLink** enables you to handle deep links, and in-app routing seamlessly in your Android app. With a simple integration, you can configure, manage, and act on links from the web dashboard in real time and for more detail refer [documentation](https://documentation.appsonair.com/MobileQuickstart/GettingStarted/).

## 🚀 Features

- ✅ Deep link support (URI scheme, AppLinks)
- ✅ Fallback behavior (e.g., open Play Store)
- ✅ Custom domain support
- ✅ Referral tracking
- ✅ Seamless migration from Firebase Dynamic Links to AppLink

**Note:** For comprehensive instructions on migrating Firebase Dynamic Links to AppLink, refer to the [documentation](https://documentation.appsonair.com/MobileQuickstart/AppLink/firebase-dynamiclinks-migration).

## Minimum Requirements

- Android Gradle Plugin (AGP): Version 8.0.2 or higher
- Kotlin: Version 1.7.10 or higher
- Gradle: Version 8.0 or higher


## How to use?

#### Add AppsOnAir AppLink dependency to your gradle.

```sh
dependencies {
   implementation 'com.github.apps-on-air:AppsOnAir-android-AppLink:TAG'
}
```

#### Add below code to setting.gradle.

```sh
dependencyResolutionManagement {
   repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
   repositories {
       google()
       mavenCentral()
       maven {
           url = uri("https://jitpack.io")
       }
   }
}
```

#### Add meta-data to the app's AndroidManifest.xml file under the application tag.

>Make sure meta-data name is “AppsonairAppId”.

>Provide your application id in meta-data value.


```sh
</application>
    ...
    <meta-data
        android:name="AppsonairAppId"
        android:value="********-****-****-****-************" />
</application>
```

#### Add below code to the app's AndroidManifest.xml file under the activity tag of your main activity.

```sh
 <intent-filter android:autoVerify="true">
   <action android:name="android.intent.action.VIEW" />
   <category android:name="android.intent.category.DEFAULT" />
   <category android:name="android.intent.category.BROWSABLE" />
    <data
     android:host="your domain"
     android:scheme="https" />
 </intent-filter>
```
#### Add below code if you are using custom uri scheme.
```sh
 <intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
     android:host="open"
     android:scheme="your scheme" />
</intent-filter>
```


## Example :

#### Initialize the AppLink

```sh

    private lateinit var appLinkService: AppLinkService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize deeplink service and set listener for deep link and attribution events
        appLinkService = AppLinkService.getInstance(this)
        // Initialize the AppLink to track the deeplink
        appLinkService.initialize(this, intent, object : AppLinkListener {
            override fun onDeepLinkProcessed(uri: Uri, result: JSONObject) {
                // Perform your action on deep link
            }

            override fun onDeepLinkError(uri: Uri?, error: String) {
                // Handle error when deep link processing fails
            }
            override fun onAttributionListener(result: JSONObject) {
                 // Perform your action on attribution data
            }
        })
    }

```

```
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        appLinkService.handleDeepLink(
            intent,
            "com.example.appsonair_android_applink"
        ) 
    }
```

#### For creating the AppLink
```
    val socialMeta = mapOf(
        "title" to "link title",
        "description" to "link description",
        "imageUrl" to "https://image.png"
    )

    val appsFlyer = mapOf(
        "channel" to "appsonair",
        "campaignId" to "01",
        "campaign" to "test",
        "subs" to listOf("sub1", "sub2", "sub3", "sub4", "sub5"),
        "metaTitle" to "metaTitle",
        "metaDescription" to "metaDescription"
    )

   
CoroutineScope(Dispatchers.Main).launch {
    val result = appLinkService.createAppLink(
        name = "AppsOnAir",
        url = "https://appsonair.com",
        urlPrefix = "YOUR_DOMAIN_NAME", //shouldn't contain http or https
        shortId = "LINK_ID", // If not set, it will be auto-generated
        socialMeta = socialMeta,
        androidFallbackUrl = "https://play.google.com",
        isOpenInAndroidApp = true,
        isOpenInBrowserAndroid = false,
        appsFlyer = appsFlyer, // Optional
        attributionTtl = 3600 // Optional
    )
  }
```

#### To retrieving the attribution info
```
CoroutineScope(Dispatchers.Main).launch {
    val attribution = appLinkService.getAttributionInfo()
}
```

`onAttributionListener()` At first detection, then on the foreground return
that follows `isFirstLaunch` turning `false`. Gate one time logic on `isFirstLaunch`, not on the
callback firing.

Along with the referral details, `getAttributionInfo()` and `onAttributionListener()` add the
following keys inside the `data` object of the response:

| Response Key | Type | Description |
| --- | --- | --- |
| `isFirstLaunch` | Boolean | `true` during the first launch after installation, until the app leaves the foreground. |
| `firstInstallTime` | Long | Timestamp (epoch milliseconds) of the app's first installation. |
| `applink_click_time` | Long | Timestamp (epoch milliseconds) of the click this install is attributed to. Absent when the install referrer carried none. |
| `isConsumed` | Boolean | `true` when `attributionStatus` is `non-organic`. |
| `attributionStatus` | String | `non-organic` when the install happened within `attributionTtl` of the click, `organic` otherwise. |


### Upgrading from 1.3.x

No code change is required: `initialize(context, intent, AppLinkListener)` keeps its signature and
an existing listener compiles unchanged. `AppLinkListener` gains `onAttributionListener()`, which
has a default implementation, so override it only when you want the attribution payload:

```sh
    appLinkService.initialize(this, intent, object : AppLinkListener {
        override fun onDeepLinkProcessed(uri: Uri, result: JSONObject) { }

        override fun onDeepLinkError(uri: Uri?, error: String) { }

        // New in 2.0.0 — optional
        override fun onAttributionListener(result: JSONObject) { }
    })
```

### Deprecated APIs

Deprecated and removed in a future release. Existing integrations keep working:

| Deprecated | Use instead |
| --- | --- |
| `onReferralLinkDetected()` | `onAttributionListener()` |
| `getReferralInfo()` | `getAttributionInfo()` |
| `getReferralDetails()` | `getAttributionInfo()` |

### Note:

To test referral functionality, your app must be live on the Play Store. If it's not, you can use the application ID of any live app instead for testing purposes.

For testing:

- Click the referral link, which should redirect you to the Play Store.

- Do not press the "Install" button. Instead, open the app directly from your IDE (e.g., Android Studio) on the device.