package com.example.billvision

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.billvision.activity.CameraActivity
import com.example.billvision.ui.main.MainScreen
import com.example.billvision.ui.main.PermissionScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

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
