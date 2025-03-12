package com.example.billvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.Log
import com.example.billvision.ml.UsdDetector
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class ModelHandler(private val context: Context) {
    val IMAGE_SIZE = 224

    private lateinit var model: UsdDetector

    fun classifyImage(photoPath: String) {
        val bitmap = loadAndPreprocessBitmap(photoPath)

        runInference(bitmap)
    }

    private fun runInference(bitmap: Bitmap) {
        Log.d("ModelHandler", "in runinference")
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


            val outputArray = outputFeature0.outputFeature0AsTensorBuffer.floatArray  // If your model output is float32
            Log.d("ModelHandler", "Inference result values: ${outputArray.joinToString(", ")}")

        } catch (e: Exception) {
            Log.d("ModelHandler", "in the catch")
            e.printStackTrace()
        } finally {
            model.close()
        }
        Log.d("ModelHandler", "out of runinference")
    }


    private fun loadAndPreprocessBitmap(filePath: String) : Bitmap {
        // load bitmap
        var bitmap = BitmapFactory.decodeFile(filePath)

        // make bitmap square
        val dimension = bitmap.height.coerceAtMost(bitmap.width)
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension)

        // make bitmap proper resolution
        bitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, false)

        return bitmap
    }
}