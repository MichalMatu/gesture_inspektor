package com.google.mediapipe.examples.gesturerecognizer.demo.dj

data class DjDemoDeckState(
    val isPlaying: Boolean = false,
    val cueEnabled: Boolean = false,
    val volume: Int = 75,
    val filter: Int = 0,
    val fxEnabled: Boolean = false,
    val fxMix: Int = 0
)

data class DjDemoState(val deckA: DjDemoDeckState = DjDemoDeckState(), val crossfader: Int = 50)
