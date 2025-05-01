package com.example.billvision.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import com.example.billvision.model.BillInference
import okio.IOException
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class BillDetector(
    private val context: Context,
    private val modelPath: String = "usd_detector.tflite",
    private val confidenceThreshold: Float = 0.9f,
    private val iouThreshold: Float = 0.45f // for no max suppression
)  : Closeable {
    private var interpreter: Interpreter? = null
    private var inputWidth = 0
    private var inputHeight = 0
    private var outputBuffer: ByteBuffer? = null
    private var outputShape: IntArray = intArrayOf()
    private var numClasses = 0
    private var numDetections = 0
    private val boxCoordsSize = 4

    private val labels = listOf(
        "1 dollar", "50 dollar", "10 dollar", "2 dollar",
        "20 dollar", "5 dollar", "100 dollar"
    )

    init {
        setupDetector()
    }

    private fun setupDetector() {
        val interpreterOptions = Interpreter.Options().apply {
            numThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
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
                numClasses = outputShape[1] - boxCoordsSize
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

        // before no mass suppression
        val preNMSDetections = mutableListOf<BillInference>()

        val outputRowStride = outputShape[1]

        for (i in 0 until numDetections) {
            val currentBoxOffset = i * outputRowStride

            // extract raw data and convert
            val cx = outputArray.get(currentBoxOffset + 0) * inputWidth
            val cy = outputArray.get(currentBoxOffset + 1) * inputHeight
            val w = outputArray.get(currentBoxOffset + 2) * inputWidth
            val h = outputArray.get(currentBoxOffset + 3) * inputHeight

            val left = cx - w / 2f
            val top = cy - h / 2f
            val right = cx + w / 2f
            val bottom = cy + h /  2f

            // extract class scores
            var maxClassScore = 0f
            var detectedClassIndex = -1
            for (c in 0 until numClasses) {
                val classScore = outputArray.get(currentBoxOffset + boxCoordsSize + c)
                if (classScore > maxClassScore) {
                    maxClassScore = classScore
                    detectedClassIndex = c
                }
            }

            if (maxClassScore >= confidenceThreshold && detectedClassIndex != -1) {
                val className = labels.getOrElse(detectedClassIndex) {
                    "Unknown (index $detectedClassIndex)"
                }

                val scaleX = bitmap.width.toFloat() / inputWidth
                val scaleY = bitmap.height.toFloat() / inputHeight

                val scaleBox = RectF(
                    left * scaleX,
                    top * scaleY,
                    right * scaleX,
                    bottom * scaleY
                )

                preNMSDetections.add(
                    BillInference(
                        name = className,
                        confidence = maxClassScore,
                        boundingBox = scaleBox
                    )
                )
            }
        }

        Log.d("BillDetector", "found ${preNMSDetections.size} detections before NMS")

        val postNMSDetections = applyNMS(preNMSDetections)

        Log.d("BillDetector", "found ${postNMSDetections.size} detections after NMS")

        return postNMSDetections
    }

    internal fun applyNMS(detections: List<BillInference>): List<BillInference> {
        if (detections.isEmpty()) return emptyList()

        // *** Add Logging Here ***
        Log.d("BillDetectorNMS", "--- Entering NMS with ${detections.size} detections ---")
        detections.take(10).forEachIndexed { index, det -> // Log first 10 boxes
            Log.v("BillDetectorNMS", "Input Box $index: Class=${det.name}, Conf=${String.format("%.2f", det.confidence)}, Box=${det.boundingBox}")
        }
        // *** End Logging ***

        // Sort by confidence in descending order
        val sortedDetections = detections.sortedByDescending { it.confidence }

        val selectedDetections = mutableListOf<BillInference>()
        val active = BooleanArray(sortedDetections.size) { true }
        var numActive = active.size

        for (i in sortedDetections.indices) {
            if (active[i]) {
                val currentBox = sortedDetections[i]
                selectedDetections.add(currentBox)
                active[i] = false
                numActive--

                if (numActive == 0) break

                for (j in (i + 1) until sortedDetections.size) {
                    if (active[j]) {
                        val otherBox = sortedDetections[j]
                        val iou = calculateIoU(currentBox.boundingBox, otherBox.boundingBox)

                        if (iou >= iouThreshold) {
                            active[j] = false
                            numActive--
                        }
                    }
                }
                if (numActive == 0) break
            }
        }
        Log.d("BillDetectorNMS", "--- Exiting NMS with ${selectedDetections.size} detections ---") // Log count after
        return selectedDetections
    }

    internal fun calculateIoU(box1: RectF, box2: RectF): Float {
        Log.v("BillDetectorIoU", "Calculating IoU for:")
        Log.v("BillDetectorIoU", "  Box1: $box1")
        Log.v("BillDetectorIoU", "  Box2: $box2")

        val xA = max(box1.left, box2.left)
        val yA = max(box1.top, box2.top)
        val xB = min(box1.right, box2.right)
        val yB = min(box1.bottom, box2.bottom)

        // Calculate intersection area
        val intersectionWidth = max(0f, xB - xA)
        val intersectionHeight = max(0f, yB - yA)
        val intersectionArea = intersectionWidth * intersectionHeight

        // Calculate union area
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        // Ensure areas are non-negative
        val validBox1Area = max(0f, box1Area)
        val validBox2Area = max(0f, box2Area)
        val unionArea = validBox1Area + validBox2Area - intersectionArea

        // Compute IoU
        val iou = if (unionArea > 0f) intersectionArea / unionArea else 0f

        // *** Add Logging Here ***
        Log.v("BillDetectorIoU", "  Intersection: A=$intersectionArea (W=$intersectionWidth, H=$intersectionHeight)")
        Log.v("BillDetectorIoU", "  Union: A=$unionArea (Area1=$validBox1Area, Area2=$validBox2Area)")
        Log.v("BillDetectorIoU", "  Result IoU: $iou")
        // *** End Logging ***

        // Ensure IoU is between 0 and 1 (sanity check)
        return max(0f, min(1f, iou))
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        Log.i("BillDetector", "Interpretor closed")
    }
}