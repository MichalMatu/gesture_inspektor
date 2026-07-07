package com.google.mediapipe.examples.gesturerecognizer.dj

import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

data class GestureFrame(
    val name: String,
    val score: Float,
    val timestampMs: Long,
    val handCount: Int,
    val centerX: Float?,
    val centerY: Float?,
) {
    val displayName: String
        get() = name.replace('_', ' ')

    companion object {
        const val NONE = "None"

        fun fromResult(result: GestureRecognizerResult): GestureFrame {
            val category = result.gestures().firstOrNull()?.firstOrNull()
            val allLandmarks = result.landmarks()
            val landmarks = allLandmarks.firstOrNull()
            val center = landmarks?.takeIf { it.isNotEmpty() }?.let { points ->
                val x = points.sumOf { it.x().toDouble() }.toFloat() / points.size
                val y = points.sumOf { it.y().toDouble() }.toFloat() / points.size
                x to y
            }

            return GestureFrame(
                name = category?.categoryName() ?: NONE,
                score = category?.score() ?: 0f,
                timestampMs = result.timestampMs(),
                handCount = allLandmarks.size,
                centerX = center?.first,
                centerY = center?.second,
            )
        }
    }
}
