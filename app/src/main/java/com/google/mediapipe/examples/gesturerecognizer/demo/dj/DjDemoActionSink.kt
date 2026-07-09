package com.google.mediapipe.examples.gesturerecognizer.demo.dj

import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent
import kotlin.math.max
import kotlin.math.min

class DjDemoActionSink {
    fun apply(event: GestureActionEvent, state: DjDemoState): DjDemoState = when (event.action.id) {
        "dj.play_pause" -> state.copy(deckA = state.deckA.copy(isPlaying = !state.deckA.isPlaying))
        "dj.cue" -> state.copy(deckA = state.deckA.copy(cueEnabled = !state.deckA.cueEnabled))
        "dj.volume_up" -> state.copy(deckA = state.deckA.copy(volume = min(100, state.deckA.volume + 5)))
        "dj.volume_down" -> state.copy(deckA = state.deckA.copy(volume = max(0, state.deckA.volume - 5)))
        "dj.filter_up" -> state.copy(deckA = state.deckA.copy(filter = min(100, state.deckA.filter + 8)))
        "dj.filter_down" -> state.copy(deckA = state.deckA.copy(filter = max(-100, state.deckA.filter - 8)))
        "dj.crossfader_left" -> state.copy(crossfader = max(0, state.crossfader - 5))
        "dj.crossfader_right" -> state.copy(crossfader = min(100, state.crossfader + 5))
        "dj.crossfader_center" -> state.copy(crossfader = 50)
        "dj.fx_toggle" -> state.copy(deckA = state.deckA.copy(fxEnabled = !state.deckA.fxEnabled))
        "dj.fx_mix_up" -> state.copy(deckA = state.deckA.copy(fxMix = min(100, state.deckA.fxMix + 8)))
        "dj.fx_mix_down" -> state.copy(deckA = state.deckA.copy(fxMix = max(0, state.deckA.fxMix - 8)))
        "dj.reset" -> DjDemoState()
        else -> state
    }
}
