package com.example.billvision.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billvision.ml.BillDetector
import com.example.billvision.ml.BillImageAnalyzer
import com.example.billvision.ui.camera.CameraPreview
import com.example.billvision.ui.camera.CameraPreviewViewModel

class CameraActivity : ComponentActivity() {
    private lateinit var analyzer: BillImageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CameraPreviewViewModel = viewModel()

            val classifications by viewModel.classifications
                .collectAsStateWithLifecycle()
            val imageDimensions by viewModel.imageDimensions
                .collectAsStateWithLifecycle()

            val currentContext = LocalContext.current

            analyzer = remember {
                val detector = BillDetector(currentContext)
                BillImageAnalyzer(
                    detector = detector,
                    onResults = { results, size ->
                        viewModel.onAnalysisResult(results, size)
                    }
                )
            }

            DisposableEffect(Unit) {
                onDispose {
                    analyzer.close()
                }
            }

            CameraPreview(
                viewModel = viewModel,
                classifications = classifications,
                imageDimensions = imageDimensions,
                analyzer = analyzer
            )
        }
    }
}
