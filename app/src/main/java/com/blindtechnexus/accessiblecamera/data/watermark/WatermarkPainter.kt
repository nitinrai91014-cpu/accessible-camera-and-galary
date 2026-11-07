/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.watermark

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draws an accessible watermark: a solid dark panel with white text placed in the bottom
 * left corner. High contrast avoids relying on colour and keeps the mark legible.
 */
class WatermarkPainter {

    suspend fun apply(
        bitmap: Bitmap,
        text: String,
        secondaryText: String? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext bitmap
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return@withContext bitmap
        val canvas = Canvas(mutable)
        val padding = (mutable.width * 0.022f).coerceAtLeast(12f)
        val primarySize = (mutable.width * 0.030f).coerceIn(18f, 96f)
        val secondarySize = primarySize * 0.72f

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = primarySize
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = secondarySize
            textAlign = Paint.Align.LEFT
        }
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 0, 0, 0) }

        val primaryBounds = Rect()
        primaryPaint.getTextBounds(text, 0, text.length, primaryBounds)
        val secondaryBounds = Rect()
        secondaryText?.let { secondaryPaint.getTextBounds(it, 0, it.length, secondaryBounds) }

        val panelWidth = maxOf(primaryBounds.width(), secondaryBounds.width()) + padding * 2
        val panelHeight = primaryBounds.height() + secondaryBounds.height() +
            padding * 2 + (if (secondaryText != null) padding * 0.4f else 0f)
        val left = padding
        val top = mutable.height - padding - panelHeight
        canvas.drawRoundRect(
            left,
            top,
            left + panelWidth,
            top + panelHeight,
            padding * 0.6f,
            padding * 0.6f,
            background
        )
        val textX = left + padding
        var textY = top + padding + primaryBounds.height()
        canvas.drawText(text, textX, textY, primaryPaint)
        secondaryText?.let {
            textY += secondaryBounds.height() + padding * 0.6f
            canvas.drawText(it, textX, textY, secondaryPaint)
        }
        mutable
    }
}
