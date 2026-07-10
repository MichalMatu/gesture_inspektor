package com.google.mediapipe.examples.gesturerecognizer.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiHandGestureInteractionEngineTest {
    @Test
    fun reorderedDetectionsKeepPhysicalTrackingIdsAndHolds() {
        val engine = MultiHandGestureInteractionEngine()

        engine.update(
            frameSet(
                0L,
                hand(0, "Left", 0.20f),
                hand(1, "Right", 0.80f)
            )
        )
        val reordered = engine.update(
            frameSet(
                33L,
                hand(0, "Right", 0.78f),
                hand(1, "Left", 0.22f)
            )
        )

        assertEquals(listOf(0, 1), reordered.map { interaction -> interaction.trackingId })
        assertEquals(listOf(1, 0), reordered.map { interaction -> interaction.frame.detectionIndex })
        assertEquals(listOf(2, 2), reordered.map { interaction -> interaction.stableFrames })
    }

    @Test
    fun matcherMaximizesAssignmentCountBeforeDistance() {
        val engine = MultiHandGestureInteractionEngine(maxMatchDistance = 0.35f)

        engine.update(
            frameSet(
                0L,
                hand(0, handedness = null, centerX = 0.30f),
                hand(1, handedness = null, centerX = 0.60f)
            )
        )
        val matched = engine.update(
            frameSet(
                33L,
                hand(0, handedness = null, centerX = 0.40f),
                hand(1, handedness = null, centerX = 0.10f)
            )
        )

        assertEquals(listOf(0, 1), matched.map { interaction -> interaction.trackingId })
        assertEquals(listOf(1, 0), matched.map { interaction -> interaction.frame.detectionIndex })
        assertEquals(listOf(2, 2), matched.map { interaction -> interaction.stableFrames })
    }

    @Test
    fun lowConfidenceHandednessFlipKeepsNearbyTrack() {
        val engine = MultiHandGestureInteractionEngine()

        engine.update(frameSet(0L, hand(0, "Right", 0.40f, handednessScore = 0.95f)))
        val flipped = engine.update(frameSet(33L, hand(0, "Left", 0.41f, handednessScore = 0.20f)))

        assertEquals(0, flipped.single().trackingId)
        assertEquals(2, flipped.single().stableFrames)
    }

    @Test
    fun missingFrameKeepsIdentityButRestartsHold() {
        val engine = MultiHandGestureInteractionEngine()

        engine.update(frameSet(0L, hand(0, "Right", 0.40f)))
        assertEquals(emptyList<GestureInteraction>(), engine.update(frameSet(33L)))
        val returned = engine.update(frameSet(66L, hand(0, "Right", 0.42f)))

        assertEquals(0, returned.single().trackingId)
        assertEquals(1, returned.single().stableFrames)
        assertEquals(0L, returned.single().holdDurationMs)
    }

    private fun frameSet(timestampMs: Long, vararg hands: HandGestureFrame): GestureFrameSet = GestureFrameSet(timestampMs, hands.toList())

    private fun hand(
        detectionIndex: Int,
        handedness: String?,
        centerX: Float,
        handednessScore: Float? = if (handedness == null) null else 0.95f
    ): HandGestureFrame = HandGestureFrame(
        detectionIndex = detectionIndex,
        handedness = handedness,
        handednessScore = handednessScore,
        candidates = listOf(GestureCandidate("Open_Palm", 0.90f, 1)),
        centerX = centerX,
        centerY = 0.50f,
        landmarkCount = 21
    )
}
