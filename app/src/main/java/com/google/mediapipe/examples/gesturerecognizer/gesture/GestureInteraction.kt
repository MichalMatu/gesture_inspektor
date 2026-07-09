package com.google.mediapipe.examples.gesturerecognizer.gesture

import kotlin.math.abs

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
    val handIndex: Int
        get() = frame.handIndex

    val gestureName: String
        get() = frame.name

    val score: Float
        get() = frame.score

    val isStable: Boolean
        get() = stableFrames > 0
}

class GestureInteractionEngine(
    private val minTrackedScore: Float = 0.60f,
    private val movementDeadZone: Float = 0.035f,
    private val smoothingAlpha: Float = 0.25f
) {
    private var activeGestureName: String? = null
    private var stableFrames = 0
    private var holdStartedAtMs = 0L
    private var smoothedCenterX: Float? = null
    private var smoothedCenterY: Float? = null
    private var hasMovedDuringHold = false
    private var lostLandmarkFrames = 0

    fun update(frame: HandGestureFrame, timestampMs: Long): GestureInteraction = when {
        frame.name == GestureFrameSet.NONE || frame.score < minTrackedScore -> updateUntrackedFrame(frame, timestampMs)
        activeGestureName != frame.name -> startGestureHold(frame, timestampMs)
        else -> continueGestureHold(frame, timestampMs)
    }

    private fun updateUntrackedFrame(frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        reset()
        return frame.toInteraction(
            timestampMs = timestampMs,
            stableFrames = 0,
            holdDurationMs = 0L,
            deltaX = 0f,
            deltaY = 0f,
            movementDirection = MovementDirection.Still,
            isTrackingReliable = false
        )
    }

    private fun startGestureHold(frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        activeGestureName = frame.name
        stableFrames = 1
        holdStartedAtMs = timestampMs
        smoothedCenterX = frame.centerX
        smoothedCenterY = frame.centerY
        hasMovedDuringHold = false
        lostLandmarkFrames = if (frame.centerX == null || frame.centerY == null) 1 else 0

        return frame.toInteraction(
            timestampMs = timestampMs,
            stableFrames = stableFrames,
            holdDurationMs = 0L,
            deltaX = 0f,
            deltaY = 0f,
            movementDirection = MovementDirection.Still,
            isTrackingReliable = frame.hasReliableLandmarks()
        )
    }

    private fun continueGestureHold(frame: HandGestureFrame, timestampMs: Long): GestureInteraction {
        stableFrames += 1

        val (nextSmoothedCenterX, deltaX) = smoothedDelta(
            current = frame.centerX,
            previous = smoothedCenterX
        )
        val (nextSmoothedCenterY, deltaY) = smoothedDelta(
            current = frame.centerY,
            previous = smoothedCenterY
        )
        smoothedCenterX = nextSmoothedCenterX
        smoothedCenterY = nextSmoothedCenterY

        if (frame.centerX == null || frame.centerY == null) {
            lostLandmarkFrames += 1
        } else {
            lostLandmarkFrames = 0
        }

        val movementDirection = movementDirection(deltaX, deltaY)
        if (movementDirection != MovementDirection.Still) {
            hasMovedDuringHold = true
        }

        return frame.toInteraction(
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
        hasMovedDuringHold = false
        lostLandmarkFrames = 0
    }

    private fun smoothedDelta(current: Float?, previous: Float?): Pair<Float?, Float> = when {
        current == null -> null to 0f

        previous == null -> current to 0f

        else -> {
            val smoothed = previous + (current - previous) * smoothingAlpha
            smoothed to (smoothed - previous)
        }
    }

    private fun movementDirection(deltaX: Float, deltaY: Float): MovementDirection {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        if (absX < movementDeadZone && absY < movementDeadZone) {
            return MovementDirection.Still
        }

        return if (absX >= absY) {
            if (deltaX > 0f) MovementDirection.Right else MovementDirection.Left
        } else {
            if (deltaY > 0f) MovementDirection.Down else MovementDirection.Up
        }
    }

    private fun HandGestureFrame.toInteraction(
        timestampMs: Long,
        stableFrames: Int,
        holdDurationMs: Long,
        deltaX: Float,
        deltaY: Float,
        movementDirection: MovementDirection,
        isTrackingReliable: Boolean
    ): GestureInteraction = GestureInteraction(
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
        horizontalZone = smoothedCenterX?.toHorizontalZone(),
        verticalZone = smoothedCenterY?.toVerticalZone(),
        movementDirection = movementDirection,
        hasMovedDuringHold = hasMovedDuringHold,
        lostLandmarkFrames = lostLandmarkFrames,
        isTrackingReliable = isTrackingReliable
    )

    private fun HandGestureFrame.hasReliableLandmarks(): Boolean = centerX != null && centerY != null && landmarkCount > 0

    private fun Float.toHorizontalZone(): HorizontalZone = when {
        this < 0.33f -> HorizontalZone.Left
        this < 0.66f -> HorizontalZone.Center
        else -> HorizontalZone.Right
    }

    private fun Float.toVerticalZone(): VerticalZone = when {
        this < 0.33f -> VerticalZone.Top
        this < 0.66f -> VerticalZone.Middle
        else -> VerticalZone.Bottom
    }
}

class MultiHandGestureInteractionEngine(private val engineFactory: () -> GestureInteractionEngine = { GestureInteractionEngine() }) {
    private val enginesByHandIndex = mutableMapOf<Int, GestureInteractionEngine>()

    fun update(frameSet: GestureFrameSet): List<GestureInteraction> {
        if (frameSet.hands.isEmpty()) {
            reset()
            return emptyList()
        }

        val activeHandIndexes = frameSet.hands.map { hand -> hand.handIndex }.toSet()
        enginesByHandIndex.keys
            .filter { handIndex -> handIndex !in activeHandIndexes }
            .forEach { handIndex -> enginesByHandIndex.remove(handIndex)?.reset() }

        return frameSet.hands.map { hand ->
            enginesByHandIndex
                .getOrPut(hand.handIndex, engineFactory)
                .update(hand, frameSet.timestampMs)
        }
    }

    fun reset() {
        enginesByHandIndex.values.forEach { engine -> engine.reset() }
        enginesByHandIndex.clear()
    }
}
