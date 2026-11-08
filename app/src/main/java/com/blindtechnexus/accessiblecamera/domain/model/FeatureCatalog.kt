/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/** Whether a feature can be used, and why not when it cannot. */
sealed interface FeatureSupport {
    data object Supported : FeatureSupport

    /** The device does not report the hardware or software the feature needs. */
    data class Unsupported(val reason: String) : FeatureSupport

    /** The feature works but needs a one-time setup step. */
    data class NeedsSetup(val reason: String) : FeatureSupport

    val isSupported: Boolean get() = this is Supported

    val statusText: String
        get() = when (this) {
            Supported -> "Supported on this device."
            is Unsupported -> "Not supported on this device. $reason"
            is NeedsSetup -> "Supported, but needs setup. $reason"
        }
}

/** One explainable feature row on the About screen. */
data class FeatureInfo(
    val title: String,
    val purpose: String,
    val howToUse: String,
    val support: FeatureSupport
) {
    val spokenSummary: String
        get() = "$title. $purpose. $howToUse ${support.statusText}"
}

/** A heading plus its features, kept in reading order. */
data class FeatureGroup(
    val title: String,
    val features: List<FeatureInfo>
)

/**
 * Single source of truth for the About screen's feature guide.
 *
 * Every entry states what the feature is for, how to use it and whether this exact device
 * supports it. Availability is derived from the capability registry and platform checks, so
 * the guide can never claim a feature exists when the hardware does not report it.
 */
object FeatureCatalog {

    fun build(
        capabilities: CameraCapabilities,
        mlKitModelsReady: Boolean,
        mlKitModelsInstalling: Boolean = false
    ): List<FeatureGroup> {
        val hasCamera = capabilities.hasAnyCamera
        val hasVideo = capabilities.isModeAvailable(CameraMode.VIDEO)
        val hasFlash = capabilities.hasFlash
        val hasAutofocus = capabilities.lenses.any { it.hasAutofocus }
        val hasZoom = capabilities.maxZoom > 1.01f
        val hasManual = capabilities.supportsManualControls
        val hasRaw = capabilities.supportsRawCapture
        val hasOptical = capabilities.lenses.any { it.supportsOpticalStabilization }
        val hasTwoCameras = capabilities.hasFrontCamera && capabilities.hasBackCamera

        val cameraUnavailable = FeatureSupport.Unsupported("No usable camera was reported by Android.")

        fun cameraSupport(supported: Boolean, reason: String): FeatureSupport =
            if (!hasCamera) cameraUnavailable else if (supported) FeatureSupport.Supported else FeatureSupport.Unsupported(reason)

        val mlKitSupport = when {
            mlKitModelsReady -> FeatureSupport.Supported
            mlKitModelsInstalling -> FeatureSupport.NeedsSetup(
                "The on-device models are installing now. Wait for the progress dialog to finish."
            )
            else -> FeatureSupport.NeedsSetup(
                "Open the camera and accept the Download Required dialog, or install the models from Settings."
            )
        }

        val capture = FeatureGroup(
            title = "Taking photos",
            features = listOf(
                FeatureInfo(
                    title = "Photo capture",
                    purpose = "Takes a single still photo and saves it to your gallery.",
                    howToUse = "Open the Photo tab and double tap the large round shutter button at the bottom.",
                    support = cameraSupport(true, "Photo capture needs a camera.")
                ),
                FeatureInfo(
                    title = "Front and rear camera switching",
                    purpose = "Changes between the rear camera and the selfie camera.",
                    howToUse = "Double tap the Flip button in the top bar. It reads Switch to Front Camera button or Switch to Rear Camera button depending on the lens in use.",
                    support = cameraSupport(hasTwoCameras, "This device reports only one usable camera.")
                ),
                FeatureInfo(
                    title = "Flashlight and torch",
                    purpose = "Keeps the light on continuously so you can frame a shot in the dark.",
                    howToUse = "In Photo mode, turn on the Flashlight switch in the control bar. Turn it off with the same switch.",
                    support = cameraSupport(hasFlash, "This camera has no flash unit.")
                ),
                FeatureInfo(
                    title = "Photo flash",
                    purpose = "Fires the flash for a single photo instead of leaving it on.",
                    howToUse = "Double tap the Flash button in the top bar to cycle Off, Auto, On and Torch.",
                    support = cameraSupport(hasFlash, "This camera has no flash unit.")
                ),
                FeatureInfo(
                    title = "Zoom",
                    purpose = "Moves the image closer or further away without moving the phone.",
                    howToUse = "Use the Zoom in, Zoom out and zoom level buttons in the control bar. Double tap the number to reset.",
                    support = cameraSupport(hasZoom, "This camera reports a fixed zoom range.")
                ),
                FeatureInfo(
                    title = "Tap to focus",
                    purpose = "Focuses and meters exposure on a specific part of the scene.",
                    howToUse = "Double tap anywhere on the preview. The app announces where focus locked.",
                    support = cameraSupport(hasAutofocus, "This camera has fixed focus.")
                ),
                FeatureInfo(
                    title = "Self timer",
                    purpose = "Waits a few seconds before taking the photo so you can get into position.",
                    howToUse = "Double tap the Timer button to cycle Off, 3, 5 and 10 seconds, then press the shutter.",
                    support = cameraSupport(true, "A timer needs a camera.")
                ),
                FeatureInfo(
                    title = "Burst capture",
                    purpose = "Takes up to twenty photos in a row so you can pick the best one.",
                    howToUse = "Open More, then activate Burst capture. Activate it again to stop.",
                    support = cameraSupport(capabilities.supportsBurst, "Burst capture is unavailable.")
                ),
                FeatureInfo(
                    title = "Grid lines and level",
                    purpose = "Guides composition and helps keep the phone straight. The level is also spoken.",
                    howToUse = "Open More and turn on Grid lines or Level indicator. The same switches live in Settings, Camera.",
                    support = cameraSupport(true, "Guides are drawn over the preview, so they need a camera.")
                )
            )
        )

        val proCapture = FeatureGroup(
            title = "Advanced camera controls",
            features = listOf(
                FeatureInfo(
                    title = "Manual ISO, shutter speed and focus",
                    purpose = "Gives full control of exposure and focus for difficult light.",
                    howToUse = "Open More, choose Pro or manual mode, then adjust Exposure mode, ISO, Shutter speed and Focus distance.",
                    support = cameraSupport(hasManual, "This camera does not report manual ISO, shutter or focus control.")
                ),
                FeatureInfo(
                    title = "RAW plus JPEG capture",
                    purpose = "Saves an unprocessed image next to the JPEG for later editing.",
                    howToUse = "Open More and change Photo format to RAW and JPEG.",
                    support = cameraSupport(hasRaw, "This camera does not report RAW capture support.")
                ),
                FeatureInfo(
                    title = "White balance and metering",
                    purpose = "Corrects colour casts and changes how brightness is measured.",
                    howToUse = "Open More and cycle White balance or Metering.",
                    support = cameraSupport(
                        capabilities.lenses.any { it.supportsWhiteBalanceControl },
                        "This camera does not report white balance control."
                    )
                ),
                FeatureInfo(
                    title = "Video stabilization",
                    purpose = "Reduces hand shake while recording.",
                    howToUse = "Open More and cycle Stabilization between Off, Standard, High and Optical.",
                    support = cameraSupport(
                        capabilities.lenses.any { it.supportsVideoStabilization } || hasOptical,
                        "This camera does not report video stabilization."
                    )
                ),
                FeatureInfo(
                    title = "Optical stabilization",
                    purpose = "Uses the lens hardware to steady the shot.",
                    howToUse = "Open More, cycle Stabilization until it reads Optical.",
                    support = cameraSupport(hasOptical, "This camera has no optical stabilization.")
                ),
                FeatureInfo(
                    title = "Portrait, Night and High dynamic range modes",
                    purpose = "Uses vendor camera extensions for background blur, low light and bright scenes.",
                    howToUse = "Double tap the mode button in the top bar or use the mode row above the shutter.",
                    support = if (
                        capabilities.isModeAvailable(CameraMode.PORTRAIT) ||
                        capabilities.isModeAvailable(CameraMode.NIGHT) ||
                        capabilities.isModeAvailable(CameraMode.HDR)
                    ) {
                        FeatureSupport.Supported
                    } else {
                        FeatureSupport.Unsupported("This device exposes no camera extensions for these modes.")
                    }
                )
            )
        )

        val video = FeatureGroup(
            title = "Recording video",
            features = listOf(
                FeatureInfo(
                    title = "Video recording",
                    purpose = "Records video with optional sound.",
                    howToUse = "Open the Video tab and double tap the shutter to start, then double tap again to stop.",
                    support = cameraSupport(hasVideo, "This device cannot record video through CameraX.")
                ),
                FeatureInfo(
                    title = "Recording sound",
                    purpose = "Adds the microphone to the recording.",
                    howToUse = "On the Video tab, toggle the Sound button, and allow microphone access when asked.",
                    support = cameraSupport(capabilities.supportsAudioRecording, "This device reports no microphone.")
                ),
                FeatureInfo(
                    title = "Pause and resume recording",
                    purpose = "Skips unwanted moments without creating a second file.",
                    howToUse = "While recording, double tap the Pause recording button, then Resume recording.",
                    support = cameraSupport(hasVideo, "Pausing requires video recording.")
                ),
                FeatureInfo(
                    title = "Photo while recording",
                    purpose = "Takes a still photo without stopping the video.",
                    howToUse = "While recording, double tap the shutter button.",
                    support = cameraSupport(capabilities.supportsPhotoDuringVideo && hasVideo, "Still capture during recording is unavailable.")
                ),
                FeatureInfo(
                    title = "Video resolution, frame rate and bitrate",
                    purpose = "Controls file size and detail.",
                    howToUse = "Open More while on the Video tab and change Video resolution, Frame rate or Video bitrate.",
                    support = cameraSupport(capabilities.videoQualities.isNotEmpty(), "This device reports no video qualities.")
                ),
                FeatureInfo(
                    title = "Slow motion",
                    purpose = "Records at a high frame rate for slow playback.",
                    howToUse = "Choose Slow motion from the mode list, then record.",
                    support = cameraSupport(capabilities.isModeAvailable(CameraMode.SLOW_MOTION), "This camera does not record fast enough for slow motion.")
                ),
                FeatureInfo(
                    title = "Time lapse and panorama",
                    purpose = "Captures a long period or a wide scene into one result.",
                    howToUse = "Choose Time lapse or Panorama from the mode list.",
                    support = cameraSupport(
                        capabilities.isModeAvailable(CameraMode.TIME_LAPSE) || capabilities.isModeAvailable(CameraMode.PANORAMA),
                        "These modes need still capture support that this device does not report."
                    )
                )
            )
        )

        val intelligence = FeatureGroup(
            title = "On-device intelligence",
            features = listOf(
                FeatureInfo(
                    title = "Face alignment guidance",
                    purpose = "Uses Google ML Kit face detection to tell you how to move so a face is centred, close enough and level.",
                    howToUse = "Double tap the Face button under the preview and choose Off, every 2, 3, 5 or 7 seconds. Guidance is spoken and shown as a pill over the preview.",
                    support = if (capabilities.supportsFaceDetection) mlKitSupport else FeatureSupport.Unsupported("ML Kit is unavailable.")
                ),
                FeatureInfo(
                    title = "Describe Scene",
                    purpose = "Names what the camera sees, using the on-device image labeling model.",
                    howToUse = "Double tap the Describe button under the preview and choose Off, every 2, 3, 5, 7, 10, 20 or 30 seconds. The newest description is spoken and pinned over the preview.",
                    support = if (capabilities.supportsSceneDescription) mlKitSupport else FeatureSupport.Unsupported("Scene description is unavailable.")
                ),
                FeatureInfo(
                    title = "Barcode and QR reading",
                    purpose = "Reads a barcode value aloud, either on demand or as part of a scene description.",
                    howToUse = "Double tap Scan barcode in the control bar, or keep barcode reading enabled in Settings so descriptions include codes in view.",
                    support = if (capabilities.supportsBarcodeScanning) mlKitSupport else FeatureSupport.Unsupported("Barcode scanning is unavailable.")
                ),
                FeatureInfo(
                    title = "Voice commands",
                    purpose = "Runs actions such as take photo, start video or switch camera by speaking.",
                    howToUse = "Turn on Enable voice commands in Settings, then double tap the Voice button in the camera control bar to start listening.",
                    support = if (capabilities.supportsVoiceRecognition) {
                        FeatureSupport.Supported
                    } else {
                        FeatureSupport.Unsupported("No speech recognition service was found.")
                    }
                )
            )
        )

        val saving = FeatureGroup(
            title = "Saving and privacy",
            features = listOf(
                FeatureInfo(
                    title = "System shutter sound",
                    purpose = "Plays the same shutter and recording sounds as the system camera.",
                    howToUse = "On by default. Turn Shutter sound off in Settings, Camera if you need silence.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Location tagging",
                    purpose = "Stores where a photo was taken.",
                    howToUse = "Turn on Location tagging in Settings, Saving and allow location access.",
                    support = if (capabilities.supportsLocationTagging) {
                        FeatureSupport.Supported
                    } else {
                        FeatureSupport.Unsupported("This device reports no location hardware.")
                    }
                ),
                FeatureInfo(
                    title = "Watermark",
                    purpose = "Stamps high contrast text onto saved photos.",
                    howToUse = "Turn on Watermark in Settings, Saving, then type the text you want.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Save location and review",
                    purpose = "Chooses the folder used for captures and shows a review sheet after each one.",
                    howToUse = "Settings, Saving changes the folder. Review after capture lives in Settings, Camera.",
                    support = FeatureSupport.Supported
                )
            )
        )

        val gallery = FeatureGroup(
            title = "Gallery",
            features = listOf(
                FeatureInfo(
                    title = "Browse, search, sort and filter",
                    purpose = "Finds photos and videos by name, album, type, date, size or favorites.",
                    howToUse = "Open the Gallery tab, then double tap Filter for search and filters.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Albums",
                    purpose = "Groups media by the folders on the device.",
                    howToUse = "Double tap Albums in the gallery header.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Trash button and Recently deleted",
                    purpose = "Shows items you deleted so you can restore them.",
                    howToUse = "Double tap the Trash button in the gallery header. It opens Recently deleted directly, with the count announced.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Delete permanently",
                    purpose = "Removes items from the trash for good.",
                    howToUse = "Inside Recently deleted, select items and double tap Delete permanently. A dialog names how many items will go and warns that it cannot be undone.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Favorites",
                    purpose = "Marks items you want to find quickly.",
                    howToUse = "Select an item and double tap the star, or open the item and use the star in the viewer toolbar.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Item details and camera metadata",
                    purpose = "Reads out size, resolution, path, album and EXIF camera data.",
                    howToUse = "Open an item and double tap Item details.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Share",
                    purpose = "Sends one or many items through another app.",
                    howToUse = "Select items and double tap Share selected items, or use Share in the viewer.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Edit a copy",
                    purpose = "Crops, rotates and adjusts a photo while leaving the original untouched.",
                    howToUse = "Open an item and double tap Edit this item.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Slideshow and cast",
                    purpose = "Plays items in sequence, optionally on a nearby screen.",
                    howToUse = "Start a slideshow from the gallery. A cast button appears only when a playback route is nearby.",
                    support = FeatureSupport.Supported
                )
            )
        )

        val accessibility = FeatureGroup(
            title = "Accessibility",
            features = listOf(
                FeatureInfo(
                    title = "TalkBack ready controls",
                    purpose = "Every control has a label, a role and a state description.",
                    howToUse = "Turn on TalkBack in Android accessibility settings and swipe through the app as usual.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Voice guidance, haptics and audio cues",
                    purpose = "Speaks state changes and pairs actions with distinct sounds and vibrations.",
                    howToUse = "Adjust them independently in Settings, Voice guidance and Settings, Feedback.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "High contrast and large text",
                    purpose = "Improves readability for low vision.",
                    howToUse = "Settings, Appearance offers high contrast light and dark themes plus text sizes up to huge.",
                    support = FeatureSupport.Supported
                ),
                FeatureInfo(
                    title = "Volume key shutter",
                    purpose = "Takes a photo without finding the shutter on screen.",
                    howToUse = "Keep Volume key shutter on in Settings, Camera, then press a volume key while the Photo tab is open.",
                    support = FeatureSupport.Supported
                )
            )
        )

        return listOf(capture, proCapture, video, intelligence, saving, gallery, accessibility)
    }
}
