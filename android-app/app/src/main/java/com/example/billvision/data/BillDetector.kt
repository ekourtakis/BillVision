package com.example.billvision.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.billvision.data.model.BillInference
import okio.IOException
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BillDetector(
    private val context: Context,
    private val modelPath: String = "usd_detector.tflite",
    private val confidenceThreshold: Float = 0.5f
) {
    private var interpreter: Interpreter? = null
    private var inputWidth = 0
    private var inputHeight = 0
    private var outputBuffer: ByteBuffer? = null
    private var outputShape: IntArray = intArrayOf()
    private var numClasses = 0
    private var numDetections = 0

    private val labels = listOf(
        "1_dollar", "50_dollar", "10_dollar", "2_dollar",
        "20_dollar", "5_dollar", "100_dollar"
    )

    init {
        setupDetector()
    }

    private fun setupDetector() {
        val interpreterOptions = Interpreter.Options().apply {
            numThreads = 4
        }

        try {
            val modelBuffer = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(modelBuffer, interpreterOptions)

            val inputTensor = interpreter!!.getInputTensor(0)
            val inputShape = inputTensor.shape()
            inputHeight = inputShape[1]
            inputWidth = inputShape[2]

            val outputTensor = interpreter!!.getOutputTensor(0)
            outputShape = outputTensor.shape()

            Log.i("BillDetector", "Model loaded. Input: ${inputShape.joinToString()}, Output: ${outputShape.joinToString()}")

            if (outputShape.size == 3 && outputShape[0] == 1) {
                numClasses = outputShape[1] - 4
                numDetections = outputShape[2]
                if (numClasses != labels.size) {
                    Log.e("BillDetector", "Model output class count ${numClasses}, expected labels.size == ${labels.size}")
                }
                outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes())
                outputBuffer?.order(ByteOrder.nativeOrder())
                Log.i("BillDetector", "Model loaded: input: ${inputShape.joinToString() }}")
            } else {
                Log.e("BillDetector", "Unexpected output tensor shpae: ${outputShape.joinToString()}")
                throw IOException("Unexpected output tensor shape")
            }
        } catch (e: IOException) {
            Log.e("BillDetector", "Error loading tflite model: ${e.message}")
            interpreter = null
        }
    }

    fun detectFromPhotoPath(imagePath: String): List<BillInference> {
        return try {
            val bitmap = BitmapFactory.decodeFile(File(imagePath).absolutePath)
            detect(bitmap)
        } catch (e: Exception) {
            Log.e("BillDetector", "Error processing photo path $imagePath: ${e.message}")
            emptyList()
        }
    }

    fun detect(bitmap: Bitmap) : List<BillInference> {
        if (interpreter == null || outputBuffer == null) {
            Log.w("BillDetector", "Detector not initialized.")
            return emptyList()
        }

        if (inputWidth == 0 || inputHeight == 0) {
            Log.e("BillDetector", "Input dimensions not set,")
            return emptyList()
        }

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0.0f, 255.0f))
            .build()

        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        val processedImage = imageProcessor.process(tensorImage)

        val inputBuffer = processedImage.buffer

        outputBuffer?.rewind()
        try {
            interpreter?.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("BillDetector", "TFLite inference error: ${e.message}")
            return emptyList()
        }

        outputBuffer?.rewind()
        val outputArray = outputBuffer?.asFloatBuffer()

        if (outputArray == null) {
            Log.e("BillDetector", "output array is null.")
            return emptyList()
        }

        val results = mutableListOf<BillInference>()

        val outputRowStride = outputShape[1]
        val boxCoordsSize = 4

        for (i in 0 until numDetections) {
            val offset = i * outputRowStride

            var maxClassScore = 0.0f
            var detectedClassIndex = -1

            for (c in 0 until numClasses) {
                val classScore = outputArray.get(offset + boxCoordsSize + c)

                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    detectedClassIndex = c
                }
            }

            if (maxClassScore >= confidenceThreshold && detectedClassIndex != -1) {
                val className = labels.getOrElse(detectedClassIndex) {
                    "Unknown_$detectedClassIndex"
                }
                // TODO: Extract bounding box later:
                // val cx = outputArray.get(offset + 0)
                // val cy = outputArray.get(offset + 1)
                // val w = outputArray.get(offset + 2)
                // val h = outputArray.get(offset + 3)
                // Convert cx,cy,w,h to RectF(left, top, right, bottom) - remember scaling!

                // Use maxClassScore as the confidence for this detection
                results.add(BillInference(name = className, confidence = maxClassScore))
            }

        }
        // TODO: Apply Non-Max Suppression (NMS) here!

        return results.distinctBy { it.name }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        Log.i("BillDetector", "Interpretor closed")
    }
}