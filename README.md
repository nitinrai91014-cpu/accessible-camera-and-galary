# Accessible Camera and Gallery

An accessible, open-source camera and gallery app for Android. Developed by Sujan Rai of the Blind Tech Nexus team.

## Current scope

This is an early foundation, not a complete production camera. It provides CameraX photo capture and video recording with optional sound, camera switching, a chronological media list, opening items in another app, a voice guidance toggle, TalkBack-friendly labels, and permission recovery. Media is saved to MediaStore. Android 8 and 9 require storage permission to save. No network permission or analytics is included.

Many requested features are not implemented, including ML Kit face alignment and scene descriptions, advanced camera modes and manual controls, editing, trash, playback within the app, voice commands, and the requested Hilt, Room, DataStore, Navigation and Media3 architecture. The gallery currently loads metadata as a single list rather than paging. Do not rely on this version for accessibility-critical use without device testing. Camera and permission behavior varies by device and Android version; partial photo access on Android 14 is not yet supported. Camera and microphone permissions are requested only when needed. Voice guidance is suppressed when touch exploration is enabled to reduce duplicate TalkBack announcements.

## Build and run

Install Android Studio and Android SDK platform 36, and use JDK 17. Open this directory in Android Studio, sync Gradle, and run the app on an Android 8.0 or newer device. Alternatively run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. The debug APK is in `app/build/outputs/apk/debug/`. The release APK built by CI is unsigned and must be signed with your own release key before installation. CI also uploads a signed debug APK for testing; it is not a production release.

Test with real devices and TalkBack, keyboard and Switch Access, including permission denial, low storage, backgrounding, and front and back cameras before release.
