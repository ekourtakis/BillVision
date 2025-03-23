package com.example.billvision

import android.graphics.Bitmap

interface BillClassifier {
    fun classify(bitmap: Bitmap, rotation: Int): List<BillInference>
}
