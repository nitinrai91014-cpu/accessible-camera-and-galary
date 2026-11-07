/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera

import com.blindtechnexus.accessiblecamera.data.camera.FaceAlignmentEvaluator
import com.blindtechnexus.accessiblecamera.data.camera.FaceObservation
import com.blindtechnexus.accessiblecamera.domain.model.FaceGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Face alignment thresholds drive spoken guidance, so the boundaries are verified here on a
 * plain JVM without ML Kit or a device.
 */
class FaceAlignmentEvaluatorTest {

    private fun face(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        eulerY: Float = 0f,
        eulerZ: Float = 0f
    ) = FaceObservation(left, top, right, bottom, eulerY, eulerZ)

    private fun message(result: com.blindtechnexus.accessiblecamera.data.camera.FaceFrameResult): String =
        when (val guidance = result.guidance) {
            is FaceGuidance.Aligned -> guidance.message
            is FaceGuidance.Misaligned -> guidance.message
            is FaceGuidance.NoFace -> guidance.hint
            is FaceGuidance.MultipleFaces -> "multiple"
            else -> ""
        }

    @Test
    fun `no faces produces no face guidance`() {
        val result = FaceAlignmentEvaluator.evaluate(emptyList(), 1080, 1920)
        assertTrue(result.guidance is FaceGuidance.NoFace)
    }

    @Test
    fun `invalid frame size is reported clearly`() {
        val result = FaceAlignmentEvaluator.evaluate(emptyList(), 0, 0)
        assertEquals("Camera frame is not ready.", message(result))
    }

    @Test
    fun `centred large face is aligned`() {
        // Half the frame height, dead centre: comfortably inside every threshold.
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(300, 480, 780, 1440)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(result.guidance is FaceGuidance.Aligned)
    }

    @Test
    fun `face far right asks the user to move left`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(880, 700, 1060, 1140)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(result.guidance is FaceGuidance.Misaligned)
        assertTrue(message(result).contains("left", ignoreCase = true))
    }

    @Test
    fun `small face asks the user to move closer`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(520, 900, 600, 1000)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(message(result).contains("closer", ignoreCase = true))
    }

    @Test
    fun `turned head asks the user to turn back`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(300, 480, 780, 1440, eulerY = 30f)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(message(result).contains("Turn your head", ignoreCase = true))
    }

    @Test
    fun `tilted head asks the user to straighten up`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(300, 480, 780, 1440, eulerZ = 25f)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(message(result).contains("Straighten", ignoreCase = true))
    }

    @Test
    fun `two faces are reported as multiple`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(
                face(300, 480, 780, 1440),
                face(760, 480, 1040, 1440)
            ),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(result.guidance is FaceGuidance.MultipleFaces)
    }

    @Test
    fun `face near the bottom asks the user to move up`() {
        val result = FaceAlignmentEvaluator.evaluate(
            listOf(face(400, 1500, 680, 1900)),
            frameWidth = 1080,
            frameHeight = 1920
        )
        assertTrue(message(result).contains("up", ignoreCase = true))
    }
}
