package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureCandidate
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.HandGestureFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureControllerTest {
    @Test
    fun duplicateFrameIsDroppedWithoutMutatingState() {
        val controller = controller()

        controller.handle(frame(0L))
        controller.handle(frame(33L))
        val accepted = controller.handle(frame(66L))
        val duplicate = controller.handle(frame(66L, gestureName = "Closed_Fist"))

        assertEquals(1, accepted.actionEvents.size)
        assertNotSame(accepted, duplicate)
        assertTrue(duplicate.actionEvents.isEmpty())
        assertEquals(accepted.lastAction, duplicate.lastAction)
        assertEquals(accepted.frameSet, duplicate.frameSet)
    }

    @Test
    fun longFrameGapRestartsPipelineAndClearsLastAction() {
        val controller = controller(maxFrameGapMs = 100L)

        controller.handle(frame(0L))
        controller.handle(frame(33L))
        assertEquals(1, controller.handle(frame(66L)).actionEvents.size)

        val afterGap = controller.handle(frame(500L))

        assertEquals(1, afterGap.interactions.single().stableFrames)
        assertTrue(afterGap.actionEvents.isEmpty())
        assertNull(afterGap.lastAction)
    }

    @Test
    fun unreliableLandmarksBetweenValidFramesRestartHold() {
        val controller = controller()

        controller.handle(frame(0L))
        controller.handle(frame(33L))
        val unreliable = controller.handle(frame(66L, landmarkCount = 0))
        val returned = controller.handle(frame(99L))

        assertEquals(0, unreliable.interactions.single().stableFrames)
        assertTrue(unreliable.actionEvents.isEmpty())
        assertEquals(1, returned.interactions.single().stableFrames)
        assertEquals(0L, returned.interactions.single().holdDurationMs)
        assertTrue(returned.actionEvents.isEmpty())
    }

    @Test
    fun acceptedFrameDoesNotRetainMutableInputCollections() {
        val controller = controller()
        val candidates = mutableListOf(GestureCandidate("Open_Palm", 0.90f, 1))
        val hands = mutableListOf(
            HandGestureFrame(
                detectionIndex = 0,
                handedness = "Right",
                handednessScore = 0.95f,
                candidates = candidates,
                centerX = 0.50f,
                centerY = 0.50f,
                landmarkCount = 21
            )
        )
        val snapshot = controller.handle(GestureFrameSet(0L, hands))

        candidates.clear()
        hands.clear()

        assertEquals("Open_Palm", snapshot.frameSet.hands.single().name)
        assertEquals(1, snapshot.frameSet.hands.single().candidates.size)
    }

    private fun controller(maxFrameGapMs: Long = 500L): GestureController = GestureController(
        preset = GesturePreset(
            id = "test",
            name = "Test",
            bindings = listOf(
                GestureBinding(
                    id = "open-palm",
                    gestureName = "Open_Palm",
                    action = GestureAction("action.open-palm", "Open palm"),
                    triggerMode = GestureTriggerMode.OncePerHold
                )
            )
        ),
        maxFrameGapMs = maxFrameGapMs
    )

    private fun frame(timestampMs: Long, gestureName: String = "Open_Palm", landmarkCount: Int = 21): GestureFrameSet = GestureFrameSet(
        timestampMs = timestampMs,
        hands = listOf(
            HandGestureFrame(
                detectionIndex = 0,
                handedness = "Right",
                handednessScore = 0.95f,
                candidates = listOf(GestureCandidate(gestureName, 0.90f, 1)),
                centerX = 0.50f,
                centerY = 0.50f,
                landmarkCount = landmarkCount
            )
        )
    )
}
