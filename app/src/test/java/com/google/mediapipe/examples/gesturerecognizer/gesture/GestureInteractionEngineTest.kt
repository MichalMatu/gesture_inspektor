package com.google.mediapipe.examples.gesturerecognizer.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInteractionEngineTest {
    @Test
    fun tracksStableFramesAndHoldDuration() {
        val engine = GestureInteractionEngine()

        engine.update(hand("Open_Palm"), 0L)
        engine.update(hand("Open_Palm"), 100L)
        val interaction = engine.update(hand("Open_Palm"), 200L)

        assertEquals(3, interaction.stableFrames)
        assertEquals(200L, interaction.holdDurationMs)
        assertTrue(interaction.isStable)
    }

    @Test
    fun usesSmoothedCenterForZonesAndMovement() {
        val engine = GestureInteractionEngine()

        engine.update(hand("Open_Palm", centerX = 0.30f, centerY = 0.50f), 0L)
        val interaction = engine.update(
            hand("Open_Palm", centerX = 0.90f, centerY = 0.50f),
            100L
        )

        assertEquals(0.90f, interaction.rawCenterX ?: 0f, 0.001f)
        assertEquals(0.45f, interaction.smoothedCenterX ?: 0f, 0.001f)
        assertEquals(HorizontalZone.Center, interaction.horizontalZone)
        assertEquals(VerticalZone.Middle, interaction.verticalZone)
        assertEquals(MovementDirection.Right, interaction.movementDirection)
        assertTrue(interaction.hasMovedDuringHold)
    }

    @Test
    fun keepsSmallJitterStill() {
        val engine = GestureInteractionEngine()
        val frames = listOf(
            hand("Open_Palm", centerX = 0.50f),
            hand("Open_Palm", centerX = 0.52f),
            hand("Open_Palm", centerX = 0.48f),
            hand("Open_Palm", centerX = 0.51f),
            hand("Open_Palm", centerX = 0.49f)
        )

        frames.forEachIndexed { index, frame ->
            val interaction = engine.update(frame, index * 33L)
            assertEquals(MovementDirection.Still, interaction.movementDirection)
            assertFalse(interaction.hasMovedDuringHold)
        }
    }

    @Test
    fun missingLandmarksDoNotCreateCatchUpMovement() {
        val engine = GestureInteractionEngine()

        engine.update(hand("Open_Palm", centerX = 0.20f, centerY = 0.50f), 0L)
        val missingLandmarks = engine.update(
            hand("Open_Palm", centerX = null, centerY = null, landmarkCount = 0),
            33L
        )
        val landmarksReturned = engine.update(
            hand("Open_Palm", centerX = 0.90f, centerY = 0.50f),
            66L
        )

        assertEquals(MovementDirection.Still, missingLandmarks.movementDirection)
        assertEquals(1, missingLandmarks.lostLandmarkFrames)
        assertEquals(MovementDirection.Still, landmarksReturned.movementDirection)
        assertFalse(landmarksReturned.hasMovedDuringHold)
    }

    @Test
    fun lowConfidenceResetsHoldState() {
        val engine = GestureInteractionEngine()

        engine.update(hand("Open_Palm", score = 0.90f), 0L)
        engine.update(hand("Open_Palm", score = 0.90f), 100L)
        val lowConfidence = engine.update(hand("Open_Palm", score = 0.20f), 200L)
        val restarted = engine.update(hand("Open_Palm", score = 0.90f), 300L)

        assertEquals(0, lowConfidence.stableFrames)
        assertEquals(0L, lowConfidence.holdDurationMs)
        assertEquals(1, restarted.stableFrames)
        assertEquals(0L, restarted.holdDurationMs)
    }

    private fun hand(
        name: String,
        score: Float = 0.90f,
        centerX: Float? = 0.50f,
        centerY: Float? = 0.50f,
        landmarkCount: Int = if (centerX == null || centerY == null) 0 else 21
    ): HandGestureFrame = HandGestureFrame(
        handIndex = 0,
        handedness = "Right",
        handednessScore = 0.90f,
        candidates = listOf(GestureCandidate(name, score, 1)),
        centerX = centerX,
        centerY = centerY,
        landmarkCount = landmarkCount
    )
}
