package com.example.billvision.data.model

import android.graphics.RectF
import java.io.Serializable

data class BillInference (
    val name: String,
    val confidence: Float,
    val boundingBox: RectF
) : Serializable