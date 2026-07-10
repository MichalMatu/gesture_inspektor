package com.google.mediapipe.examples.gesturerecognizer.gesture

import kotlin.math.abs
import kotlin.math.hypot

enum class HorizontalZone {
    Left,
    Center,
    Right
}

enum class VerticalZone {
    Top,
    Middle,
    Bottom
}

enum class MovementDirection {
    Still,
    Left,
    Right,
    Up,
    Down,
    Any
}

data class GestureInteraction(
    val trackingId: Int,
    val frame: HandGestureFrame,
    val timestampMs: Long,
    val stableFrames: Int,
    val holdDurationMs: Long,
    val rawCenterX: Float?,
    val rawCenterY: Float?,
    val smoothedCenterX: Float?,
    val smoothedCenterY: Float?,
    val deltaX: Float,
    val deltaY: Float,
    val horizontalZone: HorizontalZone?,
    val verticalZone: VerticalZone?,
    val movementDirection: MovementDirection,
    val hasMovedDuringHold: Boolean,
    val lostLandmarkFrames: Int,
    val isTrackingReliable: Boolean
) {
    init {
        require(trackingId >= 0) { "Tracking ID must not be negative." }
        require(timestampMs >= 0L) { "Interaction timestamp must not be negative." }
        require(stableFrames >= 0) { "Stable frame count must not be negative." }
        require(holdDurationMs >= 0L) { "Hold duration must not be negative." }
        require(lostLandmarkFrames >= 0) { "Lost landmark frame count must not be negative." }
    }

    val gestureName: String
        get() = frame.name

    val score: Float
        get() = frame.score
}

class GestureInteractionEngine(
    private val minTrackedScore: Float = 0.60f,
    private val movementDeadZone: Float = 0.035f,
    private val smoothingAlpha: Float = 0.25f,
    private val maxFrameGapMs: Long = 500L,
    private val minimumLandmarkCount: Int = 21,
    private val zoneHysteresis: Float = 0.02f
) {
    private var activeGestureName: String? = null
    private var stableFrames = 0
    private var holdStartedAtMs = 0L
    private var smoothedCenterX: Float? = null
    private var smoothedCenterY: Float? = null
    private var movementReferenceX: Float? = null
    private var movementReferenceY: Float? = null
    private var horizontalZone: HorizontalZone? = null
    private var verticalZone: VerticalZone? = null
    private var hasMovedDuringHold = false
    private var lostLandmarkFrames = 0
    private var lastTimestampMs: Long? = null
    private var activeTrackingId: Int? = null

    init {
        require(minTrackedScore.isFinite() && minTrackedScore in 0f..1f) { "Minimum tracked score must be between 0 and 1." }
        require(movementDeadZone.isFinite() && movementDeadZone in 0f..1f) { "Movement dead zone must be between 0 and 1." }
        require(smoothingAlpha.isFinite() && smoothingAlpha > 0f && smoothingAlpha <= 1f) {
            "Smoothing alpha must be greater than 0 and at most 1."
        }
        require(maxFrameGapMs > 0L) { "Maximum frame gap must be positive." }
        require(minimumLandmarkCount > 0) { "Minimum landmark count must be positive." }
        require(zoneHysteresis.isFinite() && zoneHysteresis >= 0f && zoneHysteresis < ZONE_WIDTH / 2f) {
            "Zone hysteresis must be non-negative and smaller than half a zone."
        }
    }

    fun update(trackingId: Int, frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        require(trackingId >= 0) { "Tracking ID must not be negative." }
        require(timestampMs >= 0L) { "Frame timestamp must not be negative." }

        if (activeTrackingId != null && activeTrackingId != trackingId) {
            reset()
        }
        val previousTimestampMs = lastTimestampMs
        if (previousTimestampMs != null && (timestampMs <= previousTimestampMs || timestampMs - previousTimestampMs > maxFrameGapMs)) {
            reset()
        }
        activeTrackingId = trackingId
        lastTimestampMs = timestampMs

        return when {
            frame.name == GestureFrameSet.NONE || frame.score < minTrackedScore || !frame.hasReliableLandmarks() ->
                updateUntrackedFrame(trackingId, frame, timestampMs)

            activeGestureName != frame.name -> startGestureHold(trackingId, frame, timestampMs)

            else -> continueGestureHold(trackingId, frame, timestampMs)
        }
    }

    private fun updateUntrackedFrame(trackingId: Int, frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        val consecutiveLostLandmarkFrames = if (frame.hasReliableLandmarks()) 0 else lostLandmarkFrames + 1
        reset()
        lostLandmarkFrames = consecutiveLostLandmarkFrames
        return frame.toInteraction(
            trackingId = trackingId,
            timestampMs = timestampMs,
            stableFrames = 0,
            holdDurationMs = 0L,
            deltaX = 0f,
            deltaY = 0f,
            movementDirection = MovementDirection.Still,
            isTrackingReliable = frame.hasReliableLandmarks()
        )
    }

    private fun startGestureHold(trackingId: Int, frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        activeGestureName = frame.name
        stableFrames = 1
        holdStartedAtMs = timestampMs
        smoothedCenterX = frame.centerX
        smoothedCenterY = frame.centerY
        movementReferenceX = frame.centerX
        movementReferenceY = frame.centerY
        horizontalZone = frame.centerX?.toHorizontalZone()
        verticalZone = frame.centerY?.toVerticalZone()
        hasMovedDuringHold = false
        lostLandmarkFrames = 0

        return frame.toInteraction(
            trackingId = trackingId,
            timestampMs = timestampMs,
            stableFrames = stableFrames,
            holdDurationMs = 0L,
            deltaX = 0f,
            deltaY = 0f,
            movementDirection = MovementDirection.Still,
            isTrackingReliable = frame.hasReliableLandmarks()
        )
    }

    private fun continueGestureHold(trackingId: Int, frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        stableFrames += 1

        val previousSmoothedCenterX = smoothedCenterX
        val previousSmoothedCenterY = smoothedCenterY
        val nextSmoothedCenterX = smoothedValue(
            current = frame.centerX,
            previous = smoothedCenterX
        )
        val nextSmoothedCenterY = smoothedValue(
            current = frame.centerY,
            previous = smoothedCenterY
        )
        smoothedCenterX = nextSmoothedCenterX
        smoothedCenterY = nextSmoothedCenterY
        horizontalZone = nextSmoothedCenterX?.toHorizontalZone(horizontalZone)
        verticalZone = nextSmoothedCenterY?.toVerticalZone(verticalZone)

        val hasContinuousCenter = previousSmoothedCenterX != null &&
            previousSmoothedCenterY != null &&
            nextSmoothedCenterX != null &&
            nextSmoothedCenterY != null
        if (!hasContinuousCenter) {
            movementReferenceX = nextSmoothedCenterX
            movementReferenceY = nextSmoothedCenterY
        }

        val deltaX = if (hasContinuousCenter) {
            val centerX = checkNotNull(nextSmoothedCenterX)
            centerX - (movementReferenceX ?: centerX)
        } else {
            0f
        }
        val deltaY = if (hasContinuousCenter) {
            val centerY = checkNotNull(nextSmoothedCenterY)
            centerY - (movementReferenceY ?: centerY)
        } else {
            0f
        }
        val movementDirection = movementDirection(deltaX, deltaY)
        if (movementDirection != MovementDirection.Still) {
            hasMovedDuringHold = true
            movementReferenceX = nextSmoothedCenterX
            movementReferenceY = nextSmoothedCenterY
        }

        return frame.toInteraction(
            trackingId = trackingId,
            timestampMs = timestampMs,
            stableFrames = stableFrames,
            holdDurationMs = timestampMs - holdStartedAtMs,
            deltaX = deltaX,
            deltaY = deltaY,
            movementDirection = movementDirection,
            isTrackingReliable = frame.hasReliableLandmarks()
        )
    }

    fun reset() {
        activeGestureName = null
        stableFrames = 0
        holdStartedAtMs = 0L
        smoothedCenterX = null
        smoothedCenterY = null
        movementReferenceX = null
        movementReferenceY = null
        horizontalZone = null
        verticalZone = null
        hasMovedDuringHold = false
        lostLandmarkFrames = 0
        lastTimestampMs = null
        activeTrackingId = null
    }

    private fun smoothedValue(current: Float?, previous: Float?): Float? = when {
        current == null -> null
        previous == null -> current
        else -> previous + (current - previous) * smoothingAlpha
    }

    private fun movementDirection(deltaX: Float, deltaY: Float): MovementDirection {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        if (absX <= movementDeadZone && absY <= movementDeadZone) {
            return MovementDirection.Still
        }

        return if (absX >= absY) {
            if (deltaX > 0f) MovementDirection.Right else MovementDirection.Left
        } else {
            if (deltaY > 0f) MovementDirection.Down else MovementDirection.Up
        }
    }

    private fun HandGestureFrame.toInteraction(
        trackingId: Int,
        timestampMs: Long,
        stableFrames: Int,
        holdDurationMs: Long,
        deltaX: Float,
        deltaY: Float,
        movementDirection: MovementDirection,
        isTrackingReliable: Boolean
    ): GestureInteraction = GestureInteraction(
        trackingId = trackingId,
        frame = this,
        timestampMs = timestampMs,
        stableFrames = stableFrames,
        holdDurationMs = holdDurationMs,
        rawCenterX = centerX,
        rawCenterY = centerY,
        smoothedCenterX = smoothedCenterX,
        smoothedCenterY = smoothedCenterY,
        deltaX = deltaX,
        deltaY = deltaY,
        horizontalZone = horizontalZone,
        verticalZone = verticalZone,
        movementDirection = movementDirection,
        hasMovedDuringHold = hasMovedDuringHold,
        lostLandmarkFrames = lostLandmarkFrames,
        isTrackingReliable = isTrackingReliable
    )

    private fun HandGestureFrame.hasReliableLandmarks(): Boolean =
        centerX != null && centerY != null && landmarkCount >= minimumLandmarkCount

    private fun Float.toHorizontalZone(previous: HorizontalZone? = null): HorizontalZone = when (previous) {
        HorizontalZone.Left -> if (this < FIRST_ZONE_BOUNDARY + zoneHysteresis) HorizontalZone.Left else baseHorizontalZone()

        HorizontalZone.Center -> when {
            this < FIRST_ZONE_BOUNDARY - zoneHysteresis -> HorizontalZone.Left
            this >= SECOND_ZONE_BOUNDARY + zoneHysteresis -> HorizontalZone.Right
            else -> HorizontalZone.Center
        }

        HorizontalZone.Right -> if (this >= SECOND_ZONE_BOUNDARY - zoneHysteresis) HorizontalZone.Right else baseHorizontalZone()

        null -> baseHorizontalZone()
    }

    private fun Float.toVerticalZone(previous: VerticalZone? = null): VerticalZone = when (previous) {
        VerticalZone.Top -> if (this < FIRST_ZONE_BOUNDARY + zoneHysteresis) VerticalZone.Top else baseVerticalZone()

        VerticalZone.Middle -> when {
            this < FIRST_ZONE_BOUNDARY - zoneHysteresis -> VerticalZone.Top
            this >= SECOND_ZONE_BOUNDARY + zoneHysteresis -> VerticalZone.Bottom
            else -> VerticalZone.Middle
        }

        VerticalZone.Bottom -> if (this >= SECOND_ZONE_BOUNDARY - zoneHysteresis) VerticalZone.Bottom else baseVerticalZone()

        null -> baseVerticalZone()
    }

    private fun Float.baseHorizontalZone(): HorizontalZone = when {
        this < FIRST_ZONE_BOUNDARY -> HorizontalZone.Left
        this < SECOND_ZONE_BOUNDARY -> HorizontalZone.Center
        else -> HorizontalZone.Right
    }

    private fun Float.baseVerticalZone(): VerticalZone = when {
        this < FIRST_ZONE_BOUNDARY -> VerticalZone.Top
        this < SECOND_ZONE_BOUNDARY -> VerticalZone.Middle
        else -> VerticalZone.Bottom
    }

    private companion object {
        const val FIRST_ZONE_BOUNDARY = 0.33f
        const val SECOND_ZONE_BOUNDARY = 0.66f
        const val ZONE_WIDTH = 0.33f
    }
}

class MultiHandGestureInteractionEngine(
    private val engineFactory: () -> GestureInteractionEngine = { GestureInteractionEngine() },
    private val maxMatchDistance: Float = 0.35f,
    private val maxTrackGapMs: Long = 500L,
    private val minIdentityHandednessScore: Float = 0.75f
) {
    private val tracksById = mutableMapOf<Int, TrackedHand>()
    private var nextTrackingId = 0
    private var lastTimestampMs: Long? = null

    init {
        require(maxMatchDistance.isFinite() && maxMatchDistance > 0f) { "Maximum hand match distance must be positive." }
        require(maxTrackGapMs > 0L) { "Maximum track gap must be positive." }
        require(minIdentityHandednessScore.isFinite() && minIdentityHandednessScore in 0f..1f) {
            "Minimum identity handedness score must be between 0 and 1."
        }
    }

    fun update(frameSet: GestureFrameSet): List<GestureInteraction> {
        val previousTimestampMs = lastTimestampMs
        if (previousTimestampMs != null && frameSet.timestampMs <= previousTimestampMs) {
            reset()
        }
        lastTimestampMs = frameSet.timestampMs
        expireOldTracks(frameSet.timestampMs)

        val matchedTracks = matchTracks(frameSet.hands)
        val activeTrackIds = mutableSetOf<Int>()
        val interactions = frameSet.hands.map { hand ->
            val track = matchedTracks[hand.detectionIndex] ?: createTrack(hand, frameSet.timestampMs)
            if (track.wasMissing) {
                track.engine.reset()
            }
            track.wasMissing = false
            track.lastCenterX = hand.centerX ?: track.lastCenterX
            track.lastCenterY = hand.centerY ?: track.lastCenterY
            hand.identityHandedness()?.let { handedness -> track.handedness = handedness }
            track.lastSeenAtMs = frameSet.timestampMs
            activeTrackIds += track.trackingId

            track.engine.update(track.trackingId, hand, frameSet.timestampMs)
        }

        tracksById.values
            .filter { track -> track.trackingId !in activeTrackIds && !track.wasMissing }
            .forEach { track ->
                track.engine.reset()
                track.wasMissing = true
            }

        return interactions.sortedBy { interaction -> interaction.trackingId }
    }

    private fun matchTracks(hands: List<HandGestureFrame>): Map<Int, TrackedHand> {
        val sortedHands = hands.sortedBy { hand -> hand.detectionIndex }
        val candidatesByDetection = sortedHands.associate { hand ->
            hand.detectionIndex to tracksById.values
                .mapNotNull { track -> matchCost(hand, track)?.let { cost -> MatchCandidate(track, cost) } }
                .sortedWith(compareBy<MatchCandidate> { candidate -> candidate.cost }.thenBy { candidate -> candidate.track.trackingId })
        }
        var best = MatchSolution(emptyMap(), Float.POSITIVE_INFINITY)

        fun search(handOffset: Int, usedTrackIds: Set<Int>, assignments: Map<Int, TrackedHand>, totalCost: Float) {
            if (handOffset == sortedHands.size) {
                if (assignments.size > best.assignments.size ||
                    (assignments.size == best.assignments.size && totalCost < best.totalCost)
                ) {
                    best = MatchSolution(assignments, totalCost)
                }
                return
            }

            val hand = sortedHands[handOffset]
            candidatesByDetection.getValue(hand.detectionIndex)
                .filter { candidate -> candidate.track.trackingId !in usedTrackIds }
                .forEach { candidate ->
                    search(
                        handOffset = handOffset + 1,
                        usedTrackIds = usedTrackIds + candidate.track.trackingId,
                        assignments = assignments + (hand.detectionIndex to candidate.track),
                        totalCost = totalCost + candidate.cost
                    )
                }
            search(handOffset + 1, usedTrackIds, assignments, totalCost)
        }

        search(handOffset = 0, usedTrackIds = emptySet(), assignments = emptyMap(), totalCost = 0f)
        return best.assignments
    }

    private fun matchCost(hand: HandGestureFrame, track: TrackedHand): Float? {
        val handLabel = hand.identityHandedness()
        val trackLabel = track.handedness
        val hasConflictingLabels = handLabel != null && trackLabel != null && !handLabel.equals(trackLabel, ignoreCase = true)
        if (hasConflictingLabels) return null

        val handX = hand.centerX
        val handY = hand.centerY
        val trackX = track.lastCenterX
        val trackY = track.lastCenterY
        val hasHandCenter = handX != null && handY != null
        val hasTrackCenter = trackX != null && trackY != null
        return if (hasHandCenter && hasTrackCenter) {
            val distance = hypot(checkNotNull(handX) - checkNotNull(trackX), checkNotNull(handY) - checkNotNull(trackY))
            distance.takeIf { value -> value <= maxMatchDistance }
        } else {
            maxMatchDistance.takeIf { handLabel != null && trackLabel != null }
        }
    }

    private fun createTrack(hand: HandGestureFrame, timestampMs: Long): TrackedHand = TrackedHand(
        trackingId = nextTrackingId++,
        engine = engineFactory(),
        lastCenterX = hand.centerX,
        lastCenterY = hand.centerY,
        handedness = hand.identityHandedness(),
        lastSeenAtMs = timestampMs,
        wasMissing = false
    ).also { track -> tracksById[track.trackingId] = track }

    private fun expireOldTracks(timestampMs: Long) {
        tracksById.values
            .filter { track -> timestampMs - track.lastSeenAtMs > maxTrackGapMs }
            .forEach { track ->
                track.engine.reset()
                tracksById.remove(track.trackingId)
            }
    }

    private fun HandGestureFrame.identityHandedness(): String? =
        handedness.takeIf { handednessScore?.let { score -> score >= minIdentityHandednessScore } == true }

    fun reset() {
        tracksById.values.forEach { track -> track.engine.reset() }
        tracksById.clear()
        nextTrackingId = 0
        lastTimestampMs = null
    }

    private data class TrackedHand(
        val trackingId: Int,
        val engine: GestureInteractionEngine,
        var lastCenterX: Float?,
        var lastCenterY: Float?,
        var handedness: String?,
        var lastSeenAtMs: Long,
        var wasMissing: Boolean
    )

    private data class MatchCandidate(val track: TrackedHand, val cost: Float)

    private data class MatchSolution(val assignments: Map<Int, TrackedHand>, val totalCost: Float)
}
