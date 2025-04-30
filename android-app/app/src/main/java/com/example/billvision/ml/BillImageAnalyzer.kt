package com.example.billvision.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.Size // Import Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.billvision.model.BillInference
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.Closeable
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class BillImageAnalyzer(
    private val detector: BillDetector,
    private val onResults: (List<BillInference>, Size) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var frameSkipCounter = 0
    private val skipFrames = 5
    private var currentDetectionJob: Job? = null
    private var lastImageSize = Size(0, 0) // Cache last known size

    private val isClosed = AtomicBoolean(false)
    private val detectorLock = Any()

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            imageProxy.close()
            return
        }

        if (frameSkipCounter < skipFrames) {
            frameSkipCounter++
            imageProxy.close()
            return
        }
        frameSkipCounter = 0

        val imageTimeStamp = imageProxy.imageInfo.timestamp
        val originalImageSize = Size(imageProxy.width, imageProxy.height)

        currentDetectionJob = scope.launch {
            if (isClosed.get() || !isActive) {
                imageProxy.close()
                return@launch
            }

            var bitmap: Bitmap? = null
            try {
                val image = imageProxy.image ?: run {
                    Log.w("BillImageAnalyzer", "ImageProxy.image was null.")
                    imageProxy.close()
                    return@launch
                }

                bitmap = image.toBitmap()
                var results: List<BillInference>? = null

                // bitmap is valid before proceeding
                if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
                    Log.w("BillImageAnalyzer", "Bitmap conversion resulted in null or invalid bitmap.")
                    return@launch
                }

                if (isClosed.get() || !isActive) {
                    bitmap.recycle()
                    return@launch
                }

                // --- detection ---
                Log.v("BillImageAnalyzer", "Timestamp $imageTimeStamp: Acquiring lock for detection...")
                synchronized(detectorLock) { // lock detector
                    if (!isClosed.get()) {
                        Log.d("BillImageAnalyzer", "Timestamp $imageTimeStamp: Performing detection...")
                        results = detector.detect(bitmap)
                    } else {
                        Log.w("BillImageAnalyzer", "Timestamp $imageTimeStamp: Analyzer closed while waiting for lock.")
                    }
                } // lock released
                Log.v("BillImageAnalyzer", "Timestamp $imageTimeStamp: Released lock after detection.")

                if (!isActive) {
                    Log.d("BillImageAnalyzer", "Coroutine cancelled before UI update.")
                    return@launch
                }

                // detection didn't run
                if (results == null && isActive) {
                    Log.d("BillImageAnalyzer", "Timestamp $imageTimeStamp: Detection skipped because analyzer was closed.")
                    return@launch
                }

                if (results != null && isActive) {
                    Log.d("BillImageAnalyzer", "Timestamp $imageTimeStamp: Detection returned ${results.size} results.")
                    withContext(Dispatchers.Main) {
                        if (!isClosed.get()) {
                            onResults(results, originalImageSize)
                        }
                    }
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("BillImageAnalyzer", "Detection job cancelled.")
                    throw e
                }
                Log.e("BillImageAnalyzer", "Error during detection: ${e.message}", e)
                // post empty results
                 withContext(Dispatchers.Main) { onResults(emptyList(), lastImageSize) }
            } finally {
                 bitmap?.recycle()
                try {
                    imageProxy.close() // proxy is always closed
                } catch (ise: IllegalStateException) {
                    Log.w("BillImageAnalyzer", "Failed to close ImageProxy, may already be closed.", ise)
                }
            }
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            Log.d("BillImageAnalyzer", "Closing analyzer...")

            scope.cancel("Analyzer closing")
            Log.d("BillImageAnalyzer", "Scope cancelled.")

            Log.d("BillImageAnalyzer", "Acquiring lock to close detector...")
            synchronized(detectorLock) { // lock detector
                Log.d("BillImageAnalyzer", "Lock acquired. Closing detector instance...")
                try {
                    detector.close()
                } catch (e: Exception) {
                    Log.e("BillImageAnalyzer", "Error closing detector: ${e.message}", e)
                }
            } // lock released
            Log.d("BillImageAnalyzer", "Detector closed. Analyzer close complete.")
        } else {
            Log.d("BillImageAnalyzer", "Analyzer already closed.")
        }
    }

    @ExperimentalGetImage
    fun Image.toBitmap(): Bitmap? {
        if (format != ImageFormat.YUV_420_888) {
            Log.e("ImageExt", "Unsupported image format: $format")
            return null // Return null for unsupported formats
        }

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining() // Keep this
        val vSize = vBuffer.remaining() // Keep this

        // Check if image dimensions are valid
        if (width <= 0 || height <= 0) {
            Log.e("ImageExt", "Invalid image dimensions: ${width}x$height")
            return null
        }

        // Check buffer sizes relative to expected image size
        // Expected Y size = width * height
        // Expected U/V size = width * height / 4 (for YUV420)
        val expectedYSize = width * height
        val expectedUVSize = width * height / 4
        if (ySize < expectedYSize || uSize < expectedUVSize || vSize < expectedUVSize) {
            Log.w("ImageExt", "Buffer sizes (Y=$ySize, U=$uSize, V=$vSize) are smaller than expected for image size ${width}x$height (Y=$expectedYSize, UV=$expectedUVSize). Might be due to cropping or format issues.")
            // Decide whether to proceed or return null. Proceeding might lead to artifacts.
            // Returning null might be safer if sizes mismatch significantly.
            // For now, let's log and proceed, but be aware.
        }


        val nv21 = ByteArray(ySize + uSize + vSize) // Allocation might be slightly larger than needed if buffers have padding

        try {
            yBuffer.get(nv21, 0, ySize)

            val vPixelStride = planes[2].pixelStride
            val uPixelStride = planes[1].pixelStride
            val vRowStride = planes[2].rowStride
            val uRowStride = planes[1].rowStride

            // Common NV21/NV12 case (interleaved VU plane)
            if (vPixelStride == 2 && uPixelStride == 2 && vRowStride == uRowStride) {
                val vuBuffer = planes[2].buffer // Usually the V plane buffer holds the interleaved data

                // Ensure we don't try to read more bytes than the nv21 array can hold after ySize
                val maxVuBytesToRead = nv21.size - ySize
                val actualVuBytesToRead = minOf(vuBuffer.remaining(), maxVuBytesToRead)

                if (actualVuBytesToRead < 0) {
                    Log.e("ImageExt", "Calculated negative bytes to read for VU plane ($actualVuBytesToRead). nv21 size: ${nv21.size}, ySize: $ySize")
                    return null
                }

                if (vuBuffer.remaining() > maxVuBytesToRead) {
                    Log.w("ImageExt", "VU buffer remaining (${vuBuffer.remaining()}) > available space in nv21 array ($maxVuBytesToRead). Reading truncated VU data.")
                }

                Log.d("ImageExt", "Copying $actualVuBytesToRead bytes from VU buffer into nv21 at offset $ySize")
                vuBuffer.get(nv21, ySize, actualVuBytesToRead) // <-- FIX: Use actualVuBytesToRead

            } else {
                // Manual interleaving required for planar or other formats (more complex)
                // This part needs careful implementation if you encounter non-NV21 formats.
                // It copies V then U sequentially, which creates I420 format, not NV21.
                Log.w("ImageExt", "Attempting fallback copy for non-interleaved format (V then U). Result will be I420-like, not NV21.")

                // Ensure we don't try to read more bytes than nv21 can hold
                var currentOffset = ySize
                val maxVBytes = nv21.size - currentOffset
                val actualVBytes = minOf(vSize, maxVBytes)
                if (actualVBytes < 0) { Log.e("ImageExt", "Negative V bytes"); return null}
                vBuffer.get(nv21, currentOffset, actualVBytes)
                currentOffset += actualVBytes

                val maxUBytes = nv21.size - currentOffset
                val actualUBytes = minOf(uSize, maxUBytes)
                if (actualUBytes < 0) { Log.e("ImageExt", "Negative U bytes"); return null}
                uBuffer.get(nv21, currentOffset, actualUBytes)

                // Note: This fallback results in YYYYYYYYVVVVUUUU layout (I420),
                // while YuvImage expects YYYYYYYYVUVUVU (NV21).
                // The resulting JPEG might be distorted if the format isn't truly NV21.
            }

        } catch (e: Exception) {
            Log.e("ImageExt", "Error copying YUV buffer data: ${e.message}", e)
            return null
        }

        // Create YuvImage using the potentially slightly oversized nv21 array,
        // but specify the correct image dimensions.
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        return try {
            // Use a compression rectangle matching image dimensions
            yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 90, out) // Quality 90
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ImageExt", "Error compressing YuvImage or decoding JPEG: ${e.message}", e)
            null // Return null on error
        }
    }
}