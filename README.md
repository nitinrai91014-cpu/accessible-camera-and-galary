# Accessible Camera and Gallery

An accessible, open-source camera and gallery app for Android, built so that a blind or
low-vision person can take a well-framed photo, record a video, find it again, edit it and
share it without sighted help.

Developed by **Sujan Rai** of the **Blind Tech Nexus team**. Licensed under the Apache
License 2.0.

## What makes it different

Most camera apps assume you can see the viewfinder. This one assumes you cannot:

- Every control has a name, role and state for TalkBack, and a spoken result after activation.
- The preview itself is described: mode, camera facing, zoom, flash, timer, face alignment
  and scene description are all spoken on demand and on change.
- Focus order follows the visual order, focus is restored when you come back, and every
  target is at least 48 dp with the shutter and primary camera controls larger than that.
- ML Kit face detection drives spoken alignment guidance ("Move your face left", "Turn your
  head to the right", "Face aligned. Hold still.") so a selfie can be framed by ear.
- No meaning depends on colour alone, the palette meets WCAG AA contrast, and a high contrast
  theme is available.
- Voice guidance is independent of TalkBack: one TTS queue, configurable rate, pitch and
  verbosity, no interrupting critical errors, debounced live values and haptics/audio cues so
  speech is never the only feedback channel.

## Screens

Bottom tabbed navigation with four tabs.

| Tab | Contents |
| --- | --- |
| Photo | Viewfinder, mode selector, quick controls, advanced controls, shutter, review sheet |
| Video | Same layout plus audio toggle, pause/resume, recording timer, photo during video |
| Gallery | Sections (all, photos, videos, screenshots, favorites, trash), search, sort, filters, grid or list, multi-select, details, viewer, editor |
| Settings | Voice, camera, gallery, privacy, reset |

A first-run Welcome screen explains the purpose, the permissions and why they are needed,
opens with an announcement, and gives one large Start button. Secondary screens: media
viewer, editor, item details, device capabilities, voice command help and about.

## Camera features

Modes: photo, video, portrait, night, HDR, panorama, slow motion, time lapse, pro. Modes the
device cannot support are shown with the reason instead of being silently hidden.

Photo: flash and torch, timer with countdown announcements, grid, level indicator, mirror
front camera, geotagging, watermark, RAW plus JPEG, aspect ratio and resolution, pinch and
button zoom, tap to focus, focus and exposure lock, exposure compensation, manual ISO,
shutter speed and focus distance, white balance, metering, stabilization, lens switching
(wide, ultra wide, telephoto), burst, volume key shutter, orientation lock, review after
capture.

Video: resolution, frame rate, bitrate, codec and audio preferences, pause and resume, zoom
while recording, photo during recording, timer announcements, interrupted recording recovery.

Camera2 interop supplies the manual controls through `Camera2Interop.Extender` and
`Camera2CameraControl`, so unsupported combinations degrade gracefully instead of crashing.
A capability registry detected at startup drives the whole UI.

## Gallery features

Date, album, favorites, screenshots, videos and trash sections; grid and list layouts with
adjustable thumbnail size; search; filters; sort order; multi-select and select all; share;
move to trash; restore; permanent delete behind a confirmation; undo snackbar that is also
spoken; details with date, size, resolution, duration, path, album and metadata.

The viewer supports Previous and Next with spoken position, play and pause, seek, zoom,
rotate, slideshow and share, and casts when a cast target is present.

Editing covers crop, rotate, flip, brightness, contrast, saturation, exposure, filters,
annotation, freehand drawing, text, video trim, video mute and export. Every destructive or
exporting action confirms first and undoes where feasible.

## Architecture

MVVM with a strict three layer split.

```
app/src/main/java/com/blindtechnexus/accessiblecamera/
  domain/       models, repository interfaces, use cases  (no Android UI, unit tested)
  data/         CameraX, Camera2 interop, MediaStore, Room, DataStore, ML Kit, TTS, Media3
  ui/           Compose screens, view models, components, theme, navigation
  core/         small pure utilities (formatting, storage, haptics, intents)
  di/           Hilt modules
```

- Kotlin, Jetpack Compose, Material 3, minSdk 26, compile and target SDK 36.
- Hilt for dependency injection, Coroutines and Flow throughout, Navigation Compose.
- DataStore for settings, Room for the media index, favorites, trash, undo history and
  capability cache, MediaStore as the source of truth for files.
- CameraX 1.6 with `camera-extensions` and `camera-mlkit-vision`; Media3 for playback and
  Transformer for video export; Coil for thumbnails and video frames; Paging 3 for the grid.
- A single camera state machine: idle, initializing, ready, focusing, capturing, recording,
  paused, saving, error. No concurrent captures, resources released when the screen stops,
  and storage, camera-in-use and interruption failures surface as human readable, retryable
  messages that are also spoken.

Use cases in `domain/usecase` hold the rules that are easy to get wrong, so they can be
tested without a device: `CaptureReadinessUseCase` gates every shutter press (state, capture
in flight, free space, camera present) and returns the exact sentence to speak;
`GalleryQueryUseCase` owns section membership, search, filtering, sorting and the spoken
result count; `TrashUseCase` owns trash, restore and the "cannot be undone" confirmation.

## Accessibility contract

- `contentDescription` for every icon and image, decorative thumbnails deliberately excluded
  so TalkBack does not repeat the row's description.
- Roles and states on toggles, selected tabs and disabled controls, plus the reason a control
  is unavailable.
- Logical focus order matching visual order, focus restored after dialogs and mode changes.
- Live regions only where a value changes on screen, to avoid duplicate TalkBack output.
- Minimum 48 dp targets; primary camera controls are larger. Layout reflows with font scale
  up to 200 percent and in landscape, inside scrollable containers so nothing clips.
- WCAG AA contrast in both themes, plus a high contrast theme; no colour-only state.
- Fully operable with TalkBack, Switch Access and a hardware keyboard, including volume keys
  as a shutter.
- No emojis anywhere; short UI text; standard Android accessibility APIs only.

## Permissions

Camera and microphone are requested only when used. Photo and video access uses
`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` and `READ_MEDIA_VISUAL_USER_SELECTED` on Android 13
and later, with legacy storage read only where the platform requires it. Saves go through
MediaStore, so no broad storage permission is needed. Location is optional, off by default,
and requested only when geotagging is turned on. There is no internet permission, no
analytics and no tracking.

## Build and run

Requirements: JDK 17, Android SDK platform 36, and Android Studio or the command line tools.

```bash
./gradlew :app:assembleDebug          # debug build
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assembleRelease        # release build
```

The debug APK is written to `app/build/outputs/apk/debug/`. Release builds are unsigned; sign
them with your own key before installing.

Continuous integration runs the unit tests and a release build on every pull request, uploads
the release APK as an artifact and publishes compile or test failures as a pull request
comment.

## Testing notes

Unit tests run on the JVM and cover the face alignment thresholds, voice command parsing, time
and size formatting, settings and capability rules, capture gating, gallery query behaviour and
trash rules. Camera, speech, MediaStore, ML Kit and Media3 behaviour still needs a real device.

Before release, test on real hardware with TalkBack, Switch Access and a keyboard, in portrait
and landscape, with font scale at 200 percent, in both light and high contrast themes, with
permission denial and permanent denial, with low storage, with the camera in use by another
app, and with recordings interrupted by a call.

## Assumptions

- Extensions, RAW, high frame rates, panorama and cast availability are device dependent; the
  capability registry reports what is missing and why rather than pretending support.
- Face alignment uses bounding box and Euler angles only; it guides framing, it is not a
  biometric feature and no face data is stored or transmitted.
- Scene description uses on-device image labelling when the device supports it, at an interval
  the user chooses, and is off by default.
- On-device speech recognition requires Android 13 or later with a supporting device; older
  devices fall back to the system recogniser, which the user can decline.
- Video resolution and frame rate choices are constrained by what CameraX and the encoder
  report for the selected lens.

## Credits

Sujan Rai, Blind Tech Nexus team. Built with Jetpack Compose, CameraX, ML Kit and Media3.
