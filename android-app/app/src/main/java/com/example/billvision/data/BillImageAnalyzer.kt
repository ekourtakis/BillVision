package com.example.billvision.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.billvision.data.model.BillInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException

class BillImageAnalyzer(
    private val detector: BillDetector,
    private val onResults: (List<BillInference>) -> Unit
): ImageAnalysis.Analyzer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var frameSkipCounter = 0
    private val skipFrames = 10

    private var currentDetectionJob: Job? = null

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (frameSkipCounter % skipFrames != 0) {
            frameSkipCounter++
            imageProxy.close()
            return // skip
        }
        frameSkipCounter = 0

        currentDetectionJob?.cancel()

        currentDetectionJob = scope.launch {
            try {
                val bitmap = imageProxy.toBitmap()
                Log.d("BillImageAnalyzer", "Bitmap size: ${bitmap.width}x${bitmap.height}. calling detector...")
                val results = detector.detect(bitmap) // Pass rotationDegrees if needed by detect()
                Log.d("BillImageAnalyzer", "Detection returned ${results.size} results.")

                // c) Switch to Main thread to post results safely
                withContext(Dispatchers.Main) {
                    Log.d("BillImageAnalyzer", "calling onResults on main thread.")
                    onResults(results)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Don't log cancellation exceptions, they are expected
                    throw e // Re-throw cancellation
                }
                Log.e("BillImageAnalyzer", "Error during background detection: ${e.message}", e)
                // Post empty results on error
                withContext(Dispatchers.Main) { onResults(emptyList()) }
            } finally {
                currentDetectionJob?.cancel() // Cancel any active job
                scope.cancel() // Cancel the scope itself to release resources
                Log.i("BillImageAnalyzer", "Coroutine scope cancelled.")
            }
        }
    }

    // Method to cancel all background jobs when the analyzer is no longer needed
    fun close() {
        currentDetectionJob?.cancel() // Cancel any active job
        scope.cancel() // Cancel the scope itself to release resources
        Log.i("BillImageAnalyzer", "Coroutine scope cancelled.")
    }

    // Helper to copy ByteBuffer contents
    private fun ByteBuffer.copy(): ByteBuffer {
        val readOnlyCopy = this.asReadOnlyBuffer()
        val byteArray = ByteArray(readOnlyCopy.remaining())
        readOnlyCopy.get(byteArray)
        return ByteBuffer.wrap(byteArray)
    }

    fun Image.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

}