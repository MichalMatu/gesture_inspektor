package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.HorizontalZone
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection
import com.google.mediapipe.examples.gesturerecognizer.gesture.VerticalZone

enum class GestureTriggerMode {
    OncePerHold,
    RepeatWhileHeld
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
    val minHandednessScore: Float = 0.50f,
    val priority: Int = 0,
    val repeatIntervalMs: Long = 300L,
    val cooldownMs: Long = 500L,
    val exclusiveGroup: String? = null
) {
    init {
        require(id.isNotBlank()) { "Gesture binding ID must not be blank." }
        require(gestureName.isNotBlank()) { "Gesture name must not be blank." }
        require(gestureName != GestureFrameSet.NONE) { "The reserved None gesture cannot be bound to an action." }
        require(minScore.isFinite() && minScore in 0f..1f) { "Minimum gesture score must be between 0 and 1." }
        require(minHandednessScore.isFinite() && minHandednessScore in 0f..1f) {
            "Minimum handedness score must be between 0 and 1."
        }
        require(minHoldMs >= 0L) { "Minimum hold duration must not be negative." }
        require(maxHoldMs == null || maxHoldMs >= minHoldMs) {
            "Maximum hold duration must be greater than or equal to the minimum."
        }
        require(horizontalZones == null || horizontalZones.isNotEmpty()) { "Horizontal zones must not be empty." }
        require(verticalZones == null || verticalZones.isNotEmpty()) { "Vertical zones must not be empty." }
        require(repeatIntervalMs > 0L) { "Repeat interval must be positive." }
        require(cooldownMs >= 0L) { "Cooldown must not be negative." }
        require(exclusiveGroup == null || exclusiveGroup.isNotBlank()) { "Exclusive group must not be blank." }
    }
}

data class GesturePreset(val id: String, val name: String, val bindings: List<GestureBinding>) {
    init {
        require(id.isNotBlank()) { "Gesture preset ID must not be blank." }
        require(name.isNotBlank()) { "Gesture preset name must not be blank." }
        require(bindings.map { binding -> binding.id }.distinct().size == bindings.size) {
            "Gesture binding IDs must be unique within a preset."
        }
    }
}
