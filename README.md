# किसान फार्म — Farm Manager (Android APK)

A WebView wrapper around the self-contained Farm Manager web app
(`app/src/main/assets/index.html`). Works fully offline. Data is stored
in the app's WebView storage; backups save to the phone's **Downloads**
folder as `FarmBackup_YYYY-MM-DD.json`.

- Package: `com.radhe.farm`
- minSdk 29 (Android 10) · targetSdk 34 · runs on Android 10 → latest
- No INTERNET permission (offline)

## How the APK is built (free, on GitHub)

This repo includes a GitHub Actions workflow (`.github/workflows/build.yml`)
that builds a debug APK automatically on every push.

1. Push this project to a GitHub repository.
2. Open the repo on GitHub → **Actions** tab → wait for **Build APK** to finish (green tick, ~3–5 min).
3. Open that run → scroll to **Artifacts** → download **FarmManager-APK** (a zip).
4. Unzip → you get **app-debug.apk**.

## Install on the phone (no Play Store)

1. Copy `app-debug.apk` to the Android phone.
2. Tap it → Android will ask to allow **"Install from unknown apps"** → allow for your file manager / browser.
3. Install → the **किसान फार्म** icon appears on the home screen.

## Build locally (optional, for developers)

Needs JDK 17 + Android SDK (compileSdk 34). Then:

```
gradle assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## Updating the app

Replace `app/src/main/assets/index.html` with a newer export of the web
app and rebuild. Bump `versionCode`/`versionName` in `app/build.gradle`.
