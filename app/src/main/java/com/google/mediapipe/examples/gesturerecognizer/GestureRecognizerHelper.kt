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
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class GestureRecognizerHelper(
    minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    currentDelegate: Int = DELEGATE_CPU,
    context: Context,
    private val gestureRecognizerListener: GestureRecognizerListener? = null
) {
    var minHandDetectionConfidence: Float = minHandDetectionConfidence
        set(value) {
            requireValidConfidence(value)
            field = value
        }
    var minHandTrackingConfidence: Float = minHandTrackingConfidence
        set(value) {
            requireValidConfidence(value)
            field = value
        }
    var minHandPresenceConfidence: Float = minHandPresenceConfidence
        set(value) {
            requireValidConfidence(value)
            field = value
        }
    var currentDelegate: Int = currentDelegate
        set(value) {
            require(value == DELEGATE_CPU || value == DELEGATE_GPU) { "Unsupported MediaPipe delegate: $value" }
            field = value
        }

    private val context = context.applicationContext
    private val isProcessingFrame = AtomicBoolean(false)
    private val recognizerGeneration = AtomicLong(0L)

    @Volatile
    private var gestureRecognizer: GestureRecognizer? = null

    init {
        requireValidConfidence(minHandDetectionConfidence)
        requireValidConfidence(minHandTrackingConfidence)
        requireValidConfidence(minHandPresenceConfidence)
        require(currentDelegate == DELEGATE_CPU || currentDelegate == DELEGATE_GPU) {
            "Unsupported MediaPipe delegate: $currentDelegate"
        }
    }

    @Synchronized
    fun clearGestureRecognizer() {
        recognizerGeneration.incrementAndGet()
        val recognizerToClose = gestureRecognizer
        gestureRecognizer = null
        isProcessingFrame.set(false)
        try {
            recognizerToClose?.close()
        } catch (error: RuntimeException) {
            Log.e(TAG, "MediaPipe task failed to close cleanly.", error)
        }
    }

    // Initialize the gesture recognizer using current settings on the
    // thread that is using it. CPU can be used with recognizers
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the recognizer
    @Synchronized
    fun setupGestureRecognizer() {
        clearGestureRecognizer()
        val generation = recognizerGeneration.get()
        val delegate = currentDelegate

        // Set general recognition options, including number of used threads
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (delegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_RECOGNIZER_TASK)

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(MAX_NUM_HANDS)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, input -> returnLivestreamResult(result, input, generation) }
                    .setErrorListener { error -> returnLivestreamError(error, generation) }
            val options = optionsBuilder.build()
            gestureRecognizer =
                GestureRecognizer.createFromOptions(context, options)
        } catch (error: IllegalStateException) {
            gestureRecognizerListener?.onError(
                "Gesture recognizer failed to initialize. See error logs for details.",
                if (delegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            )
            Log.e(TAG, "MediaPipe task failed to load.", error)
        } catch (error: RuntimeException) {
            gestureRecognizerListener?.onError(
                "Gesture recognizer failed to initialize. See error logs for details.",
                if (delegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            )
            Log.e(TAG, "MediaPipe task failed to initialize.", error)
        }
    }

    // Convert the ImageProxy to MP Image and feed it to GestureRecognizer.
    fun recognizeLiveStream(imageProxy: ImageProxy) {
        val recognizer = gestureRecognizer
        if (recognizer == null || !isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        var submitted = false
        var inputImage: MPImage? = null
        var rotatedBitmap: Bitmap? = null

        try {
            var bitmapBuffer: Bitmap? = null
            try {
                val buffer = createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
                bitmapBuffer = buffer
                buffer.copyPixelsFromBuffer(imageProxy.planes.first().buffer)
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                    postScale(-1f, 1f, imageWidth.toFloat(), imageHeight.toFloat())
                }
                rotatedBitmap = Bitmap.createBitmap(
                    buffer,
                    0,
                    0,
                    buffer.width,
                    buffer.height,
                    matrix,
                    true
                )
            } finally {
                imageProxy.close()
                if (bitmapBuffer !== rotatedBitmap) {
                    bitmapBuffer?.recycle()
                }
            }

            val mpImage = BitmapImageBuilder(checkNotNull(rotatedBitmap)).build()
            inputImage = mpImage
            try {
                recognizer.recognizeAsync(mpImage, frameTime)
                submitted = true
            } finally {
                mpImage.close()
                inputImage = null
                rotatedBitmap = null
            }
        } catch (error: RuntimeException) {
            inputImage?.close()
            rotatedBitmap?.recycle()
            gestureRecognizerListener?.onError(error.message ?: "Gesture recognition failed.")
            Log.e(TAG, "Failed to submit a camera frame to MediaPipe.", error)
        } finally {
            if (!submitted) {
                isProcessingFrame.set(false)
            }
        }
    }

    // Return running status of the recognizer helper
    fun isClosed(): Boolean = gestureRecognizer == null

    fun currentGeneration(): Long = recognizerGeneration.get()

    // Return the recognition result to the GestureRecognizerHelper's caller
    private fun returnLivestreamResult(result: GestureRecognizerResult, input: MPImage, generation: Long) {
        try {
            val finishTimeMs = SystemClock.uptimeMillis()
            val inferenceTime = finishTimeMs - result.timestampMs()

            gestureRecognizerListener?.onResults(
                ResultBundle(
                    result,
                    inferenceTime,
                    input.height,
                    input.width,
                    generation
                )
            )
        } finally {
            input.close()
            if (generation == recognizerGeneration.get()) {
                isProcessingFrame.set(false)
            }
        }
    }

    // Return errors thrown during recognition to this GestureRecognizerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException, generation: Long) {
        if (generation == recognizerGeneration.get()) {
            isProcessingFrame.set(false)
            gestureRecognizerListener?.onError(error.message ?: "An unknown recognition error occurred.")
        }
    }

    companion object {
        private const val TAG = "GestureRecognizerHelper"
        private const val MP_RECOGNIZER_TASK = "gesture_recognizer.task"
        private const val MAX_NUM_HANDS = 2

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val result: GestureRecognizerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val recognizerGeneration: Long
    )

    interface GestureRecognizerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }

    private fun requireValidConfidence(confidence: Float) {
        require(confidence.isFinite() && confidence in 0f..1f) { "Confidence threshold must be between 0 and 1." }
    }
}
