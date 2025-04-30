package com.example.billvision

import android.graphics.RectF
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.example.billvision.model.BillInference
import com.example.billvision.model.ImageDimensions
import com.example.billvision.ui.camera.CameraPreviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class CameraPreviewViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CameraPreviewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CameraPreviewViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onAnalysisResult updates classifications flow correctly`() = runTest {
        val initialExpected = emptyList<BillInference>()
        val testResults1 = listOf(BillInference("10 dollar", 0.9f, RectF()))
        val testResults2 = listOf(BillInference("5 dollar", 0.95f, RectF()))
        val emptyResults = emptyList<BillInference>()

        viewModel.classifications.test {
            assertEquals(initialExpected, awaitItem())
            // Pass ImageDimensions instance
            viewModel.onAnalysisResult(testResults1, ImageDimensions(100, 100))
            assertEquals(testResults1, awaitItem())
            viewModel.onAnalysisResult(testResults2, ImageDimensions.ZERO)
            assertEquals(testResults2, awaitItem())
            viewModel.onAnalysisResult(emptyResults, ImageDimensions(200, 200))
            assertEquals(emptyResults, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onAnalysisResult updates imageSize flow correctly with valid sizes`() = runTest {
        val initialExpected = ImageDimensions.ZERO
        val testSize1 = ImageDimensions(640, 480)
        val testSize2 = ImageDimensions(1280, 720)
        val dummyResults = listOf(BillInference("Any", 0.9f, RectF()))

        viewModel.imageDimensions.test {
            assertEquals(initialExpected, awaitItem())
            viewModel.onAnalysisResult(dummyResults, testSize1)
            assertEquals(testSize1, awaitItem())
            viewModel.onAnalysisResult(dummyResults, testSize2)
            assertEquals(testSize2, awaitItem())
            viewModel.onAnalysisResult(dummyResults, testSize2)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `onAnalysisResult does not update imageSize flow with invalid sizes`() = runTest {
        val initialExpected = ImageDimensions.ZERO
        val invalidSizeZero = ImageDimensions.ZERO
        val invalidSizeNegative = ImageDimensions(-100, 100)
        val dummyResults = listOf(BillInference("Any", 0.9f, RectF()))
        val validSize = ImageDimensions(100, 100)

        viewModel.imageDimensions.test {
            assertEquals(initialExpected, awaitItem())
            viewModel.onAnalysisResult(dummyResults, invalidSizeZero)
            expectNoEvents()
            viewModel.onAnalysisResult(dummyResults, invalidSizeNegative)
            expectNoEvents()
            viewModel.onAnalysisResult(dummyResults, validSize)
            assertEquals(validSize, awaitItem())
            viewModel.onAnalysisResult(dummyResults, invalidSizeZero)
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }
}