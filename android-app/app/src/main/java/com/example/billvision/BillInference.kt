package com.example.billvision

import java.io.Serializable

data class BillInference (
    val name: String,
    val confidence: Float,
) : Serializable