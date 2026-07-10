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
) {
    init {
        require(id.isNotBlank()) { "Gesture action ID must not be blank." }
        require(label.isNotBlank()) { "Gesture action label must not be blank." }
        require(target == null || target.isNotBlank()) { "Gesture action target must not be blank." }
        require(value == null || value.isFinite()) { "Gesture action value must be finite." }
        require(type != GestureActionType.SetValue || value != null) { "SetValue actions require a value." }
    }
}

data class GestureActionEvent(val action: GestureAction, val bindingId: String, val interaction: GestureInteraction) {
    init {
        require(bindingId.isNotBlank()) { "Gesture binding ID must not be blank." }
    }

    val timestampMs: Long
        get() = interaction.timestampMs
}
