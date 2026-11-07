/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatting helpers for dates, sizes and spoken descriptions of media.
 * All formatters use the device locale so spoken output matches the user's language.
 */
object Formatters {

    fun date(millis: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis))

    fun dateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(millis))

    fun time(millis: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(millis))

    /** Human readable byte size, for example "2.4 megabytes". */
    fun size(bytes: Long): String {
        if (bytes <= 0L) return "0 bytes"
        val units = arrayOf("bytes", "kilobytes", "megabytes", "gigabytes", "terabytes")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) {
            TimeFormat.plural(bytes, "byte")
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    fun dimensions(width: Int, height: Int): String =
        if (width <= 0 || height <= 0) "unknown size" else "$width by $height pixels"

    fun megapixels(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val mp = (width.toLong() * height.toLong()).toDouble() / 1_000_000.0
        return String.format(Locale.US, "%.1f megapixels", mp)
    }

    fun percent(value: Float): String = "${(value * 100).toInt()} percent"
}

/** Convenience access to locale-aware formatters inside composables. */
@Composable
@ReadOnlyComposable
fun rememberLocale(): Locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
