package com.example.billvision.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.view.Surface
import com.example.billvision.data.model.BillInference
import com.example.billvision.data.model.Result
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
import java.io.File

class BillClassifier(
    private val context: Context,
    private val threshold: Float = 0.5f,
    private val maxResults: Int = 1
) {
    private var classifier: ImageClassifier? = null

    private fun setUpImageClassifier() {
        val baseOptions = BaseOptions.builder()
            .setNumThreads(2)
            .build()

        val options = ImageClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(maxResults)
            .setScoreThreshold(threshold)
            .build()

        try {
            classifier = ImageClassifier.createFromFileAndOptions(
                context,
                "usd_detector.tflite",
                options
            )
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    // classify from a filepath
    fun classifyFromPhotoPath(imagePath: String): Result {
        val file = File(imagePath)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

        // Get the rotation from the EXIF data
        val exif = ExifInterface(file.absolutePath)
        val rotationDegrees = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (rotationDegrees) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

        val inference = classify(bitmap, rotation)[0]

        return Result(inference, imagePath)
    }

    fun classify(bitmap: Bitmap, rotation: Int): List<BillInference> {
        if (classifier == null) setUpImageClassifier()

        val imageProcessor = ImageProcessor.Builder().build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setOrientation(getOrientationFromRotation(rotation))
            .build()

        val results = classifier?.classify(tensorImage, imageProcessingOptions)

        return results?.flatMap { classifications ->
            classifications.categories.map { category ->
                BillInference(
                    name = category.label,
                    confidence = category.score
                )
            }
        }?.distinctBy { it.name } ?: emptyList()
    }

    private fun getOrientationFromRotation(rotation: Int): ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_270 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            Surface.ROTATION_90 -> ImageProcessingOptions.Orientation.TOP_LEFT
            Surface.ROTATION_180 -> ImageProcessingOptions.Orientation.RIGHT_BOTTOM
            else -> ImageProcessingOptions.Orientation.RIGHT_TOP
        }
    }
}
