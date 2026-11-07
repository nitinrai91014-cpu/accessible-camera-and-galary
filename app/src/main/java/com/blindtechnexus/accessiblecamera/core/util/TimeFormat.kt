/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formats durations and elapsed times for both display and speech. */
object TimeFormat {

    /** Compact clock such as 0:07 or 12:34 for on-screen timers. */
    fun clock(millis: Long): String {
        val safe = if (millis < 0) 0 else millis
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safe)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Spoken duration, for example "1 minute 5 seconds". */
    fun spoken(millis: Long): String {
        val safe = if (millis < 0) 0 else millis
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safe)
        if (totalSeconds < 1L) return "less than a second"
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val parts = mutableListOf<String>()
        if (hours > 0L) parts += plural(hours, "hour")
        if (minutes > 0L) parts += plural(minutes, "minute")
        if (seconds > 0L && hours == 0L) parts += plural(seconds, "second")
        return parts.joinToString(" ")
    }

    /** Short duration for lists, for example "1:05". */
    fun shortDuration(millis: Long): String = clock(millis)

    fun plural(count: Long, singular: String, pluralForm: String = singular + "s"): String =
        if (count == 1L) "$count $singular" else "$count $pluralForm"

    fun eraTimeToMillis(seconds: Long): Long = TimeUnit.SECONDS.toMillis(seconds)
}
