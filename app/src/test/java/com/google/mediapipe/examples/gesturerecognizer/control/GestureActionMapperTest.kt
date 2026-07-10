package com.google.mediapipe.examples.gesturerecognizer.control

import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureCandidate
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInteraction
import com.google.mediapipe.examples.gesturerecognizer.gesture.HandGestureFrame
import com.google.mediapipe.examples.gesturerecognizer.gesture.HorizontalZone
import com.google.mediapipe.examples.gesturerecognizer.gesture.MovementDirection
import com.google.mediapipe.examples.gesturerecognizer.gesture.VerticalZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GestureActionMapperTest {
    @Test
    fun oncePerHoldActionFiresOnlyOnceUntilGestureChanges() {
        val mapper = GestureActionMapper(
            bindings = listOf(binding(id = "open-palm", gestureName = "Open_Palm")),
            stableFramesRequired = 2,
            trackStateRetentionMs = 2_000L
        )

        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 0L, stableFrames = 1)))
        assertEquals(
            "action.open-palm",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 33L, stableFrames = 2))?.id
        )
        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 66L, stableFrames = 3)))
        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 1200L, stableFrames = 4)))

        mapper.map(
            interactions = listOf(interaction("Closed_Fist", timestampMs = 1233L, stableFrames = 1)),
            timestampMs = 1233L
        )

        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 1300L, stableFrames = 1)))
        assertEquals(
            "action.open-palm",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 1333L, stableFrames = 2))?.id
        )
    }

    @Test
    fun repeatActionUsesRepeatInterval() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "thumb-up",
                    gestureName = "Thumb_Up",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    repeatIntervalMs = 300L
                )
            ),
            stableFramesRequired = 2
        )

        assertNull(mapper.nextAction(interaction("Thumb_Up", timestampMs = 0L, stableFrames = 1)))
        assertEquals(
            "action.thumb-up",
            mapper.nextAction(interaction("Thumb_Up", timestampMs = 50L, stableFrames = 2))?.id
        )
        assertNull(mapper.nextAction(interaction("Thumb_Up", timestampMs = 100L, stableFrames = 3)))
        assertNull(mapper.nextAction(interaction("Thumb_Up", timestampMs = 349L, stableFrames = 4)))
        assertEquals(
            "action.thumb-up",
            mapper.nextAction(interaction("Thumb_Up", timestampMs = 350L, stableFrames = 5))?.id
        )
    }

    @Test
    fun matchesScoreHoldPriorityAndMaxHoldConditions() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "low-priority",
                    gestureName = "ILoveYou",
                    priority = 1
                ),
                binding(
                    id = "high-priority",
                    gestureName = "ILoveYou",
                    minScore = 0.80f,
                    minHoldMs = 100L,
                    maxHoldMs = 500L,
                    priority = 10
                )
            ),
            stableFramesRequired = 2
        )

        assertEquals(
            "action.low-priority",
            mapper.nextAction(interaction("ILoveYou", score = 0.70f, holdDurationMs = 200L))?.id
        )

        val highPriorityMapper = GestureActionMapper(
            bindings = listOf(
                binding(id = "low-priority", gestureName = "ILoveYou", priority = 1),
                binding(
                    id = "high-priority",
                    gestureName = "ILoveYou",
                    minScore = 0.80f,
                    minHoldMs = 100L,
                    maxHoldMs = 500L,
                    priority = 10
                )
            ),
            stableFramesRequired = 2
        )
        assertEquals(
            "action.high-priority",
            highPriorityMapper.nextAction(
                interaction("ILoveYou", score = 0.90f, holdDurationMs = 200L)
            )?.id
        )

        val maxHoldMapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "short-hold",
                    gestureName = "ILoveYou",
                    minHoldMs = 50L,
                    maxHoldMs = 100L
                )
            ),
            stableFramesRequired = 2
        )
        assertNull(maxHoldMapper.nextAction(interaction("ILoveYou", holdDurationMs = 101L)))
    }

    @Test
    fun requireNoMovementAndHandPreferenceBlockBindings() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "right-still",
                    gestureName = "Open_Palm",
                    requireNoMovementDuringHold = true,
                    handPreference = HandPreference.Right
                )
            ),
            stableFramesRequired = 2
        )

        assertNull(
            mapper.nextAction(
                interaction("Open_Palm", hasMovedDuringHold = true, handedness = "Right")
            )
        )
        assertNull(
            mapper.nextAction(
                interaction("Open_Palm", hasMovedDuringHold = false, handedness = "Left")
            )
        )
        assertEquals(
            "action.right-still",
            mapper.nextAction(
                interaction("Open_Palm", hasMovedDuringHold = false, handedness = "Right")
            )?.id
        )
    }

    @Test
    fun exclusiveGroupBlocksOtherBindingsInSameHold() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(id = "first", gestureName = "ILoveYou", priority = 10, exclusiveGroup = "iloveyou"),
                binding(id = "second", gestureName = "ILoveYou", priority = 1, exclusiveGroup = "iloveyou")
            ),
            stableFramesRequired = 2,
            trackStateRetentionMs = 2_000L
        )

        assertEquals("action.first", mapper.nextAction(interaction("ILoveYou"))?.id)
        assertNull(mapper.nextAction(interaction("ILoveYou", timestampMs = 1000L, stableFrames = 4)))
    }

    @Test
    fun repeatingExclusiveGroupWinnerCanRepeatButPeerCannotTakeOver() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "winner",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    priority = 10,
                    repeatIntervalMs = 200L,
                    exclusiveGroup = "open-palm"
                ),
                binding(
                    id = "peer",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    priority = 1,
                    repeatIntervalMs = 10L,
                    exclusiveGroup = "open-palm"
                )
            )
        )

        assertEquals("action.winner", mapper.nextAction(interaction("Open_Palm", timestampMs = 100L))?.id)
        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 150L)))
        assertEquals("action.winner", mapper.nextAction(interaction("Open_Palm", timestampMs = 300L))?.id)
    }

    @Test
    fun repeatingExclusiveGroupWinnerKeepsRepeatingWhenHigherPriorityPeerStartsMatching() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "later-high-priority",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    minHoldMs = 200L,
                    priority = 10,
                    repeatIntervalMs = 100L,
                    exclusiveGroup = "open-palm"
                ),
                binding(
                    id = "initial-winner",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    priority = 1,
                    repeatIntervalMs = 100L,
                    exclusiveGroup = "open-palm"
                )
            )
        )

        assertEquals(
            "action.initial-winner",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 0L, holdDurationMs = 0L))?.id
        )
        assertEquals(
            "action.initial-winner",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 200L, holdDurationMs = 200L))?.id
        )
    }

    @Test
    fun highPriorityBindingDoesNotFallThroughDuringCooldown() {
        val mapper = GestureActionMapper(
            bindings = listOf(
                binding(
                    id = "high",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    priority = 10,
                    repeatIntervalMs = 1_000L
                ),
                binding(
                    id = "low",
                    gestureName = "Open_Palm",
                    triggerMode = GestureTriggerMode.RepeatWhileHeld,
                    priority = 1,
                    repeatIntervalMs = 10L
                )
            )
        )

        assertEquals("action.high", mapper.nextAction(interaction("Open_Palm", timestampMs = 100L))?.id)
        assertNull(mapper.nextAction(interaction("Open_Palm", timestampMs = 200L)))
    }

    @Test
    fun retainedStateExpiresBeforeAReappearingTrackIsMapped() {
        val mapper = GestureActionMapper(
            bindings = listOf(binding(id = "open-palm", gestureName = "Open_Palm")),
            trackStateRetentionMs = 500L
        )

        assertEquals(
            "action.open-palm",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 0L))?.id
        )
        assertEquals(
            "action.open-palm",
            mapper.nextAction(interaction("Open_Palm", timestampMs = 1_000L))?.id
        )
    }

    @Test
    fun mutableBindingZonesAreSnapshottedAtConstruction() {
        val zones = mutableSetOf(HorizontalZone.Center)
        val mapper = GestureActionMapper(
            listOf(
                binding(id = "center", gestureName = "Open_Palm").copy(horizontalZones = zones)
            )
        )
        zones.clear()

        assertEquals(
            "action.center",
            mapper.nextAction(interaction("Open_Palm", horizontalZone = HorizontalZone.Center))?.id
        )
    }

    @Test
    fun unreliableTrackingNeverTriggersAction() {
        val mapper = GestureActionMapper(listOf(binding(id = "open-palm", gestureName = "Open_Palm")))

        assertNull(mapper.nextAction(interaction("Open_Palm", isTrackingReliable = false)))
    }

    @Test
    fun rejectsDuplicateBindingIds() {
        val first = binding(id = "duplicate", gestureName = "Open_Palm")
        val second = binding(id = "duplicate", gestureName = "Victory")

        assertThrows(IllegalArgumentException::class.java) {
            GestureActionMapper(listOf(first, second))
        }
    }

    @Test
    fun inspectorDemoOpenPalmMovingRightDoesNotFireStillAction() {
        val mapper = GestureActionMapper(InspectorDemoPreset.create().bindings)

        val event = mapper.map(
            interactions = listOf(
                interaction(
                    "Open_Palm",
                    timestampMs = 300L,
                    holdDurationMs = 300L,
                    movementDirection = MovementDirection.Right,
                    hasMovedDuringHold = true
                )
            ),
            timestampMs = 300L
        ).actionEvents.firstOrNull()

        assertEquals("open-palm-right", event?.bindingId)
        assertEquals("action.open_palm_right", event?.action?.id)
    }

    @Test
    fun inspectorDemoVictoryMovementDoesNotFireStillAction() {
        val mapper = GestureActionMapper(InspectorDemoPreset.create().bindings)

        val event = mapper.map(
            interactions = listOf(
                interaction(
                    "Victory",
                    timestampMs = 200L,
                    movementDirection = MovementDirection.Up,
                    hasMovedDuringHold = true
                )
            ),
            timestampMs = 200L
        ).actionEvents.firstOrNull()

        assertEquals("victory-up", event?.bindingId)
        assertEquals("action.victory_up", event?.action?.id)
    }

    @Test
    fun inspectorDemoRejectsWeakTransitionalVictory() {
        val weakMapper = GestureActionMapper(InspectorDemoPreset.create().bindings)
        val strongMapper = GestureActionMapper(InspectorDemoPreset.create().bindings)

        assertNull(
            weakMapper.nextAction(
                interaction(
                    "Victory",
                    score = 0.69f,
                    movementDirection = MovementDirection.Up,
                    hasMovedDuringHold = true
                )
            )
        )
        assertEquals(
            "action.victory_up",
            strongMapper.nextAction(
                interaction(
                    "Victory",
                    score = 0.70f,
                    movementDirection = MovementDirection.Up,
                    hasMovedDuringHold = true
                )
            )?.id
        )
    }

    @Test
    fun inspectorDemoILoveYouBottomLongDoesNotFireShortAction() {
        val mapper = GestureActionMapper(InspectorDemoPreset.create().bindings)

        val event = mapper.map(
            interactions = listOf(
                interaction(
                    "ILoveYou",
                    timestampMs = 900L,
                    holdDurationMs = 900L,
                    verticalZone = VerticalZone.Bottom
                )
            ),
            timestampMs = 900L
        ).actionEvents.firstOrNull()

        assertEquals("i-love-you-bottom-long", event?.bindingId)
        assertEquals("action.iloveyou_bottom_long", event?.action?.id)
    }

    @Test
    fun inspectorDemoILoveYouTopOrMiddleShortFiresShortAction() {
        val topMapper = GestureActionMapper(InspectorDemoPreset.create().bindings)
        val middleMapper = GestureActionMapper(InspectorDemoPreset.create().bindings)

        assertEquals(
            "action.iloveyou_short",
            topMapper.nextAction(
                interaction("ILoveYou", holdDurationMs = 200L, verticalZone = VerticalZone.Top)
            )?.id
        )
        assertEquals(
            "action.iloveyou_short",
            middleMapper.nextAction(
                interaction("ILoveYou", holdDurationMs = 200L, verticalZone = VerticalZone.Middle)
            )?.id
        )
    }

    private fun binding(
        id: String,
        gestureName: String,
        triggerMode: GestureTriggerMode = GestureTriggerMode.OncePerHold,
        minScore: Float = 0.60f,
        minHoldMs: Long = 0L,
        maxHoldMs: Long? = null,
        movement: MovementDirection? = null,
        requireNoMovementDuringHold: Boolean = false,
        handPreference: HandPreference = HandPreference.Any,
        priority: Int = 0,
        repeatIntervalMs: Long = 300L,
        exclusiveGroup: String? = null
    ): GestureBinding = GestureBinding(
        id = id,
        gestureName = gestureName,
        action = GestureAction(
            id = "action.$id",
            label = id
        ),
        triggerMode = triggerMode,
        minScore = minScore,
        minHoldMs = minHoldMs,
        maxHoldMs = maxHoldMs,
        movement = movement,
        requireNoMovementDuringHold = requireNoMovementDuringHold,
        handPreference = handPreference,
        priority = priority,
        repeatIntervalMs = repeatIntervalMs,
        exclusiveGroup = exclusiveGroup
    )

    private fun GestureActionMapper.nextAction(interaction: GestureInteraction): GestureAction? =
        map(listOf(interaction), interaction.timestampMs).actionEvents.firstOrNull()?.action

    private fun interaction(
        name: String,
        timestampMs: Long = 100L,
        stableFrames: Int = 3,
        holdDurationMs: Long = 300L,
        score: Float = 0.90f,
        movementDirection: MovementDirection = MovementDirection.Still,
        hasMovedDuringHold: Boolean = false,
        horizontalZone: HorizontalZone = HorizontalZone.Center,
        verticalZone: VerticalZone = VerticalZone.Middle,
        handedness: String? = "Right",
        trackingId: Int = 0,
        isTrackingReliable: Boolean = true
    ): GestureInteraction = GestureInteraction(
        trackingId = trackingId,
        frame = HandGestureFrame(
            detectionIndex = 0,
            handedness = handedness,
            handednessScore = 0.90f,
            candidates = listOf(GestureCandidate(name, score, 1)),
            centerX = 0.50f,
            centerY = 0.50f,
            landmarkCount = 21
        ),
        timestampMs = timestampMs,
        stableFrames = stableFrames,
        holdDurationMs = holdDurationMs,
        rawCenterX = 0.50f,
        rawCenterY = 0.50f,
        smoothedCenterX = 0.50f,
        smoothedCenterY = 0.50f,
        deltaX = if (movementDirection == MovementDirection.Right) 0.05f else 0f,
        deltaY = if (movementDirection == MovementDirection.Up) -0.05f else 0f,
        horizontalZone = horizontalZone,
        verticalZone = verticalZone,
        movementDirection = movementDirection,
        hasMovedDuringHold = hasMovedDuringHold,
        lostLandmarkFrames = 0,
        isTrackingReliable = isTrackingReliable
    )
}
