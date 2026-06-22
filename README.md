# SOME FUSION

Native Android camera app for natural-looking still photos.

## Build

Open this folder in Android Studio and run the `app` configuration on a connected Android device.

Command-line debug build:

```powershell
$env:JAVA_HOME="C:\Users\Tom\.bubblewrap\jdk\jdk-17.0.11+9"
.\gradlew.bat :app:assembleDebug
```

The app is Android-only. The PC is only the development machine.

## Current Scope

- Full-screen still-photo camera UI.
- JPEG capture.
- RAW+DNG plus JPEG when the selected camera reports support.
- Core manual controls with unsupported controls shown disabled.
- Focus zoom toggle for manual focus assist.
- Tap-to-focus with a visible focus reticle.
- Floating Live Mode starts a visible foreground camera service with an over-apps REC/STOP control for recording while using other apps.
- Visible build model and Git commit label in the UI.

## Install Output

After a successful debug build, the APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```
