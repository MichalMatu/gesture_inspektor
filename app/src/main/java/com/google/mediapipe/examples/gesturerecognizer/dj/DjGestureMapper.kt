package com.google.mediapipe.examples.gesturerecognizer.dj

class DjGestureMapper(
    bindings: List<DjGestureBinding> = defaultBindings(),
    private val stableFramesRequired: Int = 3,
) {
    private val bindingsByGesture = bindings.associateBy { it.gestureName }
    private val lastRepeatedCommandAt = mutableMapOf<DjCommand, Long>()

    private var activeGestureName: String? = null
    private var stableFrameCount = 0
    private var consumedOnceGestureName: String? = null

    fun nextCommand(frame: GestureFrame): DjCommand? {
        val binding = bindingsByGesture[frame.name]
        if (binding == null || frame.score < binding.minScore) {
            resetActiveGesture()
            return null
        }

        if (activeGestureName != frame.name) {
            activeGestureName = frame.name
            stableFrameCount = 1
            consumedOnceGestureName = null
            return null
        }

        stableFrameCount += 1
        if (stableFrameCount < stableFramesRequired) {
            return null
        }

        return when (binding.triggerMode) {
            DjTriggerMode.OncePerHold -> nextOncePerHoldCommand(binding)
            DjTriggerMode.RepeatWhileHeld -> nextRepeatedCommand(binding, frame.timestampMs)
        }
    }

    fun reset() {
        resetActiveGesture()
        lastRepeatedCommandAt.clear()
    }

    private fun nextOncePerHoldCommand(binding: DjGestureBinding): DjCommand? {
        if (consumedOnceGestureName == binding.gestureName) {
            return null
        }

        consumedOnceGestureName = binding.gestureName
        return binding.command
    }

    private fun nextRepeatedCommand(
        binding: DjGestureBinding,
        timestampMs: Long,
    ): DjCommand? {
        val lastAt = lastRepeatedCommandAt[binding.command]
        if (lastAt != null && timestampMs - lastAt < binding.repeatIntervalMs) {
            return null
        }

        lastRepeatedCommandAt[binding.command] = timestampMs
        return binding.command
    }

    private fun resetActiveGesture() {
        activeGestureName = null
        stableFrameCount = 0
        consumedOnceGestureName = null
    }

    companion object {
        fun defaultBindings(): List<DjGestureBinding> = listOf(
            DjGestureBinding(
                gestureName = "Open_Palm",
                command = DjCommand.PlayPauseDeckA,
                triggerMode = DjTriggerMode.OncePerHold,
            ),
            DjGestureBinding(
                gestureName = "Closed_Fist",
                command = DjCommand.CueDeckA,
                triggerMode = DjTriggerMode.OncePerHold,
            ),
            DjGestureBinding(
                gestureName = "Thumb_Up",
                command = DjCommand.VolumeUpDeckA,
                triggerMode = DjTriggerMode.RepeatWhileHeld,
            ),
            DjGestureBinding(
                gestureName = "Thumb_Down",
                command = DjCommand.VolumeDownDeckA,
                triggerMode = DjTriggerMode.RepeatWhileHeld,
            ),
            DjGestureBinding(
                gestureName = "Pointing_Up",
                command = DjCommand.FilterUpDeckA,
                triggerMode = DjTriggerMode.RepeatWhileHeld,
            ),
            DjGestureBinding(
                gestureName = "Victory",
                command = DjCommand.CrossfaderCenter,
                triggerMode = DjTriggerMode.OncePerHold,
            ),
            DjGestureBinding(
                gestureName = "ILoveYou",
                command = DjCommand.ToggleFxDeckA,
                triggerMode = DjTriggerMode.OncePerHold,
            ),
        )
    }
}
