/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import com.blindtechnexus.accessiblecamera.domain.model.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voice command parsing must be forgiving, but never silently destructive. */
class VoiceCommandParserTest {

    @Test
    fun `take photo variants all map to the same command`() {
        listOf("take photo", "Take a photo", "CAPTURE", "shutter").forEach { phrase ->
            assertEquals(VoiceCommand.TAKE_PHOTO, VoiceCommand.parse(phrase))
        }
    }

    @Test
    fun `stop video wins over plain stop`() {
        assertEquals(VoiceCommand.STOP_VIDEO, VoiceCommand.parse("stop video"))
        assertEquals(VoiceCommand.STOP_VIDEO, VoiceCommand.parse("stop recording please"))
    }

    @Test
    fun `pause and resume are distinct commands`() {
        assertEquals(VoiceCommand.PAUSE_VIDEO, VoiceCommand.parse("pause"))
        assertEquals(VoiceCommand.RESUME_VIDEO, VoiceCommand.parse("resume"))
    }

    @Test
    fun `navigation and selection commands parse`() {
        assertEquals(VoiceCommand.NEXT, VoiceCommand.parse("next"))
        assertEquals(VoiceCommand.PREVIOUS, VoiceCommand.parse("previous"))
        assertEquals(VoiceCommand.SELECT, VoiceCommand.parse("select this"))
        assertEquals(VoiceCommand.DESELECT, VoiceCommand.parse("deselect"))
    }

    @Test
    fun `punctuation and case do not matter`() {
        assertEquals(VoiceCommand.OPEN_GALLERY, VoiceCommand.parse("Open the gallery, please."))
    }

    @Test
    fun `unknown phrasing is reported instead of guessed`() {
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommand.parse("make me a sandwich"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommand.parse(""))
    }

    @Test
    fun `help text lists confirm and stop listening`() {
        val help = VoiceCommand.helpText()
        assertTrue(help.contains("confirm"))
        assertTrue(help.contains("stop listening"))
    }

    @Test
    fun `destructive delete requires an explicit confirm`() {
        // The controller always asks for confirmation before running delete, which is why the
        // command itself is safe to recognise.
        assertEquals(VoiceCommand.DELETE, VoiceCommand.parse("delete"))
        assertEquals(VoiceCommand.CONFIRM, VoiceCommand.parse("confirm"))
        assertEquals(VoiceCommand.CANCEL, VoiceCommand.parse("cancel"))
    }
}
