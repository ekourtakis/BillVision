package com.example.billvision.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.billvision.data.model.BillInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable // Import Closeable
import kotlin.coroutines.cancellation.CancellationException
import androidx.core.graphics.createBitmap

class BillImageAnalyzer(
    private val detector: BillDetector,
    private val onResults: (List<BillInference>) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var frameSkipCounter = 0
    private val skipFrames = 10

    private var currentDetectionJob: Job? = null

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        // Skip frames logic
        if (frameSkipCounter < skipFrames) {
            frameSkipCounter++
            imageProxy.close()
            return
        }
        frameSkipCounter = 0 // Reset counter for the next frame to be processed

        // Cancel any previous ongoing detection job before starting a new one
        currentDetectionJob?.cancel()

        // Launch detection in the background scope
        currentDetectionJob = scope.launch {
            try {
                // 1. Get the image (check for null)
                val image = imageProxy.image ?: run {
                    Log.w("BillImageAnalyzer", "ImageProxy.image was null, skipping frame.")
                    return@launch // Exit this coroutine launch
                }

                // 2. Convert to Bitmap (this can be slow, keep in background)
                val bitmap = image.toBitmap()
                Log.d("BillImageAnalyzer", "Bitmap size: ${bitmap.width}x${bitmap.height}. Calling detector...")

                // 3. Perform detection (CPU-intensive, stays in Dispatchers.Default)
                val results = detector.detect(bitmap) // Pass rotationDegrees if needed by detect()
                Log.d("BillImageAnalyzer", "Detection returned ${results.size} results.")

                // 4. Check if the coroutine is still active before switching context
                // Avoids posting results if the job was cancelled (e.g., by a newer frame)
                if (!isActive) {
                    Log.d("BillImageAnalyzer", "Coroutine cancelled before UI update.")
                    return@launch
                }

                // 5. Switch to Main thread to post results safely
                withContext(Dispatchers.Main) {
                    Log.d("BillImageAnalyzer", "Calling onResults on main thread.")
                    onResults(results)
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // not an error, called currentDetectionJob?.cancel() earlier
                    Log.d("BillImageAnalyzer", "Detection job cancelled.")
                    throw e
                }
                Log.e("BillImageAnalyzer", "Error during background detection: ${e.message}", e)
            } finally {
                try {
                    imageProxy.close()
                } catch (ise: IllegalStateException) {
                    Log.w("BillImageAnalyzer", "Failed to close ImageProxy, may already be closed or invalid.", ise)
                }
            }
        }
    }

    override fun close() {
        try {
            if (!scope.isActive) return // Avoid cancelling if already cancelled
            scope.cancel() // Cancel the scope itself to release resources and stop pending jobs
            Log.i("BillImageAnalyzer", "Coroutine scope cancelled via close().")
        } catch (e: Exception) {
            Log.e("BillImageAnalyzer", "Error cancelling scope: ${e.message}", e)
        }
    }

    @ExperimentalGetImage
    fun Image.toBitmap(): Bitmap {
        if (format != ImageFormat.YUV_420_888) {
            Log.e("ImageExt", "Unsupported image format: $format")
            return createBitmap(1, 1)
        }

        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)

        val yRowStride = planes[0].rowStride
        val vuRowStride = planes[2].rowStride
        val vuPixelStride = planes[2].pixelStride

        if (vuPixelStride == 2) { // Common case: NV21/NV12 like structure (V comes first in buffer for NV21)
            // Copy V buffer skipping padding
            var vOffset = 0
            for (row in 0 until height / 2) {
                vBuffer.position(row * vuRowStride)
                vBuffer.get(nv21, ySize + vOffset, width / 2) // Assuming width is even
                vOffset += width / 2
            }
            // Reset V buffer position if needed later (though we usually don't reuse it here)
            vBuffer.rewind()

            // Copy U buffer skipping padding and interleaving with V data
            var uOffset = 0
            for (row in 0 until height / 2) {
                uBuffer.position(row * vuRowStride)
                uBuffer.get(nv21, ySize + uOffset + 1, width / 2 -1 ) // Copy U plane data into odd bytes
                uOffset += width/2
            }
            uBuffer.rewind()


            // Correctly copy VU plane from NV21 format
            val vuPlane = ByteArray(width * height / 2) // Size of VU plane for NV21
            var offset = 0
            // Iterate through the VU plane, copying V then U for each 2x2 block
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vIndex = row * vuRowStride + col * vuPixelStride
                    val uIndex = row * planes[1].rowStride + col * planes[1].pixelStride // U plane index

                    if (vIndex < vBuffer.limit() && uIndex < uBuffer.limit()) {
                        vuPlane[offset++] = vBuffer[vIndex] // V
                        vuPlane[offset++] = uBuffer[uIndex] // U
                    } else {
                        // Handle potential out-of-bounds access, maybe due to padding assumptions
                        Log.w("ImageExt", "Potential OOB access during VU copy")
                        // Fill with default value? Or break?
                        if (offset < vuPlane.size) vuPlane[offset++] = 128.toByte() // Grey value
                        if (offset < vuPlane.size) vuPlane[offset++] = 128.toByte()
                    }
                }
            }
            // Copy the combined VU plane into the nv21 buffer
            System.arraycopy(vuPlane, 0, nv21, ySize, vuPlane.size)


        } else if (vuPixelStride == 1) {
            // Handle Planar YUV_420_888 (I420) - This NV21 conversion logic won't work directly
            // You would need to copy V plane then U plane separately into nv21 array.
            Log.e("ImageExt", "Planar YUV420 format (I420) not directly handled by this NV21 conversion.")
            // Implement I420 to NV21 conversion if needed, or use a library.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // Example fallback
        } else {
            Log.e("ImageExt", "Unsupported pixel stride: $vuPixelStride")
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) // Example fallback
        }


        // Convert YUV (NV21) to JPEG, then to Bitmap
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        // Quality 50 is quite low, might affect detection accuracy. Increase if needed (e.g., 80-95).
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 85, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}