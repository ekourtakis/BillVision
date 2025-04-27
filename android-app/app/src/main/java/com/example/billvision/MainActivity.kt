package com.example.billvision

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.billvision.activity.CameraActivity
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Camera result received: $result")
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val cameraPermissionState = rememberPermissionState(
                Manifest.permission.CAMERA
            )

            Scaffold { innerPadding ->
                if (cameraPermissionState.status.isGranted) {
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCameraButtonClicked = { launchCamera() }
                    )
                } else {
                    PermissionScreen (
                        modifier = Modifier.padding(innerPadding),
                        cameraPermissionState
                    )
                }
            }
        }
    }

    private fun launchCamera() {
        cameraLauncher.launch(Intent(this, CameraActivity::class.java))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    cameraPermissionState: PermissionState
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize()
            .padding(horizontal = 16.dp)
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val textToShow = if (cameraPermissionState.status.shouldShowRationale) {
            // If the user has denied the permission but the rationale can be shown,
            // explain why the app requires this permission
            "To identify dollar bills, BillVision needs permission to access your camera. Please grant the camera permission."
        } else {
            // If it's the first time the user lands on this feature, or the user
            // doesn't want to be asked again for this permission, explain that the
            // permission is required
            "Welcome to BillVision! To get started, please grant us camera permission so the app can see and identify dollar bills."
        }
        Text(textToShow, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
            Text("Grant Camera Permission")
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onCameraButtonClicked: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize()
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onCameraButtonClicked,
            modifier = Modifier.semantics {
                contentDescription = "Open the camera to identify bills"
            }
        ) {
            Text("Open Camera")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Point the camera towards a US dollar bill to identify its denomination.",
            textAlign = TextAlign.Center
        )
    }
}
