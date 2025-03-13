package com.example.billvision

import android.content.Context
import android.graphics.BitmapFactory
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
            val tensorImage = TensorImage(DataType.UINT8)
            tensorImage.load(bitmap)

            // Preprocess image (resize, normalize, etc.)
            val preprocessOp = ImageProcessor.Builder()
                .add(ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 1f))
                .build()
            val processedImage = preprocessOp.process(tensorImage)

            val outputFeature0 = model.process(processedImage.tensorBuffer)

            // Get output values
            val outputArray = outputFeature0.outputFeature0AsTensorBuffer.floatArray

            // Find the highest confidence prediction
            val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
            val confidence = if (maxIndex != -1) outputArray[maxIndex] / 255f else 0f

            return BillInference(
                billLabelIndex = maxIndex, confidence = confidence, photoPath = photoPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return BillInference(billLabelIndex = -1, confidence = 0f, photoPath=photoPath)
        } finally {
            model.close()
        }
    }
}