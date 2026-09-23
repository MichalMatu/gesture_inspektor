package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.examples.gesturerecognizer.control.GestureActionEvent
import java.util.Locale

enum class GestureInspectorLogLevel {
    Info,
    Verbose
}

data class GestureInspectorLogLine(val level: GestureInspectorLogLevel, val message: String)

class GestureInspectorLogReducer(private val heartbeatIntervalMs: Long = 2_000L, private val verboseIntervalMs: Long = 1_000L) {
    private var lastState: StateSignature? = null
    private var lastHeartbeatAtMs = Long.MIN_VALUE
    private var lastVerboseAtMs = Long.MIN_VALUE

    init {
        require(heartbeatIntervalMs > 0L) { "Heartbeat interval must be positive." }
        require(verboseIntervalMs > 0L) { "Verbose interval must be positive." }
    }

    fun reset() {
        lastState = null
        lastHeartbeatAtMs = Long.MIN_VALUE
        lastVerboseAtMs = Long.MIN_VALUE
    }

    fun reduce(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long, nowMs: Long, verbose: Boolean): List<GestureInspectorLogLine> =
        buildList {
            val state = snapshot.stateSignature()

            if (state != lastState) {
                add(GestureInspectorLogLine(GestureInspectorLogLevel.Info, snapshot.stateLine(inferenceTimeMs)))
                lastState = state
                lastHeartbeatAtMs = nowMs
            } else if (lastHeartbeatAtMs == Long.MIN_VALUE || nowMs - lastHeartbeatAtMs >= heartbeatIntervalMs) {
                add(GestureInspectorLogLine(GestureInspectorLogLevel.Info, snapshot.heartbeatLine(inferenceTimeMs)))
                lastHeartbeatAtMs = nowMs
            }

            snapshot.actionEvents.forEach { event ->
                add(GestureInspectorLogLine(GestureInspectorLogLevel.Info, event.actionLine()))
            }

            if (verbose && shouldWriteVerbose(snapshot, nowMs)) {
                GestureInspectorFormatter
                    .formatDiagnostics(snapshot, inferenceTimeMs)
                    .forEach { line ->
                        add(GestureInspectorLogLine(GestureInspectorLogLevel.Verbose, "VERBOSE $line"))
                    }
                lastVerboseAtMs = nowMs
            }
        }

    private fun shouldWriteVerbose(snapshot: GestureInspectorSnapshot, nowMs: Long): Boolean {
        val hasActionEvent = snapshot.actionEvents.isNotEmpty()
        val isFirstVerboseLine = lastVerboseAtMs == Long.MIN_VALUE
        val intervalElapsed = nowMs - lastVerboseAtMs >= verboseIntervalMs
        return hasActionEvent || isFirstVerboseLine || intervalElapsed
    }

    private fun GestureInspectorSnapshot.stateSignature(): StateSignature {
        val primary = interactions.firstOrNull()
        return StateSignature(
            status = status.label,
            handCount = frameSet.handCount,
            primaryGesture = primary?.gestureName ?: GestureFrameSet.NONE,
            primaryZone = primary?.zoneLabel() ?: "-",
            primaryMovement = primary?.movementDirection?.name ?: "-"
        )
    }

    private fun GestureInspectorSnapshot.stateLine(inferenceTimeMs: Long): String {
        val primary = interactions.firstOrNull()
        return "STATE status=${status.label} hands=${frameSet.handCount} " +
            "gesture=${primary?.gestureLabel() ?: "None"} zone=${primary?.zoneLabel() ?: "-"} " +
            "move=${primary?.movementDirection?.name ?: "-"} inference=${inferenceTimeMs}ms"
    }

    private fun GestureInspectorSnapshot.heartbeatLine(inferenceTimeMs: Long): String {
        val primary = interactions.firstOrNull()
        return "HEARTBEAT status=${status.label} hands=${frameSet.handCount} " +
            "gesture=${primary?.gestureLabel() ?: "None"} hold=${primary?.holdDurationMs ?: 0L}ms " +
            "inference=${inferenceTimeMs}ms"
    }

    private fun GestureActionEvent.actionLine(): String {
        val interaction = interaction
        return "ACTION action=\"${action.label}\" binding=$bindingId track=${interaction.trackingId} " +
            "detection=${interaction.frame.detectionIndex} " +
            "gesture=${interaction.gestureLabel()} hold=${interaction.holdDurationMs}ms " +
            "zone=${interaction.zoneLabel()} move=${interaction.movementDirection.name}"
    }

    private fun GestureInteraction.gestureLabel(): String = "${frame.displayName} ${score.percent()}"

    private fun GestureInteraction.zoneLabel(): String = listOfNotNull(
        horizontalZone?.name,
        verticalZone?.name
    ).joinToString("/")
        .ifEmpty { "-" }

    private fun Float.percent(): String = String.format(Locale.US, "%.0f%%", this * 100f)

    private data class StateSignature(
        val status: String,
        val handCount: Int,
        val primaryGesture: String,
        val primaryZone: String,
        val primaryMovement: String
    )
}
