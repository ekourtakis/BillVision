package com.example.billvision.ui.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.billvision.ml.BillImageAnalyzer
import com.example.billvision.model.BillInference
import com.example.billvision.model.ImageDimensions
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CameraPreviewViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val _classifications = MutableStateFlow<List<BillInference>>(emptyList())
    val classifications: StateFlow<List<BillInference>> = _classifications

    private val _imageSize = MutableStateFlow(ImageDimensions.ZERO)
    val imageDimensions: StateFlow<ImageDimensions> = _imageSize

    private val imageCapture: ImageCapture by lazy {
        ImageCapture.Builder().build()
    }

    private val imageAnalyzerUseCase: ImageAnalysis by lazy {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    private val cameraPreviewUseCase: Preview by lazy {
        Preview.Builder().build().apply {
            setSurfaceProvider { newSurfaceRequest ->
                _surfaceRequest.update { newSurfaceRequest }
            }
        }
    }

    fun onAnalysisResult(results: List<BillInference>, imageDimensions: ImageDimensions) {
        _classifications.value = results

        if (imageDimensions.width > 0 && imageDimensions.height > 0) {
            _imageSize.value = imageDimensions
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
            processCameraProvider.unbindAll()

            processCameraProvider.bindToLifecycle(
                lifecycleOwner,
                DEFAULT_BACK_CAMERA,
                cameraPreviewUseCase,
                imageCapture,
                imageAnalyzerUseCase
            )
            Log.d("CameraViewModel", "Camera use cases bound successfully.")

        } catch (e: Exception) {
            Log.e("CameraViewModel", "Use case binding failed: ${e.message}", e)
        }

        try { awaitCancellation() }
        finally {
            Log.d("CameraViewModel", "awaitCancellation ended, unbinding camera use cases.")
            processCameraProvider.unbindAll()
        }
    }
}

