package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInteraction
import com.google.mediapipe.examples.gesturerecognizer.gesture.HandGestureFrame
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection

data class GestureActionMapperResult(val interactions: List<GestureInteraction>, val actionEvents: List<GestureActionEvent>)

class GestureActionMapper(
    bindings: List<GestureBinding>,
    private val stableFramesRequired: Int = 3,
    private val trackStateRetentionMs: Long = 500L
) {
    private val bindings = bindings.map { binding ->
        binding.copy(
            horizontalZones = binding.horizontalZones?.toSet(),
            verticalZones = binding.verticalZones?.toSet()
        )
    }
    private val consumedOnceBindingsByTrack = mutableMapOf<Int, MutableSet<String>>()
    private val exclusiveGroupWinnersByTrack = mutableMapOf<Int, MutableMap<String, String>>()
    private val lastTriggeredAtByBindingAndTrack = mutableMapOf<TriggerKey, Long>()
    private val activeGestureNameByTrack = mutableMapOf<Int, String>()
    private val lastSeenAtByTrack = mutableMapOf<Int, Long>()
    private var lastMappedTimestampMs: Long? = null

    init {
        require(stableFramesRequired > 0) { "Required stable frame count must be positive." }
        require(trackStateRetentionMs >= 0L) { "Track state retention must not be negative." }
        require(bindings.map { binding -> binding.id }.distinct().size == bindings.size) {
            "Gesture binding IDs must be unique."
        }
    }

    fun map(interactions: List<GestureInteraction>, timestampMs: Long): GestureActionMapperResult {
        require(timestampMs >= 0L) { "Frame timestamp must not be negative." }
        require(interactions.all { interaction -> interaction.timestampMs == timestampMs }) {
            "All interactions must belong to the mapped frame timestamp."
        }
        require(interactions.map { interaction -> interaction.trackingId }.distinct().size == interactions.size) {
            "Tracking IDs must be unique within a mapped frame."
        }

        val previousTimestampMs = lastMappedTimestampMs
        if (previousTimestampMs != null && timestampMs < previousTimestampMs) {
            reset()
        }
        lastMappedTimestampMs = timestampMs

        val activeTrackingIds = interactions.map { interaction -> interaction.trackingId }.toSet()
        expireOldTrackState(timestampMs)
        activeGestureNameByTrack.keys
            .filter { trackingId -> trackingId !in activeTrackingIds }
            .forEach(::resetHold)
        interactions.forEach { interaction -> lastSeenAtByTrack[interaction.trackingId] = timestampMs }

        val events = interactions.mapNotNull { interaction -> mapInteraction(interaction) }
        return GestureActionMapperResult(
            interactions = interactions,
            actionEvents = events
        )
    }

    fun reset() {
        activeGestureNameByTrack.keys.toList().forEach(::resetHold)
        lastTriggeredAtByBindingAndTrack.clear()
        lastSeenAtByTrack.clear()
        lastMappedTimestampMs = null
    }

    private fun mapInteraction(interaction: GestureInteraction): GestureActionEvent? {
        if (interaction.gestureName == GestureFrameSet.NONE || interaction.stableFrames == 0 || !interaction.isTrackingReliable) {
            resetHold(interaction.trackingId)
            return null
        }

        if (activeGestureNameByTrack[interaction.trackingId] != interaction.gestureName) {
            resetHold(interaction.trackingId)
            activeGestureNameByTrack[interaction.trackingId] = interaction.gestureName
        }

        return bindings
            .filter { binding -> binding.matches(interaction) }
            .filter { binding -> binding.canCompeteForFrame(interaction.trackingId) }
            .sortedWith(compareByDescending<GestureBinding> { binding -> binding.priority }.thenBy { binding -> binding.id })
            .firstOrNull()
            ?.nextEvent(interaction)
    }

    private fun GestureBinding.canCompeteForFrame(trackingId: Int): Boolean {
        val group = exclusiveGroup ?: return true
        val winner = exclusiveGroupWinnersByTrack[trackingId]?.get(group)
        return winner == null || winner == id
    }

    private fun GestureBinding.matches(interaction: GestureInteraction): Boolean = matchesGesture(interaction) &&
        matchesHold(interaction) &&
        matchesZones(interaction) &&
        matchesMotion(interaction) &&
        handPreference.matches(interaction.frame, minHandednessScore)

    private fun GestureBinding.matchesGesture(interaction: GestureInteraction): Boolean = gestureName == interaction.gestureName &&
        interaction.isTrackingReliable &&
        interaction.stableFrames >= stableFramesRequired &&
        interaction.score >= minScore

    private fun GestureBinding.matchesHold(interaction: GestureInteraction): Boolean = interaction.holdDurationMs >= minHoldMs &&
        (maxHoldMs == null || interaction.holdDurationMs <= maxHoldMs)

    private fun GestureBinding.matchesZones(interaction: GestureInteraction): Boolean =
        (horizontalZones == null || interaction.horizontalZone in horizontalZones) &&
            (verticalZones == null || interaction.verticalZone in verticalZones)

    private fun GestureBinding.matchesMotion(interaction: GestureInteraction): Boolean =
        (!requireNoMovementDuringHold || !interaction.hasMovedDuringHold) &&
            (movement == null || movement.matches(interaction.movementDirection))

    private fun MovementDirection.matches(actual: MovementDirection): Boolean =
        this == actual || (this == MovementDirection.Any && actual != MovementDirection.Still)

    private fun HandPreference.matches(frame: HandGestureFrame, minScore: Float): Boolean = when (this) {
        HandPreference.Any -> true

        HandPreference.Left -> frame.handednessScore?.let { score -> score >= minScore } == true &&
            frame.handedness.equals("Left", ignoreCase = true)

        HandPreference.Right -> frame.handednessScore?.let { score -> score >= minScore } == true &&
            frame.handedness.equals("Right", ignoreCase = true)
    }

    private fun GestureBinding.nextEvent(interaction: GestureInteraction): GestureActionEvent? {
        val trackingId = interaction.trackingId
        val triggerKey = TriggerKey(trackingId, id)
        val lastTriggeredAt = lastTriggeredAtByBindingAndTrack[triggerKey]
        val intervalMs = when (triggerMode) {
            GestureTriggerMode.OncePerHold -> cooldownMs
            GestureTriggerMode.RepeatWhileHeld -> repeatIntervalMs
        }
        val exclusiveGroupWinner = exclusiveGroup?.let { group -> exclusiveGroupWinners(trackingId)[group] }
        val hasCompetingExclusiveGroupWinner = exclusiveGroupWinner != null && exclusiveGroupWinner != id
        val hasRecentTrigger = lastTriggeredAt != null && interaction.timestampMs - lastTriggeredAt < intervalMs
        val hasConsumedOnceBinding = triggerMode == GestureTriggerMode.OncePerHold && id in consumedOnceBindings(trackingId)
        if (hasCompetingExclusiveGroupWinner || hasRecentTrigger || hasConsumedOnceBinding) return null
        if (triggerMode == GestureTriggerMode.OncePerHold) {
            consumedOnceBindings(trackingId).add(id)
        }

        exclusiveGroup?.let { group -> exclusiveGroupWinners(trackingId).putIfAbsent(group, id) }
        lastTriggeredAtByBindingAndTrack[triggerKey] = interaction.timestampMs

        return GestureActionEvent(
            action = action,
            bindingId = id,
            interaction = interaction
        )
    }

    private fun resetHold(trackingId: Int) {
        activeGestureNameByTrack.remove(trackingId)
        consumedOnceBindingsByTrack.remove(trackingId)
        exclusiveGroupWinnersByTrack.remove(trackingId)
    }

    private fun consumedOnceBindings(trackingId: Int): MutableSet<String> =
        consumedOnceBindingsByTrack.getOrPut(trackingId) { mutableSetOf() }

    private fun exclusiveGroupWinners(trackingId: Int): MutableMap<String, String> =
        exclusiveGroupWinnersByTrack.getOrPut(trackingId) { mutableMapOf() }

    private fun expireOldTrackState(timestampMs: Long) {
        lastSeenAtByTrack
            .filterValues { lastSeenAtMs -> timestampMs - lastSeenAtMs > trackStateRetentionMs }
            .keys
            .toList()
            .forEach(::clearTrackState)
    }

    private fun clearTrackState(trackingId: Int) {
        resetHold(trackingId)
        lastSeenAtByTrack.remove(trackingId)
        lastTriggeredAtByBindingAndTrack.keys.removeAll { key -> key.trackingId == trackingId }
    }

    private data class TriggerKey(val trackingId: Int, val bindingId: String)
}
