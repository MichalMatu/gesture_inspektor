package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorSnapshot
import com.google.mediapipe.examples.gesturerecognizer.gesture.MultiHandGestureInteractionEngine

class GestureController(
    private val preset: GesturePreset = InspectorDemoPreset.create(),
    private val interactionEngine: MultiHandGestureInteractionEngine = MultiHandGestureInteractionEngine(),
    private val actionMapper: GestureActionMapper = GestureActionMapper(preset.bindings)
) {
    private var lastAction: GestureActionEvent? = null

    fun handle(frameSet: GestureFrameSet): GestureInspectorSnapshot {
        val interactions = interactionEngine.update(frameSet)
        val mapperResult = actionMapper.map(interactions)
        mapperResult.actionEvents.lastOrNull()?.let { event -> lastAction = event }

        return GestureInspectorSnapshot(
            activePresetName = preset.name,
            frameSet = frameSet,
            interactions = mapperResult.interactions,
            actionEvents = mapperResult.actionEvents,
            lastAction = lastAction
        )
    }

    fun reset() {
        interactionEngine.reset()
        actionMapper.reset()
        lastAction = null
    }
}
