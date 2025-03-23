package com.example.billvision

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.example.billvision.ml.UsdDetector
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class ModelHandler(private val context: Context) {
    private val IMAGE_SIZE = 224

    private lateinit var model: UsdDetector

    fun classifyImage(photoPath: String): BillInference {
        val bitmap = BitmapFactory.decodeFile(photoPath)

        try {
            model = UsdDetector.newInstance(context)

            // create a TensorImage from the bitmap
            val tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)

            // Preprocess image (resize, normalize, etc.)
            val preprocessOp = ImageProcessor.Builder()
                .add(ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 1f))
                .build()
            val processedImage = preprocessOp.process(tensorImage)

            // run inference
            val outputs = model.process(processedImage)

            // Get output values
            val probabilityList = outputs.probabilityAsCategoryList

            // Find the highest confidence prediction
            val maxIndex = probabilityList.indices.maxByOrNull { probabilityList[it].score } ?: -1
            val confidence = if (maxIndex != -1) probabilityList[maxIndex].score else 0f

            Log.d("ModelHandler", "Inference: $maxIndex, $confidence")

            return BillInference(
                name = BillLabel.fromIndex(maxIndex).name,
                confidence = confidence
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return BillInference(name = "Unknown", confidence = 0f)
        } finally {
            model.close()
        }
    }
}