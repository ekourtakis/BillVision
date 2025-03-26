package com.example.billvision.data.model

import java.io.Serializable

data class Result(
    val billInference: BillInference,
    val filePath: String
) : Serializable