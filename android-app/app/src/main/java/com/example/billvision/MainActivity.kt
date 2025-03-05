package com.example.billvision

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PermissionScreen() {
                startActivity(Intent(this, CameraActivity::class.java))
            }
        }
        startActivity(Intent(this, CameraActivity::class.java))
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
                modifier = modifier.fillMaxSize().wrapContentSize().widthIn(max = 480.dp),
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
}

