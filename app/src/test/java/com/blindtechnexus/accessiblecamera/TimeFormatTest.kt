/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import com.blindtechnexus.accessiblecamera.core.util.Formatters
import com.blindtechnexus.accessiblecamera.core.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spoken durations and sizes must read naturally, because that is how users hear them. */
class TimeFormatTest {

    @Test
    fun `clock format handles seconds minutes and hours`() {
        assertEquals("0:07", TimeFormat.clock(7_000))
        assertEquals("1:05", TimeFormat.clock(65_000))
        assertEquals("1:01:01", TimeFormat.clock(3_661_000))
    }

    @Test
    fun `negative clock values clamp to zero`() {
        assertEquals("0:00", TimeFormat.clock(-5_000))
    }

    @Test
    fun `spoken durations use words`() {
        assertEquals("less than a second", TimeFormat.spoken(200))
        assertEquals("1 minute 5 seconds", TimeFormat.spoken(65_000))
        assertTrue(TimeFormat.spoken(3_600_000).contains("hour"))
    }

    @Test
    fun `plural helper handles singular and plural`() {
        assertEquals("1 second", TimeFormat.plural(1, "second"))
        assertEquals("2 seconds", TimeFormat.plural(2, "second"))
    }

    @Test
    fun `size formatting is human readable`() {
        assertEquals("0 bytes", Formatters.size(0))
        assertTrue(Formatters.size(1024).startsWith("1.0 kilobytes"))
        assertTrue(Formatters.size(5L * 1024 * 1024).contains("megabytes"))
    }

    @Test
    fun `dimensions are spoken as words not symbols`() {
        assertEquals("1080 by 1920 pixels", Formatters.dimensions(1080, 1920))
        assertEquals("unknown size", Formatters.dimensions(0, 0))
    }
}
