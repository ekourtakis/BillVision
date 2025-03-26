package com.example.billvision.data.model

import java.io.Serializable

data class BillInference (
    val name: String,
    val confidence: Float,
) : Serializable