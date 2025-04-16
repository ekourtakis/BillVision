package com.example.billvision.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billvision.data.BillImageAnalyzer
import com.example.billvision.data.model.BillInference
import com.example.billvision.MainActivity
import com.example.billvision.data.BillDetector
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = remember { CameraPreviewViewModel() }
            CameraPreview(viewModel) { photoPath ->
                val intent = Intent().apply {
                    putExtra(MainActivity.Companion.EXTRA_PHOTO_PATH, photoPath)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }
}

@Composable
fun CameraPreview(
    viewModel: CameraPreviewViewModel,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onPhotoTaken: (String) -> Unit
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val detector = remember { BillDetector(context) }
    val analyzer = remember(detector) { // analyzer depends on detector
        BillImageAnalyzer(
            detector = detector,
            onResults = { detectedBills ->
                Log.d(
                    "CameraActivity",
                    "onResults called with ${detectedBills.size} detections: " +
                            detectedBills.joinToString { it.name + "("+String.format("%.2f", it.confidence)+")" }
                )
            }
        )
    }

    var classifications by remember {
        mutableStateOf(emptyList<BillInference>())
    }

    val updatedAnalyzer = remember(detector) {
        BillImageAnalyzer(
            detector = detector,
            onResults = { detectedBills ->
                Log.d(
                    "CameraActivity",
                    "onResults (Updated Lambda) called with ${detectedBills.size} detections: " +
                            detectedBills.joinToString { it.name + "("+String.format("%.2f", it.confidence)+")" }
                )
                classifications = detectedBills
            }
        )
    }


    DisposableEffect(Unit) {
        onDispose {
            Log.d("CameraPreview", "DisposableEffect: Closing analyzer and detector.")
            analyzer.close()
            detector.close()
        }
    }

    LaunchedEffect(lifecycleOwner, updatedAnalyzer) { // Depend on updatedAnalyzer
        Log.d("CameraPreview", "LaunchedEffect: Binding camera.")
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner, updatedAnalyzer) // Use updatedAnalyzer
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize() // Ensure viewfinder fills the Box
            )
        }

        // Column for displaying classifications (Top Center)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 16.dp) // Add some padding from the top edge
        ) {
            // Take top 3 results, or fewer if less than 3 are found
            val resultsToShow = classifications.sortedByDescending { it.confidence }.take(3)
            resultsToShow.forEach {
                Text(
                    // Format confidence as percentage
                    text = "${it.name}: ${String.format("%.1f", it.confidence * 100)}%",
                    modifier = Modifier
                        .fillMaxWidth()
                        // Use a semi-transparent background for better readability over the camera feed
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // Optional: Show a placeholder if no bills are detected
            if (classifications.isEmpty()) {
                Text(
                    text = "Point camera at a bill...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Button (Bottom Center)
        Button(
            onClick = {
                viewModel.takePhoto(context) { photoFile ->
                    onPhotoTaken(photoFile.absolutePath)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Text("Take Photo")
        }
    }
}

class CameraPreviewViewModel : ViewModel() {
    // used to set up a link between the Camera and your UI.
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val imageCapture = ImageCapture.Builder().build()

    private val imageAnalyzerUseCase = ImageAnalysis.Builder().build()

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        analyzer: BillImageAnalyzer
    ) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        imageAnalyzerUseCase.setAnalyzer(
            ContextCompat.getMainExecutor(appContext),
            analyzer
        )

        try {
            processCameraProvider.unbindAll() // clean slate

            processCameraProvider.bindToLifecycle(
                lifecycleOwner,
                DEFAULT_BACK_CAMERA,
                cameraPreviewUseCase,
                imageCapture,
                imageAnalyzerUseCase
            )
        } catch (e: Exception) {
            Log.e("CameraViewModel", "use case binding failed: ${e.message}", e)
        }

        // Cancellation signals we're done with the camera
        try { awaitCancellation() } finally { processCameraProvider.unbindAll() }
    }

    fun takePhoto(context: Context, onPhotoTaken: (File) -> Unit) {
        val photoFile = File(
            context.getExternalFilesDir(null),
            "BillVision_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onPhotoTaken(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle error here
                }
            }
        )
    }
}