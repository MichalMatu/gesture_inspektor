package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.examples.gesturerecognizer.control.GestureAction
import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureInspectorSnapshotTest {
    @Test
    fun derivesStatusFromReliabilityConfidenceAndStability() {
        assertEquals(GestureInspectorStatus.NoHand, snapshot().status)
        assertEquals(
            GestureInspectorStatus.LandmarksUnavailable,
            snapshot(interaction(isReliable = false)).status
        )
        assertEquals(
            GestureInspectorStatus.LowConfidence,
            snapshot(interaction(score = 0.40f, stableFrames = 0)).status
        )
        assertEquals(GestureInspectorStatus.Tracking, snapshot(interaction(stableFrames = 2)).status)
        assertEquals(GestureInspectorStatus.Stable, snapshot(interaction(stableFrames = 3)).status)
    }

    @Test
    fun rejectsInteractionsThatDoNotBelongToFrame() {
        val interaction = interaction(detectionIndex = 1)

        assertThrows(IllegalArgumentException::class.java) {
            GestureInspectorSnapshot(
                activePresetName = "Test",
                frameSet = GestureFrameSet(100L, listOf(hand(detectionIndex = 0))),
                interactions = listOf(interaction),
                actionEvents = emptyList(),
                lastAction = null
            )
        }
    }

    @Test
    fun rejectsInteractionWithStaleTimestampOrDifferentFrameObservation() {
        val currentFrame = GestureFrameSet(100L, listOf(hand(score = 0.90f, detectionIndex = 0)))

        assertThrows(IllegalArgumentException::class.java) {
            GestureInspectorSnapshot(
                activePresetName = "Test",
                frameSet = currentFrame,
                interactions = listOf(interaction(timestampMs = 99L)),
                actionEvents = emptyList(),
                lastAction = null
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureInspectorSnapshot(
                activePresetName = "Test",
                frameSet = currentFrame,
                interactions = listOf(interaction(score = 0.80f)),
                actionEvents = emptyList(),
                lastAction = null
            )
        }
    }

    @Test
    fun rejectsActionEventFromDifferentInteraction() {
        val currentInteraction = interaction()
        val foreignInteraction = interaction(score = 0.80f)

        assertThrows(IllegalArgumentException::class.java) {
            GestureInspectorSnapshot(
                activePresetName = "Test",
                frameSet = GestureFrameSet(100L, listOf(currentInteraction.frame)),
                interactions = listOf(currentInteraction),
                actionEvents = listOf(
                    GestureActionEvent(
                        action = GestureAction("action.test", "Test"),
                        bindingId = "test",
                        interaction = foreignInteraction
                    )
                ),
                lastAction = null
            )
        }
    }

    private fun snapshot(interaction: GestureInteraction? = null): GestureInspectorSnapshot = GestureInspectorSnapshot(
        activePresetName = "Test",
        frameSet = GestureFrameSet(100L, interaction?.let { listOf(it.frame) }.orEmpty()),
        interactions = interaction?.let(::listOf).orEmpty(),
        actionEvents = emptyList(),
        lastAction = null
    )

    private fun interaction(
        score: Float = 0.90f,
        stableFrames: Int = 1,
        isReliable: Boolean = true,
        detectionIndex: Int = 0,
        timestampMs: Long = 100L
    ): GestureInteraction = GestureInteraction(
        trackingId = 0,
        frame = hand(score, detectionIndex),
        timestampMs = timestampMs,
        stableFrames = stableFrames,
        holdDurationMs = 0L,
        rawCenterX = 0.50f,
        rawCenterY = 0.50f,
        smoothedCenterX = 0.50f,
        smoothedCenterY = 0.50f,
        deltaX = 0f,
        deltaY = 0f,
        horizontalZone = HorizontalZone.Center,
        verticalZone = VerticalZone.Middle,
        movementDirection = MovementDirection.Still,
        hasMovedDuringHold = false,
        lostLandmarkFrames = 0,
        isTrackingReliable = isReliable
    )

    private fun hand(score: Float = 0.90f, detectionIndex: Int): HandGestureFrame = HandGestureFrame(
        detectionIndex = detectionIndex,
        handedness = "Right",
        handednessScore = 0.90f,
        candidates = listOf(GestureCandidate("Open_Palm", score, 1)),
        centerX = 0.50f,
        centerY = 0.50f,
        landmarkCount = 21
    )
}
