# BTC Lottery for Android

An Android interface for the hosted Bitcoin nonce checker.

## Build

Requires JDK 17 and Android SDK 36. Open this folder in Android Studio, or set `ANDROID_HOME` to your SDK folder and run:

```powershell
./gradlew.bat assembleDebug
```

The installable APK is `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
./gradlew.bat bundleRelease
```

The release bundle is `app/build/outputs/bundle/release/app-release.aab`. It must be signed with your upload key before submitting to Google Play. The debug APK is for trying the app, not a Play release.

## Use

Enter your receiving address and nonce guesses, then tap **Check my guesses**. Numbers must be between 0 and 4294967295. Duplicate guesses are checked once without changing any numbers. A valid result is submitted automatically by the Worker. The fixed split is 95% to the entered address and 5% to the operator.

History stays on the phone. **About → Clear saved address and history** removes it. The app requests only internet access and contains no node credentials, advertising, or analytics SDKs.
