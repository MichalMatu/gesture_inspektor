package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInteraction
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection

data class GestureActionMapperResult(val interactions: List<GestureInteraction>, val actionEvents: List<GestureActionEvent>)

class GestureActionMapper(private val bindings: List<GestureBinding>, private val stableFramesRequired: Int = 3) {
    private val consumedOnceBindingsByHand = mutableMapOf<Int, MutableSet<String>>()
    private val consumedExclusiveGroupsByHand = mutableMapOf<Int, MutableSet<String>>()
    private val lastTriggeredAtByBindingAndHand = mutableMapOf<String, Long>()
    private val activeGestureNameByHand = mutableMapOf<Int, String>()

    fun map(interactions: List<GestureInteraction>): GestureActionMapperResult {
        val activeHandIndexes = interactions.map { interaction -> interaction.handIndex }.toSet()
        activeGestureNameByHand.keys
            .filter { handIndex -> handIndex !in activeHandIndexes }
            .forEach { handIndex -> resetHold(handIndex) }

        val events = interactions.mapNotNull { interaction -> mapInteraction(interaction) }
        return GestureActionMapperResult(
            interactions = interactions,
            actionEvents = events
        )
    }

    fun nextAction(interaction: GestureInteraction): GestureAction? = map(listOf(interaction)).actionEvents.firstOrNull()?.action

    fun reset() {
        activeGestureNameByHand.keys.toList().forEach { handIndex -> resetHold(handIndex) }
        lastTriggeredAtByBindingAndHand.clear()
    }

    private fun mapInteraction(interaction: GestureInteraction): GestureActionEvent? {
        if (interaction.gestureName == GestureFrameSet.NONE || interaction.stableFrames == 0) {
            resetHold(interaction.handIndex)
            return null
        }

        if (activeGestureNameByHand[interaction.handIndex] != interaction.gestureName) {
            resetHold(interaction.handIndex)
            activeGestureNameByHand[interaction.handIndex] = interaction.gestureName
        }

        return bindings
            .filter { binding -> binding.matches(interaction) }
            .sortedByDescending { binding -> binding.priority }
            .firstNotNullOfOrNull { binding -> binding.nextEvent(interaction) }
    }

    private fun GestureBinding.matches(interaction: GestureInteraction): Boolean = matchesGesture(interaction) &&
        matchesHold(interaction) &&
        matchesZones(interaction) &&
        matchesMotion(interaction) &&
        handPreference.matches(interaction.frame.handedness)

    private fun GestureBinding.matchesGesture(interaction: GestureInteraction): Boolean = gestureName == interaction.gestureName &&
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

    private fun HandPreference.matches(actual: String?): Boolean = when (this) {
        HandPreference.Any -> true
        HandPreference.Left -> actual.equals("Left", ignoreCase = true)
        HandPreference.Right -> actual.equals("Right", ignoreCase = true)
    }

    private fun GestureBinding.nextEvent(interaction: GestureInteraction): GestureActionEvent? {
        val handIndex = interaction.handIndex
        val triggerKey = "$handIndex:$id"
        val lastTriggeredAt = lastTriggeredAtByBindingAndHand[triggerKey]
        val intervalMs = when (triggerMode) {
            GestureTriggerMode.OncePerHold -> cooldownMs
            GestureTriggerMode.RepeatWhileHeld -> repeatIntervalMs
            GestureTriggerMode.ContinuousWhileHeld -> repeatIntervalMs
        }
        val hasConsumedExclusiveGroup = exclusiveGroup != null && exclusiveGroup in consumedExclusiveGroups(handIndex)
        val hasRecentTrigger = lastTriggeredAt != null && interaction.timestampMs - lastTriggeredAt < intervalMs
        val hasConsumedOnceBinding = triggerMode == GestureTriggerMode.OncePerHold && id in consumedOnceBindings(handIndex)
        if (hasConsumedExclusiveGroup || hasRecentTrigger || hasConsumedOnceBinding) return null
        if (triggerMode == GestureTriggerMode.OncePerHold) {
            consumedOnceBindings(handIndex).add(id)
        }

        exclusiveGroup?.let { group -> consumedExclusiveGroups(handIndex).add(group) }
        lastTriggeredAtByBindingAndHand[triggerKey] = interaction.timestampMs

        return GestureActionEvent(
            action = action,
            bindingId = id,
            interaction = interaction
        )
    }

    private fun resetHold(handIndex: Int) {
        activeGestureNameByHand.remove(handIndex)
        consumedOnceBindingsByHand.remove(handIndex)
        consumedExclusiveGroupsByHand.remove(handIndex)
    }

    private fun consumedOnceBindings(handIndex: Int): MutableSet<String> = consumedOnceBindingsByHand.getOrPut(handIndex) { mutableSetOf() }

    private fun consumedExclusiveGroups(handIndex: Int): MutableSet<String> =
        consumedExclusiveGroupsByHand.getOrPut(handIndex) { mutableSetOf() }
}
