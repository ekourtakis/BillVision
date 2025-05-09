package com.example.billvision.ui.camera

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.accessibility.AccessibilityManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billvision.ml.BillImageAnalyzer
import com.example.billvision.model.BillInference
import com.example.billvision.model.ImageDimensions
import kotlin.collections.forEach
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTextApi::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: CameraPreviewViewModel,
    classifications: List<BillInference>,
    imageDimensions: ImageDimensions,
    analyzer: BillImageAnalyzer,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // -- accessibility ---
    val accessibilityManager = remember {
        context.getSystemService(
            Context.ACCESSIBILITY_SERVICE
        ) as AccessibilityManager
    }

    val isAccessibilityEnabled by rememberAccessibilityManagerEnabled()

    var lastAnnouncement by remember { mutableStateOf("") }
    var lastAnnouncementTime by remember { mutableLongStateOf(0L) }

    val minDelayMillis = 5000L // 5 sec delay

    LaunchedEffect(classifications, isAccessibilityEnabled) {
        if (!isAccessibilityEnabled) {
            return@LaunchedEffect // don't announce if TalkBack is off
        }

        val timeSinceLastAnnouncement = System.currentTimeMillis() - lastAnnouncementTime
        if (timeSinceLastAnnouncement < minDelayMillis) {
            return@LaunchedEffect // don't announce if too soon after the last one
        }

        val newAnnouncement = createAnnouncementString(classifications)

        if (newAnnouncement == lastAnnouncement) {
            return@LaunchedEffect // don't announce if nothing has changed
        }

        // conditions met, announce
        accessibilityManager.sendAccessibilityEvent(
            AccessibilityEvent.obtain().apply {
                eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
                packageName = context.packageName
                className = javaClass.name
                text.add(newAnnouncement)
            }
        )

        // updates
        lastAnnouncement = newAnnouncement
        lastAnnouncementTime = System.currentTimeMillis()
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
                    size = Size(textWidth + 8.dp.toPx(), textHeight + 8.dp.toPx())
                )
                drawText(
                    textLayoutResult = measuredText,
                    topLeft = Offset(size.width / 2 - textWidth / 2, size.height / 2 - textHeight / 2)
                )
            }
        }

        // Canvas fills the Box, drawn on top of the Viewfinder
        if (imageDimensions.isValid && classifications.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val imageAspectRatio = imageDimensions.width.toFloat() / imageDimensions.height.toFloat()
                val canvasAspectRatio = canvasWidth / canvasHeight

                val scaleFactor: Float
                val offsetX: Float
                val offsetY: Float

                if (imageAspectRatio > canvasAspectRatio) {
                    // image is wider than canvas (letterboxing top/bottom)
                    scaleFactor = canvasWidth / imageDimensions.width.toFloat()
                    offsetX = 0f
                    offsetY = (canvasHeight - imageDimensions.height * scaleFactor) / 2f
                } else {
                    // image is taller than canvas (pillar boxing left/right)
                    scaleFactor = canvasHeight / imageDimensions.height.toFloat()
                    offsetX = (canvasWidth - imageDimensions.width * scaleFactor) / 2f
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
                        size = Size(boxWidth, boxHeight),
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
                        size = Size(textWidth + 4.dp.toPx(), textHeight + 4.dp.toPx())
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

internal fun createAnnouncementString(classifications: List<BillInference>): String {
    return when {
        classifications.isEmpty() -> "Point camera at a bill..."
        classifications.size == 1 -> "Detected ${classifications.size} bill: ${classifications[0].name}"
        else -> {
            val billNames = classifications.map { it.name }

            return "Detected ${classifications.size} bills: $billNames"
        }
    }
}
@Composable
fun rememberAccessibilityManagerEnabled(): State<Boolean> {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val accessibilityManager = remember {
        applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val isEnabledState = remember { mutableStateOf(accessibilityManager.isEnabled) }

    val listener = remember {
        AccessibilityManagerCompat.AccessibilityStateChangeListener { enabled ->
            isEnabledState.value = enabled
        }
    }

    DisposableEffect(accessibilityManager, lifecycleOwner, listener) {
        AccessibilityManagerCompat.addAccessibilityStateChangeListener(accessibilityManager, listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabledState.value = accessibilityManager.isEnabled
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            AccessibilityManagerCompat.removeAccessibilityStateChangeListener(accessibilityManager, listener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return isEnabledState
}
