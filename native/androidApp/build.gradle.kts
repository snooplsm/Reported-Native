import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
    tasks.register("prepareKotlinBuildScriptModel")
}

fun quotedEnv(name: String, defaultValue: String): String =
    "\"${System.getenv(name) ?: defaultValue}\""

fun readLocalProperty(name: String): String? {
    val localPropertiesFile = rootProject.file("local.properties")
    if (!localPropertiesFile.exists()) return null
    val properties = Properties()
    localPropertiesFile.inputStream().use(properties::load)
    return properties.getProperty(name)
}

fun readConfigValue(name: String, defaultValue: String = ""): String =
    providers.environmentVariable(name).orNull
        ?: providers.gradleProperty(name).orNull
        ?: readLocalProperty(name)
        ?: defaultValue

val googleMapsApiKey = readConfigValue("REPORTED_ANDROID_GOOGLE_MAPS_API_KEY")
val googleServerClientId = readConfigValue("REPORTED_GOOGLE_SERVER_CLIENT_ID", "728528457365-gvkq2phpioo23umg6q0ivtp1aeagdt09.apps.googleusercontent.com")
val appleClientId = readConfigValue("REPORTED_APPLE_CLIENT_ID")
val appleRedirectUri = readConfigValue("REPORTED_APPLE_REDIRECT_URI", "reported://oauth/apple")
val parseServerUrl = readConfigValue("REPORTED_PARSE_SERVER_URL", "https://parseapi.back4app.com")
val parseApplicationId = readConfigValue("REPORTED_PARSE_APPLICATION_ID", "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV")
val parseJavascriptKey = readConfigValue("REPORTED_PARSE_JAVASCRIPT_KEY", "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA")

android {
    namespace = "com.reported.nativeandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "cab.reported.nyc"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", quotedEnv("REPORTED_API_BASE_URL", "https://reported-stats.herokuapp.com/prod/"))
        buildConfigField("String", "PARSE_SERVER_URL", "\"$parseServerUrl\"")
        buildConfigField("String", "PARSE_APPLICATION_ID", "\"$parseApplicationId\"")
        buildConfigField("String", "PARSE_JAVASCRIPT_KEY", "\"$parseJavascriptKey\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        buildConfigField("String", "APPLE_CLIENT_ID", "\"$appleClientId\"")
        buildConfigField("String", "APPLE_REDIRECT_URI", "\"$appleRedirectUri\"")
        buildConfigField("Boolean", "SHOW_COMPLAINT_IMAGES", readConfigValue("REPORTED_SHOW_COMPLAINT_IMAGES", "true"))
        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("../../assets")
        }
    }

}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.maps.android:maps-compose:4.4.1")
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.airbnb.android:lottie-compose:6.6.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
