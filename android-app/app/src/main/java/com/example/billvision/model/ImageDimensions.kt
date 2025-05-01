package com.example.billvision.model

data class ImageDimensions(
    val width: Int,
    val height: Int
) {
    companion object {
        val ZERO = ImageDimensions(0,0)
    }

    val isValid: Boolean get() = width > 0 && height > 0
}
