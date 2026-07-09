package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent

data class GestureInspectorSnapshot(
    val activePresetName: String,
    val frameSet: GestureFrameSet,
    val interactions: List<GestureInteraction>,
    val actionEvents: List<GestureActionEvent>,
    val lastAction: GestureActionEvent?
) {
    val matchedAction: GestureActionEvent?
        get() = actionEvents.firstOrNull()
}
