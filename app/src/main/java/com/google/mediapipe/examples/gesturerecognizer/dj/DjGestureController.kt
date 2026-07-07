package com.google.mediapipe.examples.gesturerecognizer.dj

import kotlin.math.max
import kotlin.math.min

data class DjDeckState(
    val isPlaying: Boolean = false,
    val cueEnabled: Boolean = false,
    val volume: Int = 75,
    val filter: Int = 0,
    val fxEnabled: Boolean = false,
)

data class DjActionEvent(
    val command: DjCommand,
    val label: String,
    val detail: String,
    val timestampMs: Long,
)

data class DjControllerSnapshot(
    val gesture: GestureFrame,
    val deckA: DjDeckState,
    val crossfader: Int,
    val lastAction: DjActionEvent?,
)

class DjGestureController(
    private val mapper: DjGestureMapper = DjGestureMapper(),
) {
    private var deckA = DjDeckState()
    private var crossfader = 50
    private var lastAction: DjActionEvent? = null

    fun handle(frame: GestureFrame): DjControllerSnapshot {
        mapper.nextCommand(frame)?.let { command ->
            lastAction = apply(command, frame.timestampMs)
        }

        return DjControllerSnapshot(
            gesture = frame,
            deckA = deckA,
            crossfader = crossfader,
            lastAction = lastAction,
        )
    }

    fun reset() {
        mapper.reset()
        deckA = DjDeckState()
        crossfader = 50
        lastAction = null
    }

    private fun apply(command: DjCommand, timestampMs: Long): DjActionEvent {
        return when (command) {
            DjCommand.PlayPauseDeckA -> {
                deckA = deckA.copy(isPlaying = !deckA.isPlaying)
                DjActionEvent(
                    command = command,
                    label = "Deck A Play/Pause",
                    detail = if (deckA.isPlaying) "Deck A playing" else "Deck A paused",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.CueDeckA -> {
                deckA = deckA.copy(cueEnabled = !deckA.cueEnabled)
                DjActionEvent(
                    command = command,
                    label = "Deck A Cue",
                    detail = if (deckA.cueEnabled) "Cue enabled" else "Cue disabled",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.VolumeUpDeckA -> {
                deckA = deckA.copy(volume = min(100, deckA.volume + 5))
                DjActionEvent(
                    command = command,
                    label = "Deck A Volume +",
                    detail = "Volume ${deckA.volume}%",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.VolumeDownDeckA -> {
                deckA = deckA.copy(volume = max(0, deckA.volume - 5))
                DjActionEvent(
                    command = command,
                    label = "Deck A Volume -",
                    detail = "Volume ${deckA.volume}%",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.FilterUpDeckA -> {
                deckA = deckA.copy(filter = min(100, deckA.filter + 10))
                DjActionEvent(
                    command = command,
                    label = "Deck A Filter +",
                    detail = "Filter ${deckA.filter}%",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.CrossfaderCenter -> {
                crossfader = 50
                DjActionEvent(
                    command = command,
                    label = "Crossfader Center",
                    detail = "Crossfader 50%",
                    timestampMs = timestampMs,
                )
            }

            DjCommand.ToggleFxDeckA -> {
                deckA = deckA.copy(fxEnabled = !deckA.fxEnabled)
                DjActionEvent(
                    command = command,
                    label = "Deck A FX",
                    detail = if (deckA.fxEnabled) "FX enabled" else "FX disabled",
                    timestampMs = timestampMs,
                )
            }
        }
    }
}
