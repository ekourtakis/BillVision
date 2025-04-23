package com.example.billvision.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
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
import kotlinx.coroutines.flow.update
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
                onImageAnalyzed = { size -> // callback to get image size
                    if (imageSize != size && size.width > 0 && size.height > 0) {
                        imageSize = size
                    }
                },
                imageSize = imageSize // pass size to CameraPreview
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
    imageSize: Size, // receive analyzed image size
    onImageAnalyzed: (Size) -> Unit, // callback to update image size
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
                    onImageAnalyzed(analyzedSize) // update the size
                }
                Log.d(
                    "CameraActivity",
                    "onResults called with ${detectedBills.size} detections for image size ${analyzedSize.width}x${analyzedSize.height}"
                )
            }
        )
    }

    // -- accessibility ---
    val accessibilityManager = context.getSystemService(
        Context.ACCESSIBILITY_SERVICE
    ) as AccessibilityManager

    var lastAnnouncement by remember { mutableStateOf("") }

    LaunchedEffect(classifications, accessibilityManager.isEnabled) {
        if (!accessibilityManager.isEnabled) {
            return@LaunchedEffect // don't announce if TalkBack is off
        }

        val newAnnouncement = createAnnouncementString(classifications)

        if (newAnnouncement == lastAnnouncement) {
            return@LaunchedEffect // don't announce if nothing has changed
        }

        accessibilityManager.sendAccessibilityEvent(
            AccessibilityEvent.obtain().apply {
                eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
                packageName = context.packageName
                className = javaClass.name
                text.add(newAnnouncement)
            }
        )
        lastAnnouncement = newAnnouncement
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

    // for drawing text on Canvas
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxSize()) {
        // Camera Viewfinder fills the Box
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- bounding box drawing ---
        if (classifications.isEmpty()) { // placeholder text when nothing is detected
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

        // Canvas fills the Box, drawn on top of the Viewfinder
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
                    // image is wider than canvas (letterboxing top/bottom)
                    scaleFactor = canvasWidth / imageSize.width.toFloat()
                    offsetX = 0f
                    offsetY = (canvasHeight - imageSize.height * scaleFactor) / 2f
                } else {
                    // image is taller than canvas (pillar boxing left/right)
                    scaleFactor = canvasHeight / imageSize.height.toFloat()
                    offsetX = (canvasWidth - imageSize.width * scaleFactor) / 2f
                    offsetY = 0f
                }

                // draw each bounding box and label
                classifications.forEach { inference ->
                    val box = inference.boundingBox // coordinates are relative to original bitmap

                    // scale and offset the coordinates
                    val canvasLeft = box.left * scaleFactor + offsetX
                    val canvasTop = box.top * scaleFactor + offsetY
                    val canvasRight = box.right * scaleFactor + offsetX
                    val canvasBottom = box.bottom * scaleFactor + offsetY

                    // ensure coordinates are within canvas bounds (optional, prevents drawing outside)
                    if (canvasRight <= canvasLeft || canvasBottom <= canvasTop ||
                        canvasLeft >= canvasWidth || canvasTop >= canvasHeight ||
                        canvasRight <= 0 || canvasBottom <= 0
                    ) {
                        return@forEach // Skip drawing if box is invalid or fully outside
                    }

                    // clamp coordinates to be within the visible canvas area
                    val clampedLeft = max(0f, canvasLeft)
                    val clampedTop = max(0f, canvasTop)
                    val clampedRight = min(canvasWidth, canvasRight)
                    val clampedBottom = min(canvasHeight, canvasBottom)

                    val boxWidth = clampedRight - clampedLeft
                    val boxHeight = clampedBottom - clampedTop

                    if (boxWidth <= 0 || boxHeight <= 0) return@forEach // skip if degenerated after clamping

                    // draw the bounding box outline
                    drawRect(
                        color = Color.Yellow,
                        topLeft = Offset(clampedLeft, clampedTop),
                        size = GeometrySize(boxWidth, boxHeight),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // prepare text label
                    val label =
                        "${inference.name} ${String.format("%.1f", inference.confidence * 100)}%"
                    val textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    val measuredText = textMeasurer.measure(
                        text = AnnotatedString(label),
                        style = textStyle
                    )
                    val textHeight = measuredText.size.height
                    val textWidth = measuredText.size.width

                    // calculate position for text (above the box)
                    var textX = clampedLeft
                    var textY =
                        clampedTop - textHeight - 4.dp.toPx() // position above box with padding

                    // adjust text position if it goes off-screen
                    if (textY < 0) {
                        textY = clampedBottom + 4.dp.toPx() // move below box if no space above
                    }
                    if (textX + textWidth > canvasWidth) {
                        textX = canvasWidth - textWidth // adjust left if it goes off right edge
                    }
                    if (textX < 0) {
                        textX = 0f // ensure text doesn't go off left edge
                    }


                    // draw text background
                    drawRect(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(textX - 2.dp.toPx(), textY - 2.dp.toPx()),
                        size = GeometrySize(textWidth + 4.dp.toPx(), textHeight + 4.dp.toPx())
                    )

                    // draw the text label
                    drawText(
                        textLayoutResult = measuredText,
                        topLeft = Offset(textX, textY)
                    )
                }
            }
        }
    }
}

private fun createAnnouncementString(classifications: List<BillInference>): String {
    return when {
        classifications.isEmpty() -> "Point camera at a bill..."
        classifications.size == 1 -> "Detected ${classifications.size} bill: ${classifications[0].name}"
        else -> {
            val billNames = classifications.map { it.name }

            return "Detected ${classifications.size} bills: $billNames"
        }
    }
}

class CameraPreviewViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val imageCapture = ImageCapture.Builder().build()

    private val imageAnalyzerUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()


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
