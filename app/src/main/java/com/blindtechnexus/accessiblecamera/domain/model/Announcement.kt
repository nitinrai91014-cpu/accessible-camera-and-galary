/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.domain.model

/**
 * Priority of a spoken message. CRITICAL messages are never interrupted and are spoken
 * again if another message is dropped so the user cannot miss an error.
 */
enum class AnnouncementPriority(val level: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3)
}

/** A request for the TextToSpeech engine. */
data class Announcement(
    val text: String,
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
    /** Suppress when a screen reader is already announcing the same content. */
    val skipWhenTalkBackActive: Boolean = false,
    /** Rapidly changing values debounce under this key; null means never debounce. */
    val debounceKey: String? = null,
    /** Also deliver through the accessibility live region. */
    val liveRegion: Boolean = false
)

/** State of the on-device speech recogniser. */
data class SpeechRecognitionState(
    val isListening: Boolean = false,
    val isAvailable: Boolean = false,
    val lastUtterance: String? = null,
    val lastCommand: VoiceCommand? = null,
    val error: String? = null,
    val isAwaitingConfirmation: Boolean = false
)
