# Native Migration Workspace

This `native/` folder is the start of the Expo-to-native conversion:

- `shared/`: Kotlin Multiplatform shared API, repositories, session persistence, and use cases.
- `androidApp/`: Jetpack Compose Android app using unidirectional data flow (UDF).
- `iosApp/`: SwiftUI iOS app in Swift, generated with XcodeGen, also using UDF.

UDF is required for every feature on both platforms. See [UDF.md](UDF.md) for the state/action/effect contract and the boundary for UI- and ML-owned work.

## Generate the projects

From `native/`:

```bash
./gradlew wrapper
./gradlew :androidApp:assembleDebug
cd iosApp
xcodegen generate
```

Then open `iosApp/ReportediOS.xcodeproj` in Xcode.

## Notes

- The shared KMP layer already covers auth, profile, reports, report submission, complaint catalogs, and local draft persistence.
- Both native apps now use a richer report composer with:
  - plate plus state/region
  - complaint selection from shared categories
  - occurred-at timestamp entry
  - draft load/save through the shared KMP layer
- Media capture/upload, geocoding/location, notifications, analytics, and ALPR-native integrations are still the next migration slice.
- The UI is intentionally visually close to the Expo app, but not pixel-for-pixel parity yet.

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
