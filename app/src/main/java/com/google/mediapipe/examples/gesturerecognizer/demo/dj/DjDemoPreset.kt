package com.google.mediapipe.examples.gesturerecognizer.demo.dj

import com.google.mediapipe.examples.gesturerecognizer.control.GestureAction
import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionType
import com.google.mediapipe.examples.gesturerecognizer.control.GestureBinding
import com.google.mediapipe.examples.gesturerecognizer.control.GesturePreset
import com.google.mediapipe.examples.gesturerecognizer.control.GestureTriggerMode
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection
import com.google.mediapipe.examples.gesturerecognizer.gesture.VerticalZone

object DjDemoPreset {
    fun create(): GesturePreset = GesturePreset(
        id = "dj-demo",
        name = "DJ Demo",
        bindings = listOf(
            binding(
                "dj.play_pause",
                "Open_Palm",
                "Deck A Play/Pause",
                GestureTriggerMode.OncePerHold,
                movement = MovementDirection.Still,
                minHoldMs = 150L,
                requireNoMovement = true
            ),
            binding(
                "dj.cue",
                "Closed_Fist",
                "Deck A Cue",
                GestureTriggerMode.OncePerHold,
                minHoldMs = 250L
            ),
            binding(
                "dj.volume_up",
                "Thumb_Up",
                "Deck A Volume +",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Increment,
                repeatIntervalMs = 260L
            ),
            binding(
                "dj.volume_down",
                "Thumb_Down",
                "Deck A Volume -",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Decrement,
                repeatIntervalMs = 260L
            ),
            binding(
                "dj.filter_up",
                "Pointing_Up",
                "Deck A Filter +",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Increment,
                movement = MovementDirection.Up,
                repeatIntervalMs = 140L
            ),
            binding(
                "dj.filter_down",
                "Pointing_Up",
                "Deck A Filter -",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Decrement,
                movement = MovementDirection.Down,
                repeatIntervalMs = 140L
            ),
            binding(
                "dj.crossfader_left",
                "Open_Palm",
                "Crossfader Left",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Decrement,
                movement = MovementDirection.Left,
                minHoldMs = 300L,
                repeatIntervalMs = 90L
            ),
            binding(
                "dj.crossfader_right",
                "Open_Palm",
                "Crossfader Right",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Increment,
                movement = MovementDirection.Right,
                minHoldMs = 300L,
                repeatIntervalMs = 90L
            ),
            binding(
                "dj.crossfader_center",
                "Victory",
                "Crossfader Center",
                GestureTriggerMode.OncePerHold,
                type = GestureActionType.SetValue,
                movement = MovementDirection.Still,
                minHoldMs = 150L,
                requireNoMovement = true
            ),
            binding(
                "dj.fx_mix_up",
                "Victory",
                "Deck A FX Mix +",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Increment,
                movement = MovementDirection.Up,
                repeatIntervalMs = 140L
            ),
            binding(
                "dj.fx_mix_down",
                "Victory",
                "Deck A FX Mix -",
                GestureTriggerMode.RepeatWhileHeld,
                type = GestureActionType.Decrement,
                movement = MovementDirection.Down,
                repeatIntervalMs = 140L
            ),
            binding(
                "dj.reset",
                "ILoveYou",
                "Deck A Reset",
                GestureTriggerMode.OncePerHold,
                type = GestureActionType.Reset,
                verticalZones = setOf(VerticalZone.Bottom),
                minHoldMs = 900L,
                priority = 20,
                exclusiveGroup = "dj.iloveyou"
            ),
            binding(
                "dj.fx_toggle",
                "ILoveYou",
                "Deck A FX Toggle",
                GestureTriggerMode.OncePerHold,
                type = GestureActionType.Toggle,
                verticalZones = setOf(VerticalZone.Top, VerticalZone.Middle),
                minHoldMs = 150L,
                maxHoldMs = 650L,
                priority = 10,
                exclusiveGroup = "dj.iloveyou"
            )
        )
    )

    private fun binding(
        actionId: String,
        gestureName: String,
        label: String,
        triggerMode: GestureTriggerMode,
        type: GestureActionType = GestureActionType.Trigger,
        movement: MovementDirection? = null,
        verticalZones: Set<VerticalZone>? = null,
        minHoldMs: Long = 0L,
        maxHoldMs: Long? = null,
        repeatIntervalMs: Long = 300L,
        requireNoMovement: Boolean = false,
        priority: Int = 0,
        exclusiveGroup: String? = null
    ): GestureBinding = GestureBinding(
        id = actionId.removePrefix("dj.").replace('_', '-'),
        gestureName = gestureName,
        action = GestureAction(
            id = actionId,
            label = label,
            type = type,
            value = if (type == GestureActionType.SetValue) CROSSFADER_CENTER_VALUE else null
        ),
        triggerMode = triggerMode,
        minScore = if (gestureName == "Victory") MIN_VICTORY_ACTION_SCORE else DEFAULT_ACTION_SCORE,
        movement = movement,
        verticalZones = verticalZones,
        minHoldMs = minHoldMs,
        maxHoldMs = maxHoldMs,
        repeatIntervalMs = repeatIntervalMs,
        requireNoMovementDuringHold = requireNoMovement,
        priority = priority,
        exclusiveGroup = exclusiveGroup
    )

    private const val CROSSFADER_CENTER_VALUE = 0.5f
    private const val DEFAULT_ACTION_SCORE = 0.60f
    private const val MIN_VICTORY_ACTION_SCORE = 0.70f
}
