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
package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.examples.gesturerecognizer.MainViewModel
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.control.GestureController
import com.google.mediapipe.examples.gesturerecognizer.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureFrameSet
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorFormatter
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorLogLevel
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorLogReducer
import com.google.mediapipe.examples.gesturerecognizer.gesture.GestureInspectorSnapshot
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "Hand gesture recognizer"
private const val INSPECTOR_LOG_TAG = "GestureInspector"
private const val MIN_CONFIDENCE = 0.10f
private const val MAX_CONFIDENCE = 0.90f
private const val CONFIDENCE_STEP = 0.10f

class CameraFragment :
    Fragment(),
    GestureRecognizerHelper.GestureRecognizerListener {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    @Volatile
    private var gestureRecognizerHelper: GestureRecognizerHelper? = null

    @Volatile
    private var acceptsRecognitionResults = false
    private lateinit var gestureController: GestureController
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraFacing = CameraSelector.LENS_FACING_FRONT
    private var orientationEventListener: OrientationEventListener? = null
    private var targetRotation = Surface.ROTATION_0

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var diagnosticsSheetBehavior: BottomSheetBehavior<View>
    private val inspectorLogReducer = GestureInspectorLogReducer()

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            requireActivity()
                .findNavController(R.id.fragment_container)
                .navigate(R.id.action_camera_to_permissions)
            return
        }

        acceptsRecognitionResults = true
        _fragmentCameraBinding?.let { binding ->
            targetRotation = binding.viewFinder.display?.rotation ?: targetRotation
            binding.updateTargetRotation(preview, imageAnalyzer, targetRotation)
        }
        orientationEventListener?.enable()
        // Start the GestureRecognizerHelper again when users come back
        // to the foreground.
        if (this::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
            backgroundExecutor.execute {
                gestureRecognizerHelper?.let { helper ->
                    if (helper.isClosed()) {
                        helper.setupGestureRecognizer()
                    }
                }
            }
        }
    }

    override fun onPause() {
        acceptsRecognitionResults = false
        orientationEventListener?.disable()
        gestureController.reset()
        inspectorLogReducer.reset()
        _fragmentCameraBinding?.overlay?.clear()

        super.onPause()
        gestureRecognizerHelper?.let { helper ->
            viewModel.setMinHandDetectionConfidence(helper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(helper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(helper.minHandPresenceConfidence)
            viewModel.setDelegate(helper.currentDelegate)

            // Close the Gesture Recognizer helper and release resources
            if (!backgroundExecutor.isShutdown) {
                backgroundExecutor.execute { helper.clearGestureRecognizer() }
            }
        }
    }

    override fun onDestroyView() {
        acceptsRecognitionResults = false
        imageAnalyzer?.clearAnalyzer()
        cameraProvider?.unbindAll()
        val helper = gestureRecognizerHelper
        gestureRecognizerHelper = null
        orientationEventListener?.disable()
        orientationEventListener = null
        if (this::backgroundExecutor.isInitialized && !backgroundExecutor.isShutdown) {
            if (helper != null) {
                backgroundExecutor.execute { helper.clearGestureRecognizer() }
            }
            backgroundExecutor.shutdown()
        }
        _fragmentCameraBinding = null
        super.onDestroyView()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gestureController = GestureController()

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()
        fragmentCameraBinding.setRecognizerControlsEnabled(false)
        targetRotation = fragmentCameraBinding.viewFinder.display?.rotation ?: Surface.ROTATION_0
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                orientation.toSurfaceRotation()?.let { rotation ->
                    if (rotation != targetRotation) {
                        targetRotation = rotation
                        _fragmentCameraBinding?.updateTargetRotation(preview, imageAnalyzer, rotation)
                    }
                }
            }
        }

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the Hand Gesture Recognition Helper that will handle the
        // inference
        val applicationContext = requireContext().applicationContext
        backgroundExecutor.execute {
            val helper = GestureRecognizerHelper(
                context = applicationContext,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                gestureRecognizerListener = this
            )
            gestureRecognizerHelper = helper
            if (acceptsRecognitionResults) {
                helper.setupGestureRecognizer()
            }
            activity?.runOnUiThread {
                _fragmentCameraBinding?.setRecognizerControlsEnabled(true)
            }
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
        initDiagnosticsSheet()
        updateInspectorStatus(
            snapshot = gestureController.handle(GestureFrameSet.empty()),
            inferenceTimeMs = 0L
        )
    }

    private fun initDiagnosticsSheet() {
        diagnosticsSheetBehavior =
            BottomSheetBehavior.from(fragmentCameraBinding.bottomSheetLayout.root)
        diagnosticsSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        fragmentCameraBinding.diagnosticsToggle.setOnClickListener {
            diagnosticsSheetBehavior.state =
                if (diagnosticsSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    BottomSheetBehavior.STATE_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_HIDDEN
                }
        }
    }

    private fun initBottomSheetControls() {
        initThresholdLabels()
        fragmentCameraBinding.bindThresholdControls(
            helperProvider = { gestureRecognizerHelper },
            onChanged = ::updateControlsUi
        )
        initDelegateSpinner()
    }

    private fun initThresholdLabels() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinHandDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinHandTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinHandPresenceConfidence
            )
    }

    private fun initDelegateSpinner() {
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    gestureRecognizerHelper?.let { helper ->
                        if (helper.currentDelegate != p2) {
                            helper.currentDelegate = p2
                            updateControlsUi(helper)
                        }
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    private fun updateControlsUi(helper: GestureRecognizerHelper) {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                helper.minHandDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                helper.minHandTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                helper.minHandPresenceConfidence
            )

        fragmentCameraBinding.setRecognizerControlsEnabled(false)
        acceptsRecognitionResults = false
        gestureController.reset()
        inspectorLogReducer.reset()
        fragmentCameraBinding.overlay.clear()
        if (!backgroundExecutor.isShutdown) {
            backgroundExecutor.execute {
                helper.setupGestureRecognizer()
                activity?.runOnUiThread {
                    if (_fragmentCameraBinding != null && gestureRecognizerHelper === helper) {
                        fragmentCameraBinding.setRecognizerControlsEnabled(true)
                        val viewLifecycle = viewLifecycleOwnerLiveData.value?.lifecycle
                        acceptsRecognitionResults = viewLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
                    }
                }
            }
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val context = context ?: return
        if (!PermissionsFragment.hasPermissions(context)) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                if (_fragmentCameraBinding == null || !isAdded) return@addListener
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases()
                } catch (error: ExecutionException) {
                    showCameraError("Camera initialization failed.", error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    showCameraError("Camera initialization was interrupted.", error)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError", "MissingPermission")
    private fun bindCameraUseCases() {
        // CameraProvider
        val cameraProvider = checkNotNull(cameraProvider) { "Camera initialization failed." }

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        try {
            if (!cameraProvider.hasCamera(cameraSelector)) {
                showCameraError("A front-facing camera is required.")
                return
            }
        } catch (error: CameraInfoUnavailableException) {
            showCameraError("Camera information is unavailable.", error)
            return
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()

        // Prefer 4:3 because it is closest to the gesture model input shape.
        preview = Preview.Builder().setResolutionSelector(resolutionSelector)
            .setTargetRotation(targetRotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setResolutionSelector(resolutionSelector)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        recognizeHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            val useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(checkNotNull(preview))
                .addUseCase(checkNotNull(imageAnalyzer))
            fragmentCameraBinding.viewFinder.viewPort?.let(useCaseGroupBuilder::setViewPort)
            cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, useCaseGroupBuilder.build())

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: RuntimeException) {
            showCameraError("Camera use-case binding failed.", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        val helper = gestureRecognizerHelper
        if (!acceptsRecognitionResults || helper == null || helper.isClosed()) {
            imageProxy.close()
            return
        }
        helper.recognizeLiveStream(imageProxy)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _fragmentCameraBinding?.let { binding ->
            targetRotation = binding.viewFinder.display?.rotation ?: targetRotation
            binding.updateTargetRotation(preview, imageAnalyzer, targetRotation)
        }
    }

    // Update UI after a hand gesture has been recognized. Extracts original
    // image height/width to scale and place landmarks through OverlayView.
    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        val helper = gestureRecognizerHelper ?: return
        if (!acceptsRecognitionResults || resultBundle.recognizerGeneration != helper.currentGeneration()) return

        activity?.runOnUiThread {
            val viewLifecycle = viewLifecycleOwnerLiveData.value?.lifecycle
            if (acceptsRecognitionResults &&
                resultBundle.recognizerGeneration == gestureRecognizerHelper?.currentGeneration() &&
                viewLifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
            ) {
                // Show result of recognized gesture
                val recognizerResult = resultBundle.result

                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format(Locale.US, "%d ms", resultBundle.inferenceTime)

                updateInspectorStatus(
                    snapshot = gestureController.handle(
                        GestureFrameSet.fromResult(recognizerResult)
                    ),
                    inferenceTimeMs = resultBundle.inferenceTime
                )

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    recognizerResult,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    private fun updateInspectorStatus(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long) {
        val display = GestureInspectorFormatter.format(
            snapshot = snapshot,
            inferenceTimeMs = inferenceTimeMs
        )

        fragmentCameraBinding.inspectorSummaryStatus.text = display.summary
        fragmentCameraBinding.inspectorActionStatus.text = display.matchedAction
        fragmentCameraBinding.inspectorHandStatus.text = display.handDetails
        logInspectorDiagnostics(snapshot, inferenceTimeMs)
    }

    private fun logInspectorDiagnostics(snapshot: GestureInspectorSnapshot, inferenceTimeMs: Long) {
        val verbose = Log.isLoggable(INSPECTOR_LOG_TAG, Log.VERBOSE)
        inspectorLogReducer
            .reduce(
                snapshot = snapshot,
                inferenceTimeMs = inferenceTimeMs,
                nowMs = SystemClock.elapsedRealtime(),
                verbose = verbose
            )
            .forEach { line ->
                when (line.level) {
                    GestureInspectorLogLevel.Info -> Log.i(INSPECTOR_LOG_TAG, line.message)
                    GestureInspectorLogLevel.Verbose -> Log.v(INSPECTOR_LOG_TAG, line.message)
                }
            }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            val binding = _fragmentCameraBinding ?: return@runOnUiThread
            val currentContext = context ?: return@runOnUiThread
            Toast.makeText(currentContext, error, Toast.LENGTH_SHORT).show()

            if (errorCode == GestureRecognizerHelper.GPU_ERROR) {
                val helper = gestureRecognizerHelper
                if (helper != null && helper.currentDelegate != GestureRecognizerHelper.DELEGATE_CPU) {
                    helper.currentDelegate = GestureRecognizerHelper.DELEGATE_CPU
                    binding.bottomSheetLayout.spinnerDelegate.setSelection(
                        GestureRecognizerHelper.DELEGATE_CPU,
                        false
                    )
                    updateControlsUi(helper)
                    return@runOnUiThread
                }
                binding.bottomSheetLayout.spinnerDelegate.setSelection(
                    GestureRecognizerHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    private fun showCameraError(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
        val binding = _fragmentCameraBinding ?: return
        binding.inspectorSummaryStatus.text = message
        binding.inspectorActionStatus.text = getString(R.string.label_camera_unavailable)
        binding.inspectorHandStatus.text = getString(R.string.label_camera_retry_hint)
        context?.let { currentContext -> Toast.makeText(currentContext, message, Toast.LENGTH_LONG).show() }
    }
}

private fun FragmentCameraBinding.setRecognizerControlsEnabled(enabled: Boolean) {
    val controls = bottomSheetLayout
    controls.detectionThresholdMinus.isEnabled = enabled
    controls.detectionThresholdPlus.isEnabled = enabled
    controls.trackingThresholdMinus.isEnabled = enabled
    controls.trackingThresholdPlus.isEnabled = enabled
    controls.presenceThresholdMinus.isEnabled = enabled
    controls.presenceThresholdPlus.isEnabled = enabled
    controls.spinnerDelegate.isEnabled = enabled
}

private fun FragmentCameraBinding.updateTargetRotation(preview: Preview?, imageAnalyzer: ImageAnalysis?, rotation: Int) {
    if (preview?.targetRotation != rotation) {
        preview?.targetRotation = rotation
    }
    if (imageAnalyzer?.targetRotation != rotation) {
        imageAnalyzer?.targetRotation = rotation
    }
}

private fun Int.toSurfaceRotation(): Int? = when {
    this == OrientationEventListener.ORIENTATION_UNKNOWN -> null
    this in 45 until 135 -> Surface.ROTATION_270
    this in 135 until 225 -> Surface.ROTATION_180
    this in 225 until 315 -> Surface.ROTATION_90
    else -> Surface.ROTATION_0
}

private fun FragmentCameraBinding.bindThresholdControls(
    helperProvider: () -> GestureRecognizerHelper?,
    onChanged: (GestureRecognizerHelper) -> Unit
) {
    val controls = bottomSheetLayout
    listOf(
        ConfidenceControl(
            controls.detectionThresholdMinus,
            controls.detectionThresholdPlus,
            { helper -> helper.minHandDetectionConfidence },
            { helper, value -> helper.minHandDetectionConfidence = value }
        ),
        ConfidenceControl(
            controls.trackingThresholdMinus,
            controls.trackingThresholdPlus,
            { helper -> helper.minHandTrackingConfidence },
            { helper, value -> helper.minHandTrackingConfidence = value }
        ),
        ConfidenceControl(
            controls.presenceThresholdMinus,
            controls.presenceThresholdPlus,
            { helper -> helper.minHandPresenceConfidence },
            { helper, value -> helper.minHandPresenceConfidence = value }
        )
    ).forEach { control -> control.bind(helperProvider, onChanged) }
}

private fun ConfidenceControl.bind(helperProvider: () -> GestureRecognizerHelper?, onChanged: (GestureRecognizerHelper) -> Unit) {
    decreaseButton.setOnClickListener { adjust(helperProvider, -CONFIDENCE_STEP, onChanged) }
    increaseButton.setOnClickListener { adjust(helperProvider, CONFIDENCE_STEP, onChanged) }
}

private fun ConfidenceControl.adjust(
    helperProvider: () -> GestureRecognizerHelper?,
    delta: Float,
    onChanged: (GestureRecognizerHelper) -> Unit
) {
    val helper = helperProvider() ?: return
    val currentValue = read(helper)
    val value = (currentValue + delta).coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
    if (value != currentValue) {
        write(helper, value)
        onChanged(helper)
    }
}

private data class ConfidenceControl(
    val decreaseButton: View,
    val increaseButton: View,
    val read: (GestureRecognizerHelper) -> Float,
    val write: (GestureRecognizerHelper, Float) -> Unit
)
