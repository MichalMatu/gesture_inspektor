package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.examples.gesturerecognizer.control.GestureAction
import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInspectorLogReducerTest {
    @Test
    fun logsInitialStateThenSuppressesUntilHeartbeat() {
        val reducer = GestureInspectorLogReducer(heartbeatIntervalMs = 2_000L)
        val snapshot = snapshot()

        val first = reducer.reduce(snapshot, inferenceTimeMs = 10L, nowMs = 0L, verbose = false)
        val second = reducer.reduce(snapshot, inferenceTimeMs = 11L, nowMs = 500L, verbose = false)
        val heartbeat = reducer.reduce(snapshot, inferenceTimeMs = 12L, nowMs = 2_000L, verbose = false)

        assertEquals(listOf("STATE"), first.map { it.message.substringBefore(' ') })
        assertTrue(second.isEmpty())
        assertEquals(listOf("HEARTBEAT"), heartbeat.map { it.message.substringBefore(' ') })
    }

    @Test
    fun logsActionEventsImmediately() {
        val reducer = GestureInspectorLogReducer()
        val interaction = interaction()
        val event = GestureActionEvent(
            action = GestureAction(
                id = "action.open_palm_still",
                label = "Open palm still"
            ),
            bindingId = "open-palm-still",
            interaction = interaction
        )

        val lines = reducer.reduce(
            snapshot = snapshot(interaction = interaction, events = listOf(event), lastAction = event),
            inferenceTimeMs = 10L,
            nowMs = 0L,
            verbose = false
        )

        assertTrue(lines.any { line -> line.message.startsWith("ACTION") })
        assertTrue(lines.any { line -> line.message.contains("binding=open-palm-still") })
    }

    @Test
    fun verboseDiagnosticsAreOptIn() {
        val reducer = GestureInspectorLogReducer(verboseIntervalMs = 1_000L)
        val snapshot = snapshot()

        val defaultLines = reducer.reduce(snapshot, inferenceTimeMs = 10L, nowMs = 0L, verbose = false)
        val verboseLines = reducer.reduce(snapshot, inferenceTimeMs = 11L, nowMs = 1_000L, verbose = true)

        assertFalse(defaultLines.any { line -> line.level == GestureInspectorLogLevel.Verbose })
        assertTrue(verboseLines.any { line -> line.level == GestureInspectorLogLevel.Verbose })
        assertTrue(verboseLines.any { line -> line.message.contains("Top [1. Open Palm 90%") })
    }

    @Test
    fun logsMeaningfulStateChanges() {
        val reducer = GestureInspectorLogReducer()

        reducer.reduce(snapshot(), inferenceTimeMs = 10L, nowMs = 0L, verbose = false)
        val changed = reducer.reduce(
            snapshot = snapshot(
                interaction = interaction(
                    frame = HandGestureFrame(
                        handIndex = 0,
                        handedness = "Right",
                        handednessScore = 0.94f,
                        candidates = listOf(GestureCandidate("Closed_Fist", 0.88f, 1)),
                        centerX = 0.50f,
                        centerY = 0.50f,
                        landmarkCount = 21
                    )
                )
            ),
            inferenceTimeMs = 11L,
            nowMs = 100L,
            verbose = false
        )

        assertEquals(listOf("STATE"), changed.map { it.message.substringBefore(' ') })
        assertTrue(changed.first().message.contains("gesture=Closed Fist 88%"))
    }

    private fun snapshot(
        interaction: GestureInteraction = interaction(),
        events: List<GestureActionEvent> = emptyList(),
        lastAction: GestureActionEvent? = null
    ): GestureInspectorSnapshot = GestureInspectorSnapshot(
        activePresetName = "Inspector Demo",
        frameSet = GestureFrameSet(
            timestampMs = interaction.timestampMs,
            hands = listOf(interaction.frame)
        ),
        interactions = listOf(interaction),
        actionEvents = events,
        lastAction = lastAction
    )

    private fun interaction(
        frame: HandGestureFrame = HandGestureFrame(
            handIndex = 0,
            handedness = "Right",
            handednessScore = 0.94f,
            candidates = listOf(
                GestureCandidate("Open_Palm", 0.90f, 1),
                GestureCandidate("Victory", 0.07f, 2),
                GestureCandidate("Closed_Fist", 0.03f, 3)
            ),
            centerX = 0.50f,
            centerY = 0.50f,
            landmarkCount = 21
        )
    ): GestureInteraction = GestureInteraction(
        frame = frame,
        timestampMs = 100L,
        stableFrames = 3,
        holdDurationMs = 200L,
        rawCenterX = frame.centerX,
        rawCenterY = frame.centerY,
        smoothedCenterX = frame.centerX,
        smoothedCenterY = frame.centerY,
        deltaX = 0f,
        deltaY = 0f,
        horizontalZone = HorizontalZone.Center,
        verticalZone = VerticalZone.Middle,
        movementDirection = MovementDirection.Still,
        hasMovedDuringHold = false,
        lostLandmarkFrames = 0,
        isTrackingReliable = true
    )
}
