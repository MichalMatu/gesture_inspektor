package com.google.mediapipe.examples.gesturerecognizer.control

import org.junit.Assert.assertThrows
import org.junit.Test

class GestureConfigurationTest {
    @Test
    fun setValueActionRequiresFiniteValue() {
        assertThrows(IllegalArgumentException::class.java) {
            GestureAction("set", "Set", GestureActionType.SetValue)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GestureAction("set", "Set", GestureActionType.SetValue, value = Float.NaN)
        }
    }

    @Test
    fun bindingRejectsInvalidTimingScoreAndReservedGesture() {
        assertThrows(IllegalArgumentException::class.java) {
            binding(minScore = 1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding(minHoldMs = 200L, maxHoldMs = 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            binding(gestureName = "None")
        }
    }

    @Test
    fun presetRejectsDuplicateBindingIds() {
        assertThrows(IllegalArgumentException::class.java) {
            GesturePreset("preset", "Preset", listOf(binding(), binding()))
        }
    }

    private fun binding(
        gestureName: String = "Open_Palm",
        minScore: Float = 0.60f,
        minHoldMs: Long = 0L,
        maxHoldMs: Long? = null
    ): GestureBinding = GestureBinding(
        id = "binding",
        gestureName = gestureName,
        action = GestureAction("action", "Action"),
        triggerMode = GestureTriggerMode.OncePerHold,
        minScore = minScore,
        minHoldMs = minHoldMs,
        maxHoldMs = maxHoldMs
    )
}
