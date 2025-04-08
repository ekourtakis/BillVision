package com.example.billvision

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.billvision.activity.CameraActivity
import com.example.billvision.activity.ResultActivity
import com.example.billvision.data.BillDetector
import com.example.billvision.data.model.BillInference
import com.example.billvision.data.model.Result
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
        const val EXTRA_RESULT = "bill_inference"
    }

    private val detector by lazy { BillDetector(this) }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val photoPath = result.data?.getStringExtra(EXTRA_PHOTO_PATH)
            if (photoPath != null) {
                val detections = detector.detectFromPhotoPath(photoPath)

                if (detections.isNotEmpty()) {
                    val bestDetection = detections.maxByOrNull {
                        it.confidence
                    } ?: detections.first()
                    val classificationResult = Result(bestDetection, photoPath)

                    val intent = Intent(this, ResultActivity::class.java).apply {
                        putExtra(EXTRA_RESULT, classificationResult)
                    }

                    resultLauncher.launch(intent)
                } else {
                    Log.w("BillVision", "No bills detected in photo: $photoPath")
                    // Handle case where nothing is detected (e.g., show a Toast or default result)
                    // For now, let's launch ResultActivity with a "not found" state
                    val notFoundResult = Result(BillInference("Not Found", 0f), photoPath)
                    val intent = Intent(this, ResultActivity::class.java).apply {
                        putExtra(EXTRA_RESULT, notFoundResult)
                    }
                    resultLauncher.launch(intent)
                }

            } else {
                Log.e("BillVision", "photoPath null")
                // TODO: add toast saying error
                launchCamera()
            }
        } else {
            Log.w("BillVision", "CameraActivity returned non-ok result: ${result.resultCode}.")
            launchCamera()
        }
    }

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "ResultActivity finish, code ${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            if (cameraPermissionGranted()) {
                MainScreen()
            } else {
                PermissionScreen { launchCamera() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isChangingConfigurations.not()) {
            detector.close()
        }
    }

    private fun launchCamera() {
        cameraLauncher.launch(Intent(this, CameraActivity::class.java))
    }

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun PermissionScreen(
        modifier: Modifier = Modifier,
        onPermissionsGranted: () -> Unit
    ) {
        val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
        if (cameraPermissionState.status.isGranted) {
            onPermissionsGranted()
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
                    // If the user has denied the permission but the rationale can be shown,
                    // explain why the app requires this permission
                    "We need permission to use your camera. Please grant it."
                } else {
                    // If it's the first time the user lands on this feature, or the user
                    // doesn't want to be asked again for this permission, explain that the
                    // permission is required
                    "Hello! We need to use your camera to read dollar bills."
                }
                Text(textToShow, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant camera permissions")
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { launchCamera() }) {
                Text("Take another photo")
            }
        }
    }

    private fun cameraPermissionGranted() : Boolean {
        return ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}

