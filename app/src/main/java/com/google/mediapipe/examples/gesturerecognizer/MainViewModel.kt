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

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private var delegate: Int = GestureRecognizerHelper.DELEGATE_CPU
    private var minHandDetectionConfidence: Float =
        GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var minHandTrackingConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_TRACKING_CONFIDENCE
    private var minHandPresenceConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_PRESENCE_CONFIDENCE
    val currentDelegate: Int get() = delegate
    val currentMinHandDetectionConfidence: Float
        get() =
            minHandDetectionConfidence
    val currentMinHandTrackingConfidence: Float
        get() =
            minHandTrackingConfidence
    val currentMinHandPresenceConfidence: Float
        get() =
            minHandPresenceConfidence

    fun setDelegate(delegate: Int) {
        this.delegate = delegate
    }

    fun setMinHandDetectionConfidence(confidence: Float) {
        minHandDetectionConfidence = confidence
    }

    fun setMinHandTrackingConfidence(confidence: Float) {
        minHandTrackingConfidence = confidence
    }

    fun setMinHandPresenceConfidence(confidence: Float) {
        minHandPresenceConfidence = confidence
    }
}
