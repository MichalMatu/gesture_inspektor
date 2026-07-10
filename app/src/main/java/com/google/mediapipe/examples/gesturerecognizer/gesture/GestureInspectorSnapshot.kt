package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent

enum class GestureInspectorStatus(val label: String) {
    NoHand("no hand"),
    LandmarksUnavailable("landmarks unavailable"),
    LowConfidence("low confidence"),
    Stable("stable"),
    Tracking("tracking")
}

data class GestureInspectorSnapshot(
    val activePresetName: String,
    val frameSet: GestureFrameSet,
    val interactions: List<GestureInteraction>,
    val actionEvents: List<GestureActionEvent>,
    val lastAction: GestureActionEvent?
) {
    init {
        require(activePresetName.isNotBlank()) { "Active preset name must not be blank." }
        require(
            interactions.map { interaction -> interaction.frame.detectionIndex }.sorted() ==
                frameSet.hands.map { hand -> hand.detectionIndex }.sorted()
        ) {
            "Snapshot interactions must correspond to every hand in the frame."
        }
        val framesByDetectionIndex = frameSet.hands.associateBy { hand -> hand.detectionIndex }
        require(
            interactions.all { interaction ->
                interaction.timestampMs == frameSet.timestampMs &&
                    framesByDetectionIndex[interaction.frame.detectionIndex] == interaction.frame
            }
        ) {
            "Snapshot interactions must contain the current frame observations and timestamp."
        }
        require(interactions.map { interaction -> interaction.trackingId }.distinct().size == interactions.size) {
            "Snapshot tracking IDs must be unique."
        }
        require(actionEvents.all { event -> event.interaction in interactions }) {
            "Snapshot action events must contain a current interaction."
        }
    }

    val matchedAction: GestureActionEvent?
        get() = actionEvents.firstOrNull()

    val status: GestureInspectorStatus
        get() = when {
            frameSet.hands.isEmpty() -> GestureInspectorStatus.NoHand

            interactions.any { interaction -> !interaction.isTrackingReliable } -> GestureInspectorStatus.LandmarksUnavailable

            interactions.any { interaction ->
                interaction.score < MIN_DISPLAY_SCORE || interaction.gestureName == GestureFrameSet.NONE
            } -> GestureInspectorStatus.LowConfidence

            interactions.any { interaction -> interaction.stableFrames >= STABLE_DISPLAY_FRAMES } -> GestureInspectorStatus.Stable

            else -> GestureInspectorStatus.Tracking
        }

    private companion object {
        const val MIN_DISPLAY_SCORE = 0.60f
        const val STABLE_DISPLAY_FRAMES = 3
    }
}
