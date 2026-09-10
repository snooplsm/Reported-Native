plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
}

val verifyUdfArchitecture by tasks.registering {
    group = "verification"
    description = "Requires every Android and iOS ViewModel to use the native UDF contract."

    val androidSources = fileTree("androidApp/src/main/java") { include("**/*.kt") }
    val iosSources = fileTree("iosApp/ReportediOS/Sources") { include("**/*.swift") }
    inputs.files(androidSources, iosSources)

    doLast {
        val viewModelDeclaration = Regex(
            pattern = """(?:private\s+)?(?:final\s+)?class\s+\w+ViewModel\b[^\{]*\{""",
            options = setOf(RegexOption.MULTILINE)
        )
        val violations = (androidSources.files + iosSources.files)
            .sortedBy { it.path }
            .flatMap { source ->
                viewModelDeclaration.findAll(source.readText())
                    .map { it.value }
                    .filterNot { "UdfStore" in it }
                    .map { "${source.relativeTo(rootDir)}: ${it.lineSequence().first().trim()}" }
                    .toList()
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Every ViewModel must implement UdfStore.\n" + violations.joinToString("\n")
            )
        }
    }
}

subprojects {
    // Android Studio may ask individual modules for this task during Kotlin DSL sync.
    // Gradle provides it on the root build, but not consistently on child projects.
    tasks.register("prepareKotlinBuildScriptModel")
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(verifyUdfArchitecture)
    }
}
