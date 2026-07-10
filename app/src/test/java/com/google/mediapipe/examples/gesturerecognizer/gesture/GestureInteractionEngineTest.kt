package com.google.mediapipe.examples.gesturerecognizer.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInteractionEngineTest {
    @Test
    fun zeroDeadZoneKeepsIdenticalCentersStill() {
        val engine = GestureInteractionEngine(movementDeadZone = 0f, smoothingAlpha = 1f)

        engine.update(trackingId = 0, frame = hand("Open_Palm", centerX = 0.5f, centerY = 0.5f), timestampMs = 0L)
        val unchanged = engine.update(
            trackingId = 0,
            frame = hand("Open_Palm", centerX = 0.5f, centerY = 0.5f),
            timestampMs = 33L
        )

        assertEquals(MovementDirection.Still, unchanged.movementDirection)
        assertFalse(unchanged.hasMovedDuringHold)
    }

    @Test
    fun changingTrackingIdRestartsHold() {
        val engine = GestureInteractionEngine()

        engine.update(trackingId = 1, frame = hand("Open_Palm"), timestampMs = 0L)
        val differentTrack = engine.update(trackingId = 2, frame = hand("Open_Palm"), timestampMs = 33L)

        assertEquals(1, differentTrack.stableFrames)
        assertEquals(0L, differentTrack.holdDurationMs)
    }

    @Test
    fun tracksStableFramesAndHoldDuration() {
        val engine = GestureInteractionEngine()

        engine.update(TRACKING_ID, hand("Open_Palm"), 0L)
        engine.update(TRACKING_ID, hand("Open_Palm"), 100L)
        val interaction = engine.update(TRACKING_ID, hand("Open_Palm"), 200L)

        assertEquals(3, interaction.stableFrames)
        assertEquals(200L, interaction.holdDurationMs)
    }

    @Test
    fun usesSmoothedCenterForZonesAndMovement() {
        val engine = GestureInteractionEngine()

        engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.30f, centerY = 0.50f), 0L)
        val interaction = engine.update(
            TRACKING_ID,
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
            val interaction = engine.update(TRACKING_ID, frame, index * 33L)
            assertEquals(MovementDirection.Still, interaction.movementDirection)
            assertFalse(interaction.hasMovedDuringHold)
        }
    }

    @Test
    fun missingLandmarksDoNotCreateCatchUpMovement() {
        val engine = GestureInteractionEngine()

        engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.20f, centerY = 0.50f), 0L)
        val missingLandmarks = engine.update(
            TRACKING_ID,
            hand("Open_Palm", centerX = null, centerY = null, landmarkCount = 0),
            33L
        )
        val landmarksReturned = engine.update(
            TRACKING_ID,
            hand("Open_Palm", centerX = 0.90f, centerY = 0.50f),
            66L
        )

        assertEquals(MovementDirection.Still, missingLandmarks.movementDirection)
        assertEquals(1, missingLandmarks.lostLandmarkFrames)
        assertEquals(MovementDirection.Still, landmarksReturned.movementDirection)
        assertEquals(1, landmarksReturned.stableFrames)
        assertEquals(0L, landmarksReturned.holdDurationMs)
        assertFalse(landmarksReturned.hasMovedDuringHold)
    }

    @Test
    fun gradualDriftEventuallyCountsAsMovement() {
        val engine = GestureInteractionEngine()

        engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.50f), 0L)
        engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.54f), 33L)
        engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.58f), 66L)
        val interaction = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.62f), 99L)

        assertEquals(MovementDirection.Right, interaction.movementDirection)
        assertTrue(interaction.hasMovedDuringHold)
    }

    @Test
    fun duplicateTimestampAndLongGapRestartHold() {
        val engine = GestureInteractionEngine(maxFrameGapMs = 200L)

        engine.update(TRACKING_ID, hand("Open_Palm"), 100L)
        val duplicate = engine.update(TRACKING_ID, hand("Open_Palm"), 100L)
        val continued = engine.update(TRACKING_ID, hand("Open_Palm"), 150L)
        val afterGap = engine.update(TRACKING_ID, hand("Open_Palm"), 400L)

        assertEquals(1, duplicate.stableFrames)
        assertEquals(2, continued.stableFrames)
        assertEquals(1, afterGap.stableFrames)
        assertEquals(0L, afterGap.holdDurationMs)
    }

    @Test
    fun zoneHysteresisPreventsBoundaryOscillation() {
        val engine = GestureInteractionEngine(smoothingAlpha = 1f, zoneHysteresis = 0.02f)

        val left = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.32f), 0L)
        val nearBoundary = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.34f), 33L)
        val center = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.36f), 66L)
        val nearBoundaryAgain = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.32f), 99L)
        val leftAgain = engine.update(TRACKING_ID, hand("Open_Palm", centerX = 0.30f), 132L)

        assertEquals(HorizontalZone.Left, left.horizontalZone)
        assertEquals(HorizontalZone.Left, nearBoundary.horizontalZone)
        assertEquals(HorizontalZone.Center, center.horizontalZone)
        assertEquals(HorizontalZone.Center, nearBoundaryAgain.horizontalZone)
        assertEquals(HorizontalZone.Left, leftAgain.horizontalZone)
    }

    @Test
    fun lowConfidenceResetsHoldState() {
        val engine = GestureInteractionEngine()

        engine.update(TRACKING_ID, hand("Open_Palm", score = 0.90f), 0L)
        engine.update(TRACKING_ID, hand("Open_Palm", score = 0.90f), 100L)
        val lowConfidence = engine.update(TRACKING_ID, hand("Open_Palm", score = 0.20f), 200L)
        val restarted = engine.update(TRACKING_ID, hand("Open_Palm", score = 0.90f), 300L)

        assertEquals(0, lowConfidence.stableFrames)
        assertEquals(0L, lowConfidence.holdDurationMs)
        assertTrue(lowConfidence.isTrackingReliable)
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
        detectionIndex = 0,
        handedness = "Right",
        handednessScore = 0.90f,
        candidates = listOf(GestureCandidate(name, score, 1)),
        centerX = centerX,
        centerY = centerY,
        landmarkCount = landmarkCount
    )

    private companion object {
        const val TRACKING_ID = 7
    }
}
