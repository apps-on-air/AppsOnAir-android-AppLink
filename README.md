## ![pub package](https://appsonair.com/images/logo.svg)
# AppsOnAir-android-AppLink

**AppsOnAir-android-AppLink** enables you to handle deep links, and in-app routing seamlessly in your Android app. With a simple integration, you can configure, manage, and act on links from the web dashboard in real time and for more detail refer [documentation](https://documentation.appsonair.com/MobileQuickstart/GettingStarted/).

## ⚠️ Important Notice ⚠️

This plugin is currently in **pre-production**. While the plugin is fully functional, the supported services it integrates with are not yet live in production. Stay tuned for updates as we bring our services to production!

## 🚀 Features

- ✅ Deep link support (URI scheme, App Links)
- ✅ Fallback behavior (e.g., open Play Store)
- ✅ Custom domain support
- ✅ Seamless firebase dynamic link migration to AppLink(Coming Soon)

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

>Make sure meta-data name is “appId”.

>Provide your application id in meta-data value.


```sh
</application>
    ...
    <meta-data
        android:name="appId"
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


## Example :

#### Initialize the AppLink

```sh

    private lateinit var deeplinkService: AppLinkService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize deeplink service and set listener for deep link and referral link events
        deeplinkService = AppLinkService.getInstance(this)
        // Initialize the app link to track the deeplink
        deeplinkService.initialize(this, intent, object : AppLinkListener {
            override fun onDeepLinkProcessed(uri: Uri, params: Map<String, String>) {
                // Store the processed deep link URL and log the parameters
            }

            override fun onDeepLinkError(uri: Uri?, error: String) {
                // Handle error when deep link processing fails
            }
        })
    }

```

```
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplinkService.handleDeepLink(
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

     val result = deeplinkService.createAppLink(
        name = "AppsOnAir",
        url = "https://appsonair.com",
        urlPrefix = "YOUR_DOMAIN_NAME", //shouldn't contain http or https
        shortId = "LINK_ID",
        socialMeta = socialMeta,
        androidFallbackUrl = "www.playstore/app.com",
        iOSFallbackUrl = "www.appstore/app.com",
    )
```