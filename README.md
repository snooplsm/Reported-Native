# Reported Native

This repository is now centered on the native migration workspace in [`/Users/snooplsm/Reported-Native/native`](/Users/snooplsm/Reported-Native/native).

## Main projects

- Android app: [`/Users/snooplsm/Reported-Native/native/androidApp`](/Users/snooplsm/Reported-Native/native/androidApp)
- iOS app: [`/Users/snooplsm/Reported-Native/native/iosApp`](/Users/snooplsm/Reported-Native/native/iosApp)
- Shared Kotlin Multiplatform code: [`/Users/snooplsm/Reported-Native/native/shared`](/Users/snooplsm/Reported-Native/native/shared)

## Running locally

Android:

```bash
cd /Users/snooplsm/Reported-Native/native
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug
```

iOS:

```bash
cd /Users/snooplsm/Reported-Native/native/iosApp
xcodegen generate
open ReportediOS.xcodeproj
```

## Legacy code

Some old React Native / Expo-era source files are still present at the repository root as migration reference material, but the Expo project itself is being removed and is no longer the intended build path.
