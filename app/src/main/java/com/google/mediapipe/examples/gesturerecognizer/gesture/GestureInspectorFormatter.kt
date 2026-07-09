package com.google.mediapipe.examples.gesturerecognizer.gesture

import java.util.Locale

data class GestureInspectorDisplay(val summary: String, val matchedAction: String, val handDetails: String)

object GestureInspectorFormatter {
    fun format(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long): GestureInspectorDisplay {
        val frameStatus = snapshot.frameStatus()
        val primaryHand = snapshot.interactions.firstOrNull()
        val action = snapshot.matchedAction?.action?.label
            ?: snapshot.lastAction?.action?.label
            ?: "None"

        return GestureInspectorDisplay(
            summary = "Hands ${snapshot.frameSet.handCount} | $frameStatus | ${inferenceTimeMs}ms",
            matchedAction = "Gesture ${primaryHand?.gestureLabel() ?: "None"} | Action $action",
            handDetails = primaryHand?.compactDetails() ?: "Show a hand to inspect gestures"
        )
    }

    fun formatDiagnostics(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long): List<String> {
        val frameStatus = snapshot.frameStatus()
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

    private fun GestureInspectorSnapshot.frameStatus(): String = when {
        frameSet.hands.isEmpty() -> "no hand"
        interactions.any { interaction -> interaction.score < 0.60f || interaction.gestureName == GestureFrameSet.NONE } -> "low confidence"
        interactions.any { interaction -> interaction.stableFrames >= 3 } -> "stable"
        else -> "tracking"
    }

    private fun GestureInspectorSnapshot.handDiagnostics(): List<String> {
        val eventsByHand = actionEvents.associateBy { event -> event.interaction.handIndex }
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
            val bindingId = eventsByHand[hand.handIndex]?.bindingId ?: "-"

            "Hand ${hand.handIndex} | $handedness $handednessScore | " +
                "Best ${hand.name} ${hand.score.percent()} | Top [$candidates] | " +
                "Raw ${interaction.rawCenterX.coord()},${interaction.rawCenterY.coord()} | " +
                "Smooth ${interaction.smoothedCenterX.coord()},${interaction.smoothedCenterY.coord()} | " +
                "Zone $zone | Move ${interaction.movementDirection.name} " +
                "(${interaction.deltaX.coord()},${interaction.deltaY.coord()}) | " +
                "Stable ${interaction.stableFrames} | Hold ${interaction.holdDurationMs}ms | " +
                "Moved ${interaction.hasMovedDuringHold} | Binding $bindingId"
        }
    }

    private fun GestureInteraction.compactDetails(): String =
        "Hold ${holdDurationMs}ms | Move ${movementDirection.name} | Zone ${zoneLabel()}"

    private fun GestureInteraction.gestureLabel(): String = "${frame.bestCandidate?.displayName ?: frame.name} ${frame.score.percent()}"

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
