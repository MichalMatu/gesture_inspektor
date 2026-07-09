package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection
import com.google.mediapipe.examples.gesturerecognizer.gesture.VerticalZone

object InspectorDemoPreset {
    fun create(): GesturePreset = GesturePreset(
        id = "inspector-demo",
        name = "Inspector Demo",
        bindings = listOf(
            GestureBinding(
                id = "open-palm-still",
                gestureName = "Open_Palm",
                action = action("action.open_palm_still", "Open palm still"),
                triggerMode = GestureTriggerMode.OncePerHold,
                movement = MovementDirection.Still,
                minHoldMs = 150L,
                requireNoMovementDuringHold = true
            ),
            GestureBinding(
                id = "open-palm-left",
                gestureName = "Open_Palm",
                action = action("action.open_palm_left", "Open palm left"),
                triggerMode = GestureTriggerMode.ContinuousWhileHeld,
                movement = MovementDirection.Left,
                minHoldMs = 300L,
                repeatIntervalMs = 90L
            ),
            GestureBinding(
                id = "open-palm-right",
                gestureName = "Open_Palm",
                action = action("action.open_palm_right", "Open palm right"),
                triggerMode = GestureTriggerMode.ContinuousWhileHeld,
                movement = MovementDirection.Right,
                minHoldMs = 300L,
                repeatIntervalMs = 90L
            ),
            GestureBinding(
                id = "closed-fist-hold",
                gestureName = "Closed_Fist",
                action = action("action.fist_hold", "Closed fist hold"),
                triggerMode = GestureTriggerMode.OncePerHold,
                minHoldMs = 250L
            ),
            GestureBinding(
                id = "thumb-up-hold",
                gestureName = "Thumb_Up",
                action = action("action.thumb_up_hold", "Thumb up hold", GestureActionType.Increment),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                repeatIntervalMs = 260L
            ),
            GestureBinding(
                id = "thumb-down-hold",
                gestureName = "Thumb_Down",
                action = action("action.thumb_down_hold", "Thumb down hold", GestureActionType.Decrement),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                repeatIntervalMs = 260L
            ),
            GestureBinding(
                id = "pointing-up-up",
                gestureName = "Pointing_Up",
                action = action("action.pointing_up_up", "Pointing up moved up", GestureActionType.Increment),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                movement = MovementDirection.Up,
                repeatIntervalMs = 140L
            ),
            GestureBinding(
                id = "pointing-up-down",
                gestureName = "Pointing_Up",
                action = action("action.pointing_up_down", "Pointing up moved down", GestureActionType.Decrement),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                movement = MovementDirection.Down,
                repeatIntervalMs = 140L
            ),
            GestureBinding(
                id = "victory-still",
                gestureName = "Victory",
                action = action("action.victory_still", "Victory still"),
                triggerMode = GestureTriggerMode.OncePerHold,
                movement = MovementDirection.Still,
                minHoldMs = 150L,
                requireNoMovementDuringHold = true
            ),
            GestureBinding(
                id = "victory-up",
                gestureName = "Victory",
                action = action("action.victory_up", "Victory moved up", GestureActionType.Increment),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                movement = MovementDirection.Up,
                repeatIntervalMs = 140L
            ),
            GestureBinding(
                id = "victory-down",
                gestureName = "Victory",
                action = action("action.victory_down", "Victory moved down", GestureActionType.Decrement),
                triggerMode = GestureTriggerMode.RepeatWhileHeld,
                movement = MovementDirection.Down,
                repeatIntervalMs = 140L
            ),
            GestureBinding(
                id = "i-love-you-bottom-long",
                gestureName = "ILoveYou",
                action = action("action.iloveyou_bottom_long", "ILoveYou bottom long hold", GestureActionType.Reset),
                triggerMode = GestureTriggerMode.OncePerHold,
                verticalZones = setOf(VerticalZone.Bottom),
                minHoldMs = 900L,
                priority = 20,
                exclusiveGroup = "iloveyou"
            ),
            GestureBinding(
                id = "i-love-you-short",
                gestureName = "ILoveYou",
                action = action("action.iloveyou_short", "ILoveYou top/middle short hold", GestureActionType.Toggle),
                triggerMode = GestureTriggerMode.OncePerHold,
                verticalZones = setOf(VerticalZone.Top, VerticalZone.Middle),
                minHoldMs = 150L,
                maxHoldMs = 650L,
                priority = 10,
                exclusiveGroup = "iloveyou"
            )
        )
    )

    private fun action(id: String, label: String, type: GestureActionType = GestureActionType.Trigger): GestureAction = GestureAction(
        id = id,
        label = label,
        type = type
    )
}
