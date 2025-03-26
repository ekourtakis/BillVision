package com.example.billvision

import java.io.Serializable

data class Result(
    val billInference: BillInference,
    val filePath: String
) : Serializable