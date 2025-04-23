package com.example.billvision.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.Size
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billvision.ml.BillImageAnalyzer
import com.example.billvision.model.BillInference
import com.example.billvision.ml.BillDetector
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update // Keep
import kotlin.math.max
import kotlin.math.min

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var imageSize by remember { mutableStateOf(Size(0, 0)) }

            val viewModel = remember { CameraPreviewViewModel() }
            CameraPreview(
                viewModel = viewModel,
                onImageAnalyzed = { size -> // Callback to get image size
                    if (imageSize != size && size.width > 0 && size.height > 0) {
                        imageSize = size
                    }
                },
                imageSize = imageSize // Pass size to CameraPreview
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun CameraPreview(
    viewModel: CameraPreviewViewModel,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    imageSize: Size, // Receive analyzed image size
    onImageAnalyzed: (Size) -> Unit, // Callback to update image size
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val detector = remember { BillDetector(context) }
    var classifications by remember { mutableStateOf(emptyList<BillInference>()) }

    val analyzer = remember(detector, onImageAnalyzed) {
        BillImageAnalyzer(
            detector = detector,
            onResults = { detectedBills, analyzedSize ->
                classifications = detectedBills
                if (analyzedSize.width > 0 && analyzedSize.height > 0) {
                    onImageAnalyzed(analyzedSize) // Update the size in the parent
                }
                Log.d(
                    "CameraActivity",
                    "onResults called with ${detectedBills.size} detections for image size ${analyzedSize.width}x${analyzedSize.height}"
                )
            }
        )
    }

    DisposableEffect(analyzer, detector) {
        onDispose {
            Log.d("CameraPreview", "DisposableEffect: Closing analyzer and detector.")
            analyzer.close()
            detector.close()
        }
    }

    LaunchedEffect(lifecycleOwner, analyzer) {
        Log.d("CameraPreview", "LaunchedEffect: Binding camera.")
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner, analyzer)
    }

    // For drawing text on Canvas
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) { // Box fills the screen
        // Camera Viewfinder fills the Box
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize() // Ensure viewfinder fills the Box
            )
        }

        // Canvas also fills the Box, drawn on top of the Viewfinder
        if (imageSize.width > 0 && imageSize.height > 0 && classifications.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val imageAspectRatio = imageSize.width.toFloat() / imageSize.height.toFloat()
                val canvasAspectRatio = canvasWidth / canvasHeight

                val scaleFactor: Float
                val offsetX: Float
                val offsetY: Float

                if (imageAspectRatio > canvasAspectRatio) {
                    // Image is wider than canvas (letterboxing top/bottom)
                    scaleFactor = canvasWidth / imageSize.width.toFloat()
                    offsetX = 0f
                    offsetY = (canvasHeight - imageSize.height * scaleFactor) / 2f
                } else {
                    // Image is taller than canvas (pillar boxing left/right)
                    scaleFactor = canvasHeight / imageSize.height.toFloat()
                    offsetX = (canvasWidth - imageSize.width * scaleFactor) / 2f
                    offsetY = 0f
                }

                // Draw each bounding box and label
                classifications.forEach { inference ->
                    val box = inference.boundingBox // Coordinates are relative to original bitmap

                    // Scale and offset the coordinates
                    val canvasLeft = box.left * scaleFactor + offsetX
                    val canvasTop = box.top * scaleFactor + offsetY
                    val canvasRight = box.right * scaleFactor + offsetX
                    val canvasBottom = box.bottom * scaleFactor + offsetY

                    // Ensure coordinates are within canvas bounds (optional, prevents drawing outside)
                    if (canvasRight <= canvasLeft || canvasBottom <= canvasTop ||
                        canvasLeft >= canvasWidth || canvasTop >= canvasHeight ||
                        canvasRight <= 0 || canvasBottom <= 0) {
                        return@forEach // Skip drawing if box is invalid or fully outside
                    }

                    // Clamp coordinates to be within the visible canvas area
                    val clampedLeft = max(0f, canvasLeft)
                    val clampedTop = max(0f, canvasTop)
                    val clampedRight = min(canvasWidth, canvasRight)
                    val clampedBottom = min(canvasHeight, canvasBottom)

                    val boxWidth = clampedRight - clampedLeft
                    val boxHeight = clampedBottom - clampedTop

                    if (boxWidth <= 0 || boxHeight <= 0) return@forEach // Skip if degenerated after clamping

                    // Draw the bounding box outline
                    drawRect(
                        color = Color.Yellow, // Or assign colors based on class
                        topLeft = Offset(clampedLeft, clampedTop),
                        size = GeometrySize(boxWidth, boxHeight),
                        style = Stroke(width = 2.dp.toPx()) // Use dp for consistent stroke width
                    )

                    // Prepare text label
                    val label = "${inference.name} ${String.format("%.1f", inference.confidence * 100)}%"
                    val textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp // Use sp for text size
                    )
                    val measuredText = textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = textStyle
                    )
                    val textHeight = measuredText.size.height
                    val textWidth = measuredText.size.width

                    // Calculate position for text (above the box)
                    var textX = clampedLeft
                    var textY = clampedTop - textHeight - 4.dp.toPx() // Position above box with padding

                    // Adjust text position if it goes off-screen
                    if (textY < 0) {
                        textY = clampedBottom + 4.dp.toPx() // Move below box if no space above
                    }
                    if (textX + textWidth > canvasWidth) {
                        textX = canvasWidth - textWidth // Adjust left if it goes off right edge
                    }
                    if (textX < 0) {
                        textX = 0f // Ensure text doesn't go off left edge
                    }


                    // Draw text background for better readability
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(textX - 2.dp.toPx(), textY - 2.dp.toPx()),
                        size = GeometrySize(textWidth + 4.dp.toPx(), textHeight + 4.dp.toPx())
                    )

                    // Draw the text label
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(textX, textY)
                    )
                }
            }
        } else if (classifications.isEmpty()) { // Keep the placeholder text when nothing is detected
            Canvas(modifier = Modifier.fillMaxSize()) {
                val text = "Point camera at a bill..."
                val textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                val measuredText = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = textStyle,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = size.width.toInt())
                )
                val textWidth = measuredText.size.width
                val textHeight = measuredText.size.height
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    topLeft = Offset(size.width / 2 - textWidth / 2 - 4.dp.toPx(), size.height / 2 - textHeight/2 - 4.dp.toPx()),
                    size = GeometrySize(textWidth + 8.dp.toPx(), textHeight + 8.dp.toPx())
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(size.width / 2 - textWidth / 2, size.height / 2 - textHeight / 2)
                )
            }
        }
    }
}


// --- CameraPreviewViewModel (Keep as is) ---
class CameraPreviewViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val imageCapture = ImageCapture.Builder().build()

    // Configure ImageAnalysis resolution if needed (see previous accuracy answer)
    private val imageAnalyzerUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()


    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            // Debounce or check if request is actually new if needed
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    suspend fun bindToCamera(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        analyzer: BillImageAnalyzer // Receive the analyzer instance
    ) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        // Set the analyzer on the use case
        imageAnalyzerUseCase.setAnalyzer(
            ContextCompat.getMainExecutor(appContext), // Use main executor for analyzer if it posts back to UI thread quickly
            // OR provide a dedicated background executor if analysis is very heavy
            // Executors.newSingleThreadExecutor(),
            analyzer
        )

        try {
            processCameraProvider.unbindAll()

            processCameraProvider.bindToLifecycle(
                lifecycleOwner,
                DEFAULT_BACK_CAMERA,
                cameraPreviewUseCase,
                imageCapture,
                imageAnalyzerUseCase // Bind the analyzer use case
            )
            Log.d("CameraViewModel", "Camera use cases bound successfully.")

        } catch (e: Exception) {
            Log.e("CameraViewModel", "Use case binding failed: ${e.message}", e)
        }

        // Keep listening for cancellation to unbind
        try { awaitCancellation() }
        finally {
            Log.d("CameraViewModel", "awaitCancellation ended, unbinding camera use cases.")
            processCameraProvider.unbindAll()
        }
    }
}
