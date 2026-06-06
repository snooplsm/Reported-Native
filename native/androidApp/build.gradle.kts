import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
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

fun readOptionalConfigValue(name: String): String? =
    providers.environmentVariable(name).orNull
        ?: providers.gradleProperty(name).orNull
        ?: readLocalProperty(name)

fun readGoogleServicesWebClientId(): String? {
    val googleServicesFile = project.file("google-services.json")
    if (!googleServicesFile.exists()) return null

    val root = JsonSlurper().parse(googleServicesFile) as? Map<*, *> ?: return null
    val clients = root["client"] as? List<*> ?: return null
    return clients
        .asSequence()
        .mapNotNull { it as? Map<*, *> }
        .flatMap { client ->
            ((client["oauth_client"] as? List<*>) ?: emptyList<Any?>()).asSequence()
        }
        .mapNotNull { it as? Map<*, *> }
        .firstOrNull { oauthClient ->
            (oauthClient["client_type"] as? Number)?.toInt() == 3
        }
        ?.get("client_id") as? String
}

val googleMapsApiKey = readConfigValue("REPORTED_ANDROID_GOOGLE_MAPS_API_KEY")
val googleServicesWebClientId = readGoogleServicesWebClientId()
    ?: "131272311428-b58gmcm6ucc0v0acooeic2c748bb6odh.apps.googleusercontent.com"
val configuredGoogleServerClientId = readOptionalConfigValue("REPORTED_GOOGLE_SERVER_CLIENT_ID")
val googleServerClientId = configuredGoogleServerClientId
    ?.takeIf { it.substringBefore("-") == googleServicesWebClientId.substringBefore("-") }
    ?: googleServicesWebClientId
val appleClientId = readConfigValue("REPORTED_APPLE_CLIENT_ID")
val appleRedirectUri = readConfigValue("REPORTED_APPLE_REDIRECT_URI", "reported://oauth/apple")
val parseServerUrl = readConfigValue("REPORTED_PARSE_SERVER_URL", "https://parseapi.back4app.com")
val parseApplicationId = readConfigValue("REPORTED_PARSE_APPLICATION_ID", "jkAZF8ojV4vOGnhSBjdwiMWBKpWML5tM4SWGKgOV")
val parseJavascriptKey = readConfigValue("REPORTED_PARSE_JAVASCRIPT_KEY", "LeBKOerWTXGBGRLE0yvg2bXa5RRv4e8PuC6INEFA")
val releaseKeystorePath = readOptionalConfigValue("REPORTED_ANDROID_KEYSTORE_PATH")
val releaseKeystoreAlias = readOptionalConfigValue("REPORTED_ANDROID_KEYSTORE_ALIAS")
val releaseKeystorePassword = readOptionalConfigValue("REPORTED_ANDROID_KEYSTORE_PASSWORD")
val releaseKeyPassword = readOptionalConfigValue("REPORTED_ANDROID_KEY_PASSWORD") ?: releaseKeystorePassword
val hasReleaseSigningConfig = listOf(
    releaseKeystorePath,
    releaseKeystoreAlias,
    releaseKeystorePassword,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.reported.nativeandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "cab.reported.nyc"
        minSdk = 25
        targetSdk = 36
        versionCode = 95
        versionName = "3.0.10"
        buildConfigField("String", "API_BASE_URL", quotedEnv("REPORTED_API_BASE_URL", "https://reported-stats.herokuapp.com/prod/"))
        buildConfigField("String", "PARSE_SERVER_URL", "\"$parseServerUrl\"")
        buildConfigField("String", "PARSE_APPLICATION_ID", "\"$parseApplicationId\"")
        buildConfigField("String", "PARSE_JAVASCRIPT_KEY", "\"$parseJavascriptKey\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
        buildConfigField("String", "APPLE_CLIENT_ID", "\"$appleClientId\"")
        buildConfigField("String", "APPLE_REDIRECT_URI", "\"$appleRedirectUri\"")
        buildConfigField("Boolean", "SHOW_COMPLAINT_IMAGES", readConfigValue("REPORTED_SHOW_COMPLAINT_IMAGES", "true"))
        buildConfigField("Boolean", "ENABLE_LIVE", readConfigValue("REPORTED_ENABLE_LIVE", "false"))
        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeystoreAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
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

gradle.taskGraph.whenReady {
    val requestedSignedRelease = allTasks.any { task ->
        task.path == ":androidApp:bundleRelease" ||
            task.path == ":androidApp:assembleRelease" ||
            task.path == ":androidApp:signReleaseBundle"
    }
    if (requestedSignedRelease && !hasReleaseSigningConfig) {
        throw GradleException(
            "Release signing is not configured. Export REPORTED_ANDROID_KEYSTORE_PATH, " +
                "REPORTED_ANDROID_KEYSTORE_ALIAS, REPORTED_ANDROID_KEYSTORE_PASSWORD, and optionally " +
                "REPORTED_ANDROID_KEY_PASSWORD before building a release."
        )
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
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
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
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.mlkit:genai-image-description:1.0.0-beta1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.airbnb.android:lottie-compose:6.6.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
