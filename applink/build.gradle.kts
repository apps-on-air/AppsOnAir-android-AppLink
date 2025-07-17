plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
}

group = "com.appsonair"
version = "1.0.0"

val projectProperties = listOf(
    "BASE_URL",
    "VERSION_CODE",
    "VERSION_NAME"
).associateWith { project.property(it) as String }

android {
    namespace = "com.appsonair.applink"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        projectProperties.forEach { (key, value) ->
            buildConfigField("String", key, "\"$value\"")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    publishing {
        // Tell Android to publish the release variant
        singleVariant("release")
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.okhttp)
    implementation(libs.installreferrer)
    implementation(libs.appsonair.android.core)
    implementation(libs.androidx.ui.test.junit4.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.appsonair"
            artifactId = "applink"
            version = "1.0.0"

            // Wait for Android to finish configuration
            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        mavenLocal()
    }
}