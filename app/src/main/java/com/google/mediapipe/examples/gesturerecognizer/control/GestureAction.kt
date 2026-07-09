package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInteraction

enum class GestureActionType {
    Trigger,
    Toggle,
    Increment,
    Decrement,
    SetValue,
    Reset
}

data class GestureAction(
    val id: String,
    val label: String,
    val type: GestureActionType = GestureActionType.Trigger,
    val target: String? = null,
    val value: Float? = null
)

data class GestureActionEvent(val action: GestureAction, val bindingId: String, val interaction: GestureInteraction) {
    val timestampMs: Long
        get() = interaction.timestampMs
}
