plugins {
    alias(libs.plugins.comAndroidApplication) apply false
    alias(libs.plugins.comAndroidLibrary) apply false
    alias(libs.plugins.orgJetbrainsKotlinMultiplatform) apply false
    alias(libs.plugins.orgJetbrainsKotlinPluginSerialization) apply false
    alias(libs.plugins.orgJetbrainsKotlinPluginCompose) apply false
    alias(libs.plugins.comGoogleGmsGoogleServices) apply false
    alias(libs.plugins.comGoogleFirebaseCrashlytics) apply false
}

val verifyUdfArchitecture = tasks.register("verifyUdfArchitecture") {
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
