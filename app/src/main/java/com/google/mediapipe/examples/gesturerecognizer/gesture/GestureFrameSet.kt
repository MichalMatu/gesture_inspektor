package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

data class GestureCandidate(val name: String, val score: Float, val rank: Int) {
    init {
        require(name.isNotBlank()) { "Gesture candidate name must not be blank." }
        require(score.isFinite() && score in 0f..1f) { "Gesture candidate score must be between 0 and 1." }
        require(rank > 0) { "Gesture candidate rank must be positive." }
    }

    val displayName: String
        get() = name.replace('_', ' ')
}

data class HandGestureFrame(
    val detectionIndex: Int,
    val handedness: String?,
    val handednessScore: Float?,
    val candidates: List<GestureCandidate>,
    val centerX: Float?,
    val centerY: Float?,
    val landmarkCount: Int,
    val landmarkEstimate: LandmarkGestureEstimate? = null
) {
    init {
        require(detectionIndex >= 0) { "Hand detection index must not be negative." }
        require(handedness == null || handedness.isNotBlank()) { "Handedness label must not be blank." }
        require(handednessScore == null || (handednessScore.isFinite() && handednessScore in 0f..1f)) {
            "Handedness score must be between 0 and 1."
        }
        require(centerX == null || (centerX.isFinite() && centerX in 0f..1f)) { "Hand center X must be between 0 and 1." }
        require(centerY == null || (centerY.isFinite() && centerY in 0f..1f)) { "Hand center Y must be between 0 and 1." }
        require(landmarkCount >= 0) { "Landmark count must not be negative." }
        require(landmarkEstimate == null || landmarkCount >= 21) {
            "A landmark gesture estimate requires a complete hand landmark set."
        }
        require((centerX == null) == (centerY == null)) { "Hand center coordinates must both be present or both be absent." }
        require(candidates.map { candidate -> candidate.rank } == (1..candidates.size).toList()) {
            "Gesture candidate ranks must be sequential and start at 1."
        }
        require(candidates.zipWithNext().all { (first, second) -> first.score >= second.score }) {
            "Gesture candidates must be sorted by descending score."
        }
    }

    val bestCandidate: GestureCandidate?
        get() = candidates.firstOrNull()

    val name: String
        get() = landmarkEstimate?.resolvedName ?: bestCandidate?.name ?: GestureFrameSet.NONE

    val score: Float
        get() = if (landmarkEstimate?.resolvedName != null) landmarkEstimate.score else bestCandidate?.score ?: 0f

    val displayName: String
        get() = name.replace('_', ' ')

    val classificationSource: String
        get() = when {
            landmarkEstimate?.resolvedName != null -> "landmarks"
            bestCandidate != null -> "model"
            else -> "none"
        }
}

data class GestureFrameSet(val timestampMs: Long, val hands: List<HandGestureFrame>) {
    init {
        require(timestampMs >= 0L) { "Frame timestamp must not be negative." }
        require(hands.map { hand -> hand.detectionIndex }.distinct().size == hands.size) {
            "Hand detection indexes must be unique within a frame."
        }
    }

    val handCount: Int
        get() = hands.size

    companion object {
        const val NONE = "None"

        fun empty(timestampMs: Long = 0L): GestureFrameSet = GestureFrameSet(
            timestampMs = timestampMs,
            hands = emptyList()
        )

        fun fromResult(result: GestureRecognizerResult): GestureFrameSet {
            val landmarksByHand = result.landmarks()
            val worldLandmarksByHand = result.worldLandmarks()
            val gesturesByHand = result.gestures()
            val handednessByHand = result.handedness()

            val hands = landmarksByHand.mapIndexedNotNull { handIndex, landmarks ->
                if (landmarks.isEmpty()) return@mapIndexedNotNull null

                val center = landmarks.center()
                val handedness = handednessByHand
                    .getOrNull(handIndex)
                    .orEmpty()
                    .filter { category ->
                        category.categoryName().isNotBlank() && category.score().isFinite() && category.score() in 0f..1f
                    }
                    .maxByOrNull { category -> category.score() }

                HandGestureFrame(
                    detectionIndex = handIndex,
                    handedness = handedness?.categoryName(),
                    handednessScore = handedness?.score(),
                    candidates = gesturesByHand
                        .getOrNull(handIndex)
                        .orEmpty()
                        .toCandidates(),
                    centerX = center?.first,
                    centerY = center?.second,
                    landmarkCount = landmarks.size,
                    landmarkEstimate = worldLandmarksByHand
                        .getOrNull(handIndex)
                        .orEmpty()
                        .toGestureLandmarks()
                        .let(LandmarkGestureClassifier::estimate)
                )
            }

            return GestureFrameSet(
                timestampMs = result.timestampMs(),
                hands = hands
            )
        }

        private fun List<Category>.toCandidates(): List<GestureCandidate> = asSequence()
            .filter { category ->
                category.categoryName().isNotBlank() && category.score().isFinite() && category.score() in 0f..1f
            }
            .sortedByDescending { category -> category.score() }
            .mapIndexed { index, category ->
                GestureCandidate(
                    name = category.categoryName(),
                    score = category.score(),
                    rank = index + 1
                )
            }
            .toList()

        private fun List<NormalizedLandmark>.center(): Pair<Float, Float>? {
            if (isEmpty() || any { landmark -> !landmark.hasFiniteCoordinates() }) {
                return null
            }

            val centerLandmarks = if (size > PALM_LANDMARK_INDEXES.last()) {
                PALM_LANDMARK_INDEXES.map(::get)
            } else {
                this
            }
            val x = centerLandmarks.sumOf { landmark -> landmark.x().toDouble() }.toFloat() / centerLandmarks.size
            val y = centerLandmarks.sumOf { landmark -> landmark.y().toDouble() }.toFloat() / centerLandmarks.size
            return x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
        }

        private fun NormalizedLandmark.hasFiniteCoordinates(): Boolean = x().isFinite() && y().isFinite() && z().isFinite()

        private fun List<Landmark>.toGestureLandmarks(): List<GestureLandmark3D> = map { landmark ->
            GestureLandmark3D(landmark.x(), landmark.y(), landmark.z())
        }

        private val PALM_LANDMARK_INDEXES = listOf(0, 5, 9, 13, 17)
    }
}
