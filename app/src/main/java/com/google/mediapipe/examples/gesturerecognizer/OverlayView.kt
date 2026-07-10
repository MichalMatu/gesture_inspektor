/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.max

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var results: GestureRecognizerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = ContextCompat.getColor(context, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { gestureRecognizerResult ->
            gestureRecognizerResult.landmarks().forEach { landmarks -> drawHand(canvas, landmarks) }
        }
    }

    private fun drawHand(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        landmarks
            .filter { landmark -> landmark.isDrawable() }
            .forEach { landmark ->
                canvas.drawPoint(
                    landmark.x() * imageWidth * scaleFactor,
                    landmark.y() * imageHeight * scaleFactor,
                    pointPaint
                )
            }
        HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
            val start = connection?.start() ?: return@forEach
            drawConnection(canvas, landmarks.getOrNull(start), landmarks.getOrNull(connection.end()))
        }
    }

    private fun drawConnection(canvas: Canvas, start: NormalizedLandmark?, end: NormalizedLandmark?) {
        if (start?.isDrawable() != true || end?.isDrawable() != true) return

        canvas.drawLine(
            start.x() * imageWidth * scaleFactor,
            start.y() * imageHeight * scaleFactor,
            end.x() * imageWidth * scaleFactor,
            end.y() * imageHeight * scaleFactor,
            linePaint
        )
    }

    private fun NormalizedLandmark.isDrawable(): Boolean = x().isFinite() && y().isFinite()

    fun setResults(gestureRecognizerResult: GestureRecognizerResult, imageHeight: Int, imageWidth: Int) {
        require(imageHeight > 0 && imageWidth > 0) { "Input image dimensions must be positive." }
        results = gestureRecognizerResult

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // PreviewView is in FILL_START mode. Scale landmarks to the displayed camera stream.
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}
