plugins {
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}

subprojects {
    // Android Studio may ask individual modules for this task during Kotlin DSL sync.
    // Gradle provides it on the root build, but not consistently on child projects.
    tasks.register("prepareKotlinBuildScriptModel")
}
