import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.comAndroidApplication)
    alias(libs.plugins.orgJetbrainsKotlinPluginCompose)
    alias(libs.plugins.comGoogleGmsGoogleServices)
    alias(libs.plugins.comGoogleFirebaseCrashlytics)
}

if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
    tasks.register("prepareKotlinBuildScriptModel")
}

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
val parseServerUrl = readConfigValue("REPORTED_PARSE_SERVER_URL").also { require(it.isNotBlank()) { "Set REPORTED_PARSE_SERVER_URL in the environment or native/local.properties" } }
val parseApplicationId = readConfigValue("REPORTED_PARSE_APPLICATION_ID").also { require(it.isNotBlank()) { "Set REPORTED_PARSE_APPLICATION_ID in the environment or native/local.properties" } }
val parseJavascriptKey = readConfigValue("REPORTED_PARSE_JAVASCRIPT_KEY").also { require(it.isNotBlank()) { "Set REPORTED_PARSE_JAVASCRIPT_KEY in the environment or native/local.properties" } }
val philadelphiaAisGatekeeperKey = readConfigValue("REPORTED_PHILADELPHIA_AIS_GATEKEEPER_KEY")
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
    compileSdk = 37

    defaultConfig {
        applicationId = "cab.reported.nyc"
        minSdk = providers.gradleProperty("reported.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("reported.targetSdk").get().toInt()
        versionCode = providers.gradleProperty("reported.versionCode").get().toInt()
        versionName = providers.gradleProperty("reported.versionName").get()
        buildConfigField("String", "PARSE_SERVER_URL", "\"$parseServerUrl\"")
        buildConfigField("String", "PARSE_APPLICATION_ID", "\"$parseApplicationId\"")
        buildConfigField("String", "PARSE_JAVASCRIPT_KEY", "\"$parseJavascriptKey\"")
        buildConfigField("String", "PHILADELPHIA_AIS_GATEKEEPER_KEY", "\"$philadelphiaAisGatekeeperKey\"")
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

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "**/libLiteRt.so",
                "**/libLiteRtClGlAccelerator.so",
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
                "**/libimage_processing_util_jni.so",
                "**/liblitertlm_jni.so",
                "**/libmlkit_google_ocr_pipeline.so",
                "**/libonnxruntime.so",
                "**/libonnxruntime4j_jni.so",
                "**/libsurface_util_jni.so"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("../../assets")
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
    coreLibraryDesugaring(libs.desugarJdkLibs)
    implementation(project(":shared"))

    val composeBom = platform(libs.composeBom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.coreKtx)
    implementation(libs.lifecycleRuntimeKtx)
    implementation(libs.lifecycleRuntimeCompose)
    implementation(libs.activityCompose)
    implementation(libs.cameraCamera2)
    implementation(libs.cameraCore)
    implementation(libs.cameraLifecycle)
    implementation(libs.cameraVideo)
    implementation(libs.cameraView)
    implementation(libs.credentials)
    implementation(libs.credentialsPlayServicesAuth)
    implementation(libs.workRuntimeKtx)
    implementation(libs.lifecycleViewmodelCompose)
    implementation(libs.navigationCompose)
    implementation(libs.material3)
    implementation(libs.materialIconsExtended)
    implementation(libs.ui)
    implementation(libs.uiToolingPreview)
    implementation(libs.exifinterface)
    implementation(libs.media3Exoplayer)
    implementation(libs.media3Ui)
    implementation(libs.playServicesMaps)
    implementation(libs.googleid)
    implementation(libs.mapsCompose)
    implementation(platform(libs.firebaseBom))
    implementation(libs.firebaseAnalytics)
    implementation(libs.firebaseCrashlytics)
    implementation(libs.firebaseConfig)
    implementation(libs.firebaseFirestore)
    implementation(libs.genaiImageDescription)
    implementation(libs.textRecognition)
    implementation(libs.litertlmAndroid)
    implementation(libs.coilCompose)
    implementation(libs.coilSvg)
    implementation(libs.lottieCompose)
    implementation(libs.onnxruntimeAndroid)
    debugImplementation(libs.uiTooling)
    debugImplementation(libs.uiTestManifest)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
