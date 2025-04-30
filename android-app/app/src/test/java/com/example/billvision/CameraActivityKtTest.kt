package com.example.billvision

import android.graphics.RectF
import com.example.billvision.activity.createAnnouncementString
import com.example.billvision.model.BillInference
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit tests for functions within CameraActivity.kt
 */
class CameraActivityKtTest {

    // Empty list of classifications
    @Test
    fun `createAnnouncementString with empty list returns placeholder`() {
        val result = createAnnouncementString(emptyList<BillInference>())
        assertEquals("Point camera at a bill...", result)
    }

    // Single classification
    @Test
    fun `createAnnouncementString with single item returns correct singular string`() {
        val singleItemList = listOf(
            BillInference(
                name = "5 dollar",
                confidence = 0.95f,
                boundingBox = RectF()
            )
        )

        val result = createAnnouncementString(singleItemList)
        assertEquals("Detected 1 bill: 5 dollar", result)
    }

    // Multiple classifications
    @Test
    fun `createAnnouncementString with multiple items returns correct plural string`() {
        val multipleItemList = listOf(
            BillInference(name = "10 dollar", confidence = 0.92f, boundingBox = RectF()),
            BillInference(name = "1 dollar", confidence = 0.98f, boundingBox = RectF()),
            BillInference(name = "20 dollar", confidence = 0.91f, boundingBox = RectF())
        )

        val result = createAnnouncementString(multipleItemList)
        assertEquals("Detected 3 bills: [10 dollar, 1 dollar, 20 dollar]", result)
    }
}