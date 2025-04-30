package com.example.billvision.ml

import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.billvision.model.BillInference
import com.example.billvision.util.toBitmap
import kotlinx.coroutines.*
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
}