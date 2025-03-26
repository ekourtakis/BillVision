package com.example.billvision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.createBitmap

fun Bitmap.centerCrop(newWidth: Int, newHeight: Int): Bitmap? {
    // Input validation
    if (newWidth <= 0 || newHeight <= 0) {
        Log.e("BitmapExt", "Invalid new dimensions: width=$newWidth, height=$newHeight")
        return null
    }
    if (this.width <= 0 || this.height <= 0) {
        Log.e("BitmapExt", "Bitmap has invalid dimensions: width=${this.width}, height=${this.height}")
        return null
    }

    val scaleX = newWidth.toFloat() / width
    val scaleY = newHeight.toFloat() / height
    val scale = maxOf(scaleX, scaleY)

    val scaledWidth = (scale * width).toInt()
    val scaledHeight = (scale * height).toInt()

    val left = (scaledWidth - newWidth) / 2
    val top = (scaledHeight - newHeight) / 2

    if (left < 0 || top < 0) {
        Log.e("BitmapExt", "Calculated crop region is negative: left=$left, top=$top")
        return null
    }

    val result = createBitmap(newWidth, newHeight, this.config!!)
    val canvas = Canvas(result)
    val rect = Rect(left, top, left + newWidth, top + newHeight)
    val src = Rect(0, 0, this.width, this.height)
    canvas.drawBitmap(this, src, rect, null)
    return result
}