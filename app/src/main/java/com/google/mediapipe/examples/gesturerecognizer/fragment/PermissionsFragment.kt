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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mediapipe.examples.gesturerecognizer.R

private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

class PermissionsFragment : Fragment() {
    private var navigateWhenStarted = false

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                navigateWhenStarted = true
                navigateToCamera()
            } else {
                showPermissionDeniedDialog()
            }
        }

    private val applicationSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasPermissions(requireContext())) {
                navigateWhenStarted = true
                navigateToCamera()
            } else {
                showPermissionDeniedDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) -> {
                navigateWhenStarted = true
            }

            else -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.CAMERA
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (navigateWhenStarted || hasPermissions(requireContext())) {
            navigateToCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions(requireContext())) {
            navigateWhenStarted = true
            navigateToCamera()
        }
    }

    private fun navigateToCamera() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.permissions_fragment) {
            navigateWhenStarted = false
            navController.navigate(R.id.action_permissions_to_camera)
        }
    }

    private fun showPermissionDeniedDialog() {
        if (!isAdded) return

        val canAskAgain = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_message)
            .setCancelable(false)
            .setPositiveButton(if (canAskAgain) R.string.retry else R.string.open_settings) { _, _ ->
                if (canAskAgain) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    openApplicationSettings()
                }
            }
            .setNegativeButton(R.string.close_app) { _, _ -> requireActivity().finish() }
            .show()
    }

    private fun openApplicationSettings() {
        applicationSettingsLauncher.launch(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().packageName, null)
            )
        )
    }

    companion object {

        fun hasPermissions(context: Context): Boolean = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
