package com.google.mediapipe.examples.gesturerecognizer.gesture

import java.util.Locale

data class GestureInspectorDisplay(val summary: String, val matchedAction: String, val handDetails: String)

object GestureInspectorFormatter {
    fun format(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long): GestureInspectorDisplay {
        val frameStatus = snapshot.status.label
        val primaryHand = snapshot.interactions.firstOrNull()
        val matchedAction = snapshot.matchedAction
        val lastAction = snapshot.lastAction
        val action = when {
            matchedAction != null -> "Action ${matchedAction.action.label}"
            lastAction != null -> "Last action ${lastAction.action.label}"
            else -> "Action None"
        }

        return GestureInspectorDisplay(
            summary = "Hands ${snapshot.frameSet.handCount} | $frameStatus | ${inferenceTimeMs}ms",
            matchedAction = "Gesture ${primaryHand?.gestureLabel(snapshot.status) ?: "None"} | $action",
            handDetails = primaryHand?.compactDetails() ?: "Show a hand to inspect gestures"
        )
    }

    fun formatDiagnostics(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long): List<String> {
        val frameStatus = snapshot.status.label
        val matchedAction = snapshot.actionEvents
            .joinToString { event -> "${event.action.label} (${event.bindingId})" }
            .ifEmpty { "None" }
        val lastAction = snapshot.lastAction?.let { event ->
            "${event.action.label} @ ${event.timestampMs}ms"
        } ?: "None"

        return buildList {
            add(
                "Preset=${snapshot.activePresetName} Frame=${snapshot.frameSet.timestampMs}ms " +
                    "Hands=${snapshot.frameSet.handCount} Inference=${inferenceTimeMs}ms " +
                    "Status=$frameStatus Matched=$matchedAction Last=$lastAction"
            )
            if (snapshot.frameSet.hands.isEmpty()) {
                add("No hands detected")
            } else {
                addAll(snapshot.handDiagnostics())
            }
        }
    }

    private fun GestureInspectorSnapshot.handDiagnostics(): List<String> {
        val eventsByTrack = actionEvents.associateBy { event -> event.interaction.trackingId }
        return interactions.map { interaction ->
            val hand = interaction.frame
            val zone = interaction.zoneLabel()
            val candidates = hand.candidates
                .take(3)
                .joinToString { candidate ->
                    "${candidate.rank}. ${candidate.displayName} ${candidate.score.percent()}"
                }
                .ifEmpty { "none" }
            val handedness = hand.handedness ?: "Unknown"
            val handednessScore = hand.handednessScore?.percent() ?: "-"
            val bindingId = eventsByTrack[interaction.trackingId]?.bindingId ?: "-"
            val rawGesture = hand.bestCandidate?.let { candidate ->
                "${candidate.displayName} ${candidate.score.percent()}"
            } ?: "None"
            val landmarkEvidence = hand.landmarkEstimate?.diagnosticLabel() ?: "unavailable"

            "Track ${interaction.trackingId} (detection ${hand.detectionIndex}) | $handedness $handednessScore | " +
                "Resolved ${hand.displayName} ${hand.score.percent()} via ${hand.classificationSource} | " +
                "Raw $rawGesture | Top [$candidates] | Fingers $landmarkEvidence | " +
                "Raw ${interaction.rawCenterX.coord()},${interaction.rawCenterY.coord()} | " +
                "Smooth ${interaction.smoothedCenterX.coord()},${interaction.smoothedCenterY.coord()} | " +
                "Zone $zone | Move ${interaction.movementDirection.name} " +
                "(${interaction.deltaX.coord()},${interaction.deltaY.coord()}) | " +
                "Stable ${interaction.stableFrames} | Hold ${interaction.holdDurationMs}ms | " +
                "Moved ${interaction.hasMovedDuringHold} | Reliable ${interaction.isTrackingReliable} | " +
                "Lost landmarks ${interaction.lostLandmarkFrames} | Binding $bindingId"
        }
    }

    private fun GestureInteraction.compactDetails(): String =
        "Hold ${holdDurationMs}ms | Move ${movementDirection.name} | Zone ${zoneLabel()}"

    private fun GestureInteraction.gestureLabel(status: GestureInspectorStatus): String =
        if (status == GestureInspectorStatus.LowConfidence) {
            val raw = frame.bestCandidate?.let { candidate ->
                "${candidate.displayName} ${candidate.score.percent()}"
            } ?: "None"
            "Uncertain (raw $raw)"
        } else {
            "${frame.displayName} ${score.percent()}"
        }

    private fun LandmarkGestureEstimate.diagnosticLabel(): String =
        "T=${fingers.thumb.percent()} I=${fingers.index.percent()} M=${fingers.middle.percent()} " +
            "R=${fingers.ring.percent()} P=${fingers.pinky.percent()} margin=${margin.percent()}"

    private fun GestureInteraction.zoneLabel(): String = listOfNotNull(
        horizontalZone?.name,
        verticalZone?.name
    ).joinToString("/")
        .ifEmpty { "-" }

    private fun Float.percent(): String = String.format(Locale.US, "%.0f%%", this * 100f)

    private fun Float?.coord(): String = if (this == null) {
        "-"
    } else {
        String.format(Locale.US, "%.2f", this)
    }
}
