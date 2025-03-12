package com.example.billvision

import java.io.Serializable

data class BillInference (
    val billLabelIndex: Int,
    val confidence: Float,
    val photoPath: String
) : Serializable