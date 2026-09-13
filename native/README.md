# Native apps

## Structure

- `androidApp/`: Jetpack Compose Android UI and platform integrations.
- `iosApp/`: SwiftUI iOS UI and platform integrations.
- `shared/`: Kotlin Multiplatform API clients, authentication, profile, reports, session storage, drafts, and use cases.

Both apps use unidirectional data flow. See [UDF.md](UDF.md) for the state/action/effect contract.
The native apps include media capture and selection, location, complaint animations, report submission, social sign-in, and on-device analysis features.

## Prerequisites

- Android: JDK 21, Android SDK, and an emulator or connected device. Use the checked-in Gradle wrapper.
- iOS: macOS, Xcode with the iOS SDK, XcodeGen, and JDK 21 for the shared Kotlin framework build.
- Configure Parse before building, as described below. Google sign-in and other provider integrations also require configuration for your own accounts; see `androidApp/build.gradle.kts` and `iosApp/project.yml` for supported settings.

## Android

From this directory:

```sh
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

Set `sdk.dir` in ignored `local.properties` if your Android SDK is not found automatically.

## iOS

From this directory:

```sh
cd iosApp
xcodegen generate
open ReportediOS.xcodeproj
```

Select the `ReportediOS` scheme and an installed simulator, then Run. Xcode resolves Swift packages and builds the shared Kotlin framework. Physical-device and distribution builds require your own Apple signing setup.
Regenerate the Xcode project after changing `project.yml`.

## Parse configuration

Set these environment variables before running Gradle or `xcodebuild`:

```bash
export REPORTED_PARSE_SERVER_URL="https://your-parse-server.example"
export REPORTED_PARSE_APPLICATION_ID="your-application-id"
export REPORTED_PARSE_JAVASCRIPT_KEY="your-client-key"
```

For local development, both builds also read these names as `NAME=value` entries
in ignored `native/local.properties`. Environment variables take precedence.
Android additionally supports Gradle properties. Missing or blank settings fail
the build. CI should supply all three environment variables.

iOS generates `ParseConfig.plist` inside the built app; runtime environment
variables can override it when launching from Xcode. After changing `project.yml`,
run `xcodegen generate` in `native/iosApp`. Rebuild after changing configuration.
The client credentials are still bundled in the apps; do not use a Parse master
key here. Removing source literals does not remove previous Git history.

Dependency and plugin versions are centralized in `gradle/libs.versions.toml`. The build scripts remain Kotlin DSL. Both Android modules use the Java 21 toolchain. On macOS, set `JAVA_HOME` with `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

Android `minSdk`, `targetSdk`, `versionCode`, and `versionName` are configured in `gradle.properties` using the `reported.` prefix. Both Android modules share `reported.minSdk`.

The Android build uses AGP 9.4 / Gradle 9.6 with built-in Kotlin; `shared` uses the Android-KMP library plugin. Compile SDK is 37; target and minimum SDK remain in `gradle.properties`. Run shared JVM tests with `./gradlew :shared:testAndroidHostTest`.
