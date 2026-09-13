plugins {
    alias(libs.plugins.orgJetbrainsKotlinMultiplatform)
    alias(libs.plugins.comAndroidLibrary)
    alias(libs.plugins.orgJetbrainsKotlinPluginSerialization)
}

if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
    tasks.register("prepareKotlinBuildScriptModel")
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "com.reported.shared"
        compileSdk = 37
        minSdk = providers.gradleProperty("reported.minSdk").get().toInt()
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        withHostTestBuilder {}.configure {}
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "SharedCore"
            isStatic = true
        }
    }

    sourceSets {

        commonMain.dependencies {
            implementation(libs.ktorClientCore)
            implementation(libs.ktorClientContentNegotiation)
            implementation(libs.ktorSerializationKotlinxJson)
            implementation(libs.ktorClientLogging)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kotlinxSerializationJson)
            implementation(libs.kotlinxDatetime)
            implementation(libs.multiplatformSettingsNoArg)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktorClientOkhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktorClientDarwin)
        }
    }
}
