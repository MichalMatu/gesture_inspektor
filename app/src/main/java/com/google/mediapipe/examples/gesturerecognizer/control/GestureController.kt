package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorSnapshot
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInteractionEngine
import com.google.mediapipe.examples.gesturerecognizer.gesture.MultiHandGestureInteractionEngine

class GestureController(preset: GesturePreset = InspectorDemoPreset.create(), private val maxFrameGapMs: Long = 500L) {
    private val preset = GesturePreset(
        id = preset.id,
        name = preset.name,
        bindings = preset.bindings.toList()
    )
    private val interactionEngine = MultiHandGestureInteractionEngine(
        engineFactory = { GestureInteractionEngine(maxFrameGapMs = maxFrameGapMs) },
        maxTrackGapMs = maxFrameGapMs
    )
    private val actionMapper = GestureActionMapper(
        bindings = preset.bindings,
        trackStateRetentionMs = maxFrameGapMs
    )
    private var lastAction: GestureActionEvent? = null
    private var lastFrameTimestampMs: Long? = null
    private var lastSnapshot: GestureInspectorSnapshot? = null

    init {
        require(maxFrameGapMs > 0L) { "Maximum frame gap must be positive." }
    }

    fun handle(frameSet: GestureFrameSet): GestureInspectorSnapshot {
        val previousTimestampMs = lastFrameTimestampMs
        if (previousTimestampMs != null && frameSet.timestampMs <= previousTimestampMs) {
            return checkNotNull(lastSnapshot) { "A previous frame timestamp requires a previous snapshot." }
                .copy(actionEvents = emptyList())
        }
        if (previousTimestampMs != null && frameSet.timestampMs - previousTimestampMs > maxFrameGapMs) {
            resetPipelineState()
        }
        val currentFrame = frameSet.snapshotCopy()
        lastFrameTimestampMs = currentFrame.timestampMs

        val interactions = interactionEngine.update(currentFrame)
        val mapperResult = actionMapper.map(interactions, currentFrame.timestampMs)
        mapperResult.actionEvents.lastOrNull()?.let { event -> lastAction = event }

        return GestureInspectorSnapshot(
            activePresetName = preset.name,
            frameSet = currentFrame,
            interactions = mapperResult.interactions,
            actionEvents = mapperResult.actionEvents,
            lastAction = lastAction
        ).also { snapshot -> lastSnapshot = snapshot }
    }

    fun reset() {
        resetPipelineState()
        lastFrameTimestampMs = null
        lastSnapshot = null
    }

    private fun resetPipelineState() {
        interactionEngine.reset()
        actionMapper.reset()
        lastAction = null
    }

    private fun GestureFrameSet.snapshotCopy(): GestureFrameSet = GestureFrameSet(
        timestampMs = timestampMs,
        hands = hands.map { hand -> hand.copy(candidates = hand.candidates.toList()) }
    )
}
