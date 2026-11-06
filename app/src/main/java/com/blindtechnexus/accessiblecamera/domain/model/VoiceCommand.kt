/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/**
 * On-device voice commands. Recognition runs only after the user explicitly starts it,
 * so the microphone is never open in the background.
 */
enum class VoiceCommand(
    val id: String,
    val spokenConfirmation: String,
    val isDestructive: Boolean = false,
    val requiresConfirmation: Boolean = false
) {
    TAKE_PHOTO("take_photo", "Taking photo", requiresConfirmation = false),
    START_VIDEO("start_video", "Starting video"),
    STOP_VIDEO("stop_video", "Stopping recording"),
    PAUSE_VIDEO("pause_video", "Pausing recording"),
    RESUME_VIDEO("resume_video", "Resuming recording"),
    OPEN_GALLERY("open_gallery", "Opening gallery"),
    OPEN_PHOTO("open_photo", "Opening photo"),
    OPEN_SETTINGS("open_settings", "Opening settings"),
    NEXT("next", "Next item"),
    PREVIOUS("previous", "Previous item"),
    BACK("back", "Going back"),
    SELECT("select", "Selected"),
    DESELECT("deselect", "Deselected"),
    DELETE("delete", "Delete"),
    CONFIRM("confirm", "Confirmed"),
    CANCEL("cancel", "Cancelled"),
    ZOOM_IN("zoom_in", "Zooming in"),
    ZOOM_OUT("zoom_out", "Zooming out"),
    SWITCH_CAMERA("switch_camera", "Switching camera"),
    FLASH("flash", "Changing flash"),
    TIMER("timer", "Changing timer"),
    TAKE_PHOTO_CONFIRM("take_photo_confirm", "Photo confirmed"),
    PLAY_PAUSE("play_pause", "Play or pause"),
    SHARE("share", "Share"),
    HELP("help", "Listing commands"),
    STOP_LISTENING("stop_listening", "Voice commands stopped"),
    UNKNOWN("unknown", "Command not recognised");

    companion object {
        /** Phrase table. Matching is lenient and case insensitive. */
        private val phrases: List<Pair<List<String>, VoiceCommand>> = listOf(
            listOf("take photo", "take a photo", "capture", "shutter", "take picture", "take a picture", "photo") to TAKE_PHOTO,
            listOf("start video", "record", "start recording", "begin recording") to START_VIDEO,
            listOf("stop video", "stop recording", "stop") to STOP_VIDEO,
            listOf("pause video", "pause recording", "pause") to PAUSE_VIDEO,
            listOf("resume video", "resume recording", "resume", "continue") to RESUME_VIDEO,
            listOf("open gallery", "gallery", "show gallery") to OPEN_GALLERY,
            listOf("open photo", "camera", "open camera") to OPEN_PHOTO,
            listOf("open settings", "settings", "preferences") to OPEN_SETTINGS,
            listOf("next", "next item", "forward", "go forward") to NEXT,
            listOf("previous", "previous item", "back item", "go back one") to PREVIOUS,
            listOf("back", "go back", "return") to BACK,
            listOf("select", "choose", "pick") to SELECT,
            listOf("deselect", "unselect") to DESELECT,
            listOf("delete", "remove", "trash") to DELETE,
            listOf("confirm", "yes", "okay", "ok") to CONFIRM,
            listOf("cancel", "no", "never mind") to CANCEL,
            listOf("zoom in", "closer") to ZOOM_IN,
            listOf("zoom out", "farther", "further") to ZOOM_OUT,
            listOf("switch camera", "flip camera", "front camera", "back camera") to SWITCH_CAMERA,
            listOf("flash", "toggle flash") to FLASH,
            listOf("timer", "self timer") to TIMER,
            listOf("play", "pause playback", "play pause") to PLAY_PAUSE,
            listOf("share", "send") to SHARE,
            listOf("help", "what can I say", "commands") to HELP,
            listOf("stop listening", "stop voice commands", "microphone off") to STOP_LISTENING
        )

        /** Parses a recognised utterance into a command. */
        fun parse(utterance: String): VoiceCommand {
            val normalised = utterance.lowercase().trim().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ")
            if (normalised.isBlank()) return UNKNOWN
            // Prefer the longest matching phrase so "stop video" beats "stop".
            var best: Pair<String, VoiceCommand>? = null
            for ((words, command) in phrases) {
                for (phrase in words) {
                    if (normalised.contains(phrase)) {
                        if (best == null || phrase.length > best!!.first.length) best = phrase to command
                    }
                }
            }
            return best?.second ?: UNKNOWN
        }

        /** Spoken help text listing the supported commands. */
        fun helpText(): String = "You can say: take photo, start video, stop video, pause, " +
            "open gallery, open settings, next, previous, back, select, delete, confirm, cancel, " +
            "zoom in, zoom out, switch camera, flash, timer, share, or stop listening."
    }
}
