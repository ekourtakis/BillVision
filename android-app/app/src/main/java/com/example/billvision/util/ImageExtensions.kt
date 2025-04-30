package com.example.billvision.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import java.io.ByteArrayOutputStream

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