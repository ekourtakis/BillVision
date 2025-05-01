package com.example.billvision

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.billvision.ml.BillDetector
import com.example.billvision.model.BillInference
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [BillDetector] logic.
 */
@RunWith(AndroidJUnit4::class)
class BillDetectorTest {

    private lateinit var context: Context
    private lateinit var dummyDetector: BillDetector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dummyDetector = BillDetector(context, modelPath = "dummy_path.tflite")
    }

    // --- calculateIoU tests ---
    @Test
    fun `calculateIoU returns 1 for identical boxes`() {
        val box = RectF(10f, 10f, 50f, 50f)
        val expectedIoU = 1.0f
        val actualIoU = dummyDetector.calculateIoU(box, box)
        assertEquals(expectedIoU, actualIoU, 0.001f) // Use delta for float comparison
    }

    @Test
    fun `calculateIoU returns 0 for non-overlapping boxes`() {
        val box1 = RectF(10f, 10f, 20f, 20f)
        val box2 = RectF(30f, 30f, 40f, 40f)
        val expectedIoU = 0.0f
        val actualIoU = dummyDetector.calculateIoU(box1, box2)
        assertEquals(expectedIoU, actualIoU, 0.001f)
    }

    @Test
    fun `calculateIoU returns correct value for partially overlapping boxes`() {
        val box1 = RectF(10f, 10f, 30f, 30f) // Area = 20*20 = 400
        val box2 = RectF(20f, 20f, 40f, 40f) // Area = 20*20 = 400
        // Intersection: xA=20, yA=20, xB=30, yB=30 -> Width=10, Height=10 -> Area = 100
        // Union = Area1 + Area2 - Intersection = 400 + 400 - 100 = 700
        // IoU = Intersection / Union = 100 / 700 = 1/7
        val expectedIoU = 1.0f / 7.0f
        val actualIoU = dummyDetector.calculateIoU(box1, box2)
        assertEquals(expectedIoU, actualIoU, 0.001f) // Delta for float comparison
    }

    @Test
    fun `calculateIoU returns correct value when one box contains another`() {
        val outerBox = RectF(0f, 0f, 100f, 100f)   // Area = 10000
        val innerBox = RectF(25f, 25f, 75f, 75f)   // Area = 50*50 = 2500
        // Intersection = Area of inner box = 2500
        // Union = Area of outer box = 10000
        // IoU = 2500 / 10000 = 0.25
        val expectedIoU = 0.25f
        val actualIoU = dummyDetector.calculateIoU(outerBox, innerBox)
        assertEquals(expectedIoU, actualIoU, 0.001f)

        // Test the other way around too
        val actualIoUReversed = dummyDetector.calculateIoU(innerBox, outerBox)
        assertEquals(expectedIoU, actualIoUReversed, 0.001f)
    }

    @Test
    fun `calculateIoU handles boxes with zero area`() {
        val box1 = RectF(10f, 10f, 30f, 30f)
        val zeroAreaBoxLine = RectF(40f, 40f, 40f, 50f) // Line (width 0)
        val zeroAreaBoxPoint = RectF(50f, 50f, 50f, 50f) // Point (width/height 0)

        assertEquals(0.0f, dummyDetector.calculateIoU(box1, zeroAreaBoxLine), 0.001f)
        assertEquals(0.0f, dummyDetector.calculateIoU(box1, zeroAreaBoxPoint), 0.001f)
        assertEquals(0.0f, dummyDetector.calculateIoU(zeroAreaBoxLine, zeroAreaBoxPoint), 0.001f)
    }

    @Test
    fun `calculateIoU handles overlapping boxes touching edges`() {
        val box1 = RectF(10f, 10f, 20f, 20f) // Area = 100
        val box2 = RectF(20f, 10f, 30f, 20f) // Area = 100, touches right edge of box1
        // Intersection = 0 (only edge touch)
        // Union = 100 + 100 - 0 = 200
        // IoU = 0 / 200 = 0
        assertEquals(0.0f, dummyDetector.calculateIoU(box1, box2), 0.001f)

        val box3 = RectF(10f, 20f, 20f, 30f) // Area = 100, touches bottom edge of box1
        assertEquals(0.0f, dummyDetector.calculateIoU(box1, box3), 0.001f)
    }

    // --- applyNMS tests ---
    @Test
    fun `applyNMS returns empty list for empty input`() {
        val result = dummyDetector.applyNMS(emptyList<BillInference>())
        assertTrue(result.isEmpty(), "Result should be empty for empty input")
    }

    @Test
    fun `applyNMS keeps single detection`() {
        val singleDetection = listOf(
            BillInference("1 dollar", 0.95f, RectF(10f, 10f, 20f, 20f))
        )

        val result = dummyDetector.applyNMS(singleDetection)
        assertEquals(1, result.size, "Should keep the single detection")
        assertEquals("1 dollar", result[0].name)
    }

    @Test
    fun `applyNMS keeps non-overlapping boxes`() {
        val detections = listOf(
            BillInference("5 dollar", 0.98f, RectF(10f, 10f, 20f, 20f)), // High confidence
            BillInference("10 dollar", 0.95f, RectF(30f, 30f, 40f, 40f)) // Lower confidence, no overlap
        )
        val result = dummyDetector.applyNMS(detections)
        assertEquals(2, result.size, "Should keep both non-overlapping boxes")
        // order might change based on confidence, but both should be present
        assertTrue(result.any { it.name == "5 dollar" })
        assertTrue(result.any { it.name == "10 dollar" })
    }

    @Test
    fun `applyNMS suppresses lower confidence box with high overlap`() {
        // default IoU threshold of 0.45f
        val box1 = RectF(10f, 10f, 30f, 30f) // area 400
        val box2 = RectF(12f, 12f, 32f, 32f) // area 400, High overlap with box1 (IoU > 0.45)

        val detections = listOf(
            BillInference("Keep", 0.99f, box1),       // higher confidence
            BillInference("Suppress", 0.90f, box2)   // lower confidence, overlaps highly
        )
        val result = dummyDetector.applyNMS(detections)
        assertEquals(1, result.size, "Should suppress the lower confidence overlapping box")
        assertEquals("Keep", result[0].name) // only the higher confidence one should remain
    }

    @Test
    fun `applyNMS keeps lower confidence box if overlap is below threshold`() {
        // default IoU threshold of 0.45f
        val box1 = RectF(10f, 10f, 30f, 30f) // area 400
        val box2 = RectF(25f, 25f, 45f, 45f) // area 400
        // Intersection: xA=25, yA=25, xB=30, yB=30 -> W=5, H=5 -> Area = 25
        // Union = 400 + 400 - 25 = 775
        // IoU = 25 / 775 ~= 0.032 (which is < 0.45 threshold)

        val detections = listOf(
            BillInference("Keep1", 0.99f, box1), // higher confidence
            BillInference("Keep2", 0.90f, box2)  // lower confidence, low overlap
        )
        val result = dummyDetector.applyNMS(detections)
        assertEquals(2, result.size, "Should keep both boxes due to low overlap")
        assertTrue(result.any { it.name == "Keep1" })
        assertTrue(result.any { it.name == "Keep2" })
    }

    @Test
    fun `applyNMS handles multiple overlapping boxes correctly`() {
        // high to low confidence
        val box1 = RectF(15f, 15f, 35f, 35f) // Area = 20*20 = 400
        val box2 = RectF(16f, 16f, 36f, 36f) // Area = 400

        // Intersection: xA=16, yA=16, xB=35, yB=35 -> W=19, H=19 -> Area = 361
        // Union = 400 + 400 - 361 = 439
        // IoU = 361 / 439 ~= 0.82 (>= 0.45, should suppress)

        // Box 3 (Lowest confidence) - Design for HIGH overlap with Box 2
        val box3 = RectF(14f, 14f, 34f, 34f) // Area = 400
        // Intersection: xA=15, yA=15, xB=34, yB=34 -> W=19, H=19 -> Area = 361
        // Union = 400 + 400 - 361 = 439
        // IoU = 361 / 439 ~= 0.82 (>= 0.45, should suppress)


        val detections = listOf(
            BillInference("Box 2", 0.95f, box1), // highest confidence
            BillInference("Box 1", 0.90f, box2), // lower conf, high overlap
            BillInference("Box 3", 0.85f, box3)  // lowest conf, high overlap
        )

        // Expected: Box 1 suppressed by Box 2. Box 3 suppressed by Box 2.
        // Result should only contain Box 2.
        val result = dummyDetector.applyNMS(detections)

        // only one left
        assertEquals(1, result.size, "Should keep only the highest confidence box among highly overlapping ones")
        // the remaining box is box2
        assertEquals("Box 2", result[0].name)
    }
}