package com.example.billvision.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.billvision.data.model.BillInference
import com.example.billvision.utils.centerCrop

class BillImageAnalyzer(
    private val classifier: BillClassifier,
    private val onResults: (List<BillInference>) -> Unit
): ImageAnalysis.Analyzer {
    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
        // only analyze every 60th frame to improve performance
        if (frameSkipCounter % 60 != 0)
            return // skip
        frameSkipCounter++

        // get rotation
        val rotationDegrees = image.imageInfo.rotationDegrees
        val bitmap = image
            .toBitmap()
            .centerCrop(321, 321)

        // classify the image
        val results = bitmap?.let { classifier.classify(it, rotationDegrees) }

        if (results != null) {
            onResults(results)
        }

        image.close()
    }
}