# SOME FUSION

Native Android camera app for natural-looking still photos.

## Build

Open this folder in Android Studio and run the `app` configuration on a connected Android device.

Command-line debug build:

```powershell
$env:JAVA_HOME="C:\Users\Tom\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
.\gradlew.bat :app:assembleDebug
```

The app is Android-only. The PC is only the development machine.

## Current Scope

- Full-screen still-photo camera UI.
- JPEG capture.
- RAW+DNG plus JPEG when the selected camera reports support.
- Core manual controls with unsupported controls shown disabled.
- Visible build model and Git commit label in the UI.

## Install Output

After a successful debug build, the APK is written to:

```text
app\build\outputs\apk\debug\app-debug.apk
```
