package com.example.billvision

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.billvision.ml.BillDetector
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Unit tests for BillDetector logic, specifically IoU.
 */
@RunWith(AndroidJUnit4::class)
class BillDetectorTest {

    private lateinit var context: Context
    private lateinit var detectorInstanceForIouTest: BillDetector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detectorInstanceForIouTest = BillDetector(context, modelPath = "dummy_path.tflite")
    }
    @Test
    fun `calculateIoU returns 1 for identical boxes`() {
        val box = RectF(10f, 10f, 50f, 50f)
        val expectedIoU = 1.0f
        val actualIoU = detectorInstanceForIouTest.calculateIoU(box, box)
        assertEquals(expectedIoU, actualIoU, 0.001f) // Use delta for float comparison
    }

    @Test
    fun `calculateIoU returns 0 for non-overlapping boxes`() {
        val box1 = RectF(10f, 10f, 20f, 20f)
        val box2 = RectF(30f, 30f, 40f, 40f)
        val expectedIoU = 0.0f
        val actualIoU = detectorInstanceForIouTest.calculateIoU(box1, box2)
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
        val actualIoU = detectorInstanceForIouTest.calculateIoU(box1, box2)
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
        val actualIoU = detectorInstanceForIouTest.calculateIoU(outerBox, innerBox)
        assertEquals(expectedIoU, actualIoU, 0.001f)

        // Test the other way around too
        val actualIoUReversed = detectorInstanceForIouTest.calculateIoU(innerBox, outerBox)
        assertEquals(expectedIoU, actualIoUReversed, 0.001f)
    }

    @Test
    fun `calculateIoU handles boxes with zero area`() {
        val box1 = RectF(10f, 10f, 30f, 30f)
        val zeroAreaBoxLine = RectF(40f, 40f, 40f, 50f) // Line (width 0)
        val zeroAreaBoxPoint = RectF(50f, 50f, 50f, 50f) // Point (width/height 0)

        assertEquals(0.0f, detectorInstanceForIouTest.calculateIoU(box1, zeroAreaBoxLine), 0.001f)
        assertEquals(0.0f, detectorInstanceForIouTest.calculateIoU(box1, zeroAreaBoxPoint), 0.001f)
        assertEquals(0.0f, detectorInstanceForIouTest.calculateIoU(zeroAreaBoxLine, zeroAreaBoxPoint), 0.001f)
    }

    @Test
    fun `calculateIoU handles overlapping boxes touching edges`() {
        val box1 = RectF(10f, 10f, 20f, 20f) // Area = 100
        val box2 = RectF(20f, 10f, 30f, 20f) // Area = 100, touches right edge of box1
        // Intersection = 0 (only edge touch)
        // Union = 100 + 100 - 0 = 200
        // IoU = 0 / 200 = 0
        assertEquals(0.0f, detectorInstanceForIouTest.calculateIoU(box1, box2), 0.001f)

        val box3 = RectF(10f, 20f, 20f, 30f) // Area = 100, touches bottom edge of box1
        assertEquals(0.0f, detectorInstanceForIouTest.calculateIoU(box1, box3), 0.001f)
    }
}