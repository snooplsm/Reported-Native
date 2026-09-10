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
