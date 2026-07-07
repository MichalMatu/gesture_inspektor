package com.google.mediapipe.examples.gesturerecognizer.dj

enum class DjCommand {
    PlayPauseDeckA,
    CueDeckA,
    VolumeUpDeckA,
    VolumeDownDeckA,
    FilterUpDeckA,
    CrossfaderCenter,
    ToggleFxDeckA,
}

enum class DjTriggerMode {
    OncePerHold,
    RepeatWhileHeld,
}

data class DjGestureBinding(
    val gestureName: String,
    val command: DjCommand,
    val triggerMode: DjTriggerMode,
    val minScore: Float = 0.60f,
    val repeatIntervalMs: Long = 300L,
)
