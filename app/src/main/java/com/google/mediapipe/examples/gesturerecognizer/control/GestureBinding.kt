package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.HorizontalZone
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection
import com.google.mediapipe.examples.gesturerecognizer.gesture.VerticalZone

enum class GestureTriggerMode {
    OncePerHold,
    RepeatWhileHeld,
    ContinuousWhileHeld
}

enum class HandPreference {
    Any,
    Left,
    Right
}

data class GestureBinding(
    val id: String,
    val gestureName: String,
    val action: GestureAction,
    val triggerMode: GestureTriggerMode,
    val minScore: Float = 0.60f,
    val minHoldMs: Long = 0L,
    val maxHoldMs: Long? = null,
    val horizontalZones: Set<HorizontalZone>? = null,
    val verticalZones: Set<VerticalZone>? = null,
    val movement: MovementDirection? = null,
    val requireNoMovementDuringHold: Boolean = false,
    val handPreference: HandPreference = HandPreference.Any,
    val priority: Int = 0,
    val repeatIntervalMs: Long = 300L,
    val cooldownMs: Long = 500L,
    val exclusiveGroup: String? = null
)

data class GesturePreset(val id: String, val name: String, val bindings: List<GestureBinding>)
