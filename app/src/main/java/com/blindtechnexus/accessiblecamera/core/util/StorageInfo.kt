/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.core.util

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/** Storage helpers used for low storage warnings and free space checks. */
object StorageInfo {

    /** Below this, the app warns that space is running out. */
    const val LOW_STORAGE_THRESHOLD_BYTES = 150L * 1024L * 1024L

    /** Below this, captures are refused so a file is never half written. */
    const val CRITICAL_BYTES = 30L * 1024L * 1024L

    data class Snapshot(val availableBytes: Long, val totalBytes: Long) {
        val isLow: Boolean get() = availableBytes in 0 until LOW_STORAGE_THRESHOLD_BYTES
        val isCritical: Boolean get() = availableBytes in 0 until CRITICAL_BYTES
    }

    fun snapshot(context: Context): Snapshot {
        return runCatching {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val dir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager?.primaryStorageVolume?.directory ?: context.filesDir
            } else {
                context.filesDir
            }
            val stats = StatFs(dir.absolutePath)
            Snapshot(
                availableBytes = stats.availableBytes,
                totalBytes = stats.totalBytes
            )
        }.getOrElse { Snapshot(Long.MAX_VALUE, Long.MAX_VALUE) }
    }
}
