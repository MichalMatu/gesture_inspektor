package com.google.mediapipe.examples.gesturerecognizer.gesture

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

data class GestureCandidate(val name: String, val score: Float, val rank: Int) {
    val displayName: String
        get() = name.replace('_', ' ')
}

data class HandGestureFrame(
    val handIndex: Int,
    val handedness: String?,
    val handednessScore: Float?,
    val candidates: List<GestureCandidate>,
    val centerX: Float?,
    val centerY: Float?,
    val landmarkCount: Int
) {
    val bestCandidate: GestureCandidate?
        get() = candidates.firstOrNull()

    val name: String
        get() = bestCandidate?.name ?: GestureFrameSet.NONE

    val score: Float
        get() = bestCandidate?.score ?: 0f
}

data class GestureFrameSet(val timestampMs: Long, val hands: List<HandGestureFrame>) {
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
            val gesturesByHand = result.gestures()
            val handednessByHand = result.handedness()
            val handCount = maxOf(
                landmarksByHand.size,
                gesturesByHand.size,
                handednessByHand.size
            )

            val hands = (0 until handCount).map { handIndex ->
                val landmarks = landmarksByHand.getOrNull(handIndex).orEmpty()
                val center = landmarks.center()
                val handedness = handednessByHand
                    .getOrNull(handIndex)
                    .orEmpty()
                    .maxByOrNull { category -> category.score() }

                HandGestureFrame(
                    handIndex = handIndex,
                    handedness = handedness?.categoryName(),
                    handednessScore = handedness?.score(),
                    candidates = gesturesByHand
                        .getOrNull(handIndex)
                        .orEmpty()
                        .toCandidates(),
                    centerX = center?.first,
                    centerY = center?.second,
                    landmarkCount = landmarks.size
                )
            }

            return GestureFrameSet(
                timestampMs = result.timestampMs(),
                hands = hands
            )
        }

        private fun List<Category>.toCandidates(): List<GestureCandidate> = sortedByDescending { category -> category.score() }
            .mapIndexed { index, category ->
                GestureCandidate(
                    name = category.categoryName(),
                    score = category.score(),
                    rank = index + 1
                )
            }

        private fun List<NormalizedLandmark>.center(): Pair<Float, Float>? {
            if (isEmpty()) return null

            val x = sumOf { landmark -> landmark.x().toDouble() }.toFloat() / size
            val y = sumOf { landmark -> landmark.y().toDouble() }.toFloat() / size
            return x to y
        }
    }
}
