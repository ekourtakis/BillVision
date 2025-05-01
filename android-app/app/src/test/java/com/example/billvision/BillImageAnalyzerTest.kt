package com.example.billvision

import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.billvision.ml.BillDetector
import com.example.billvision.ml.BillImageAnalyzer
import com.example.billvision.model.BillInference
import com.example.billvision.model.ImageDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.lenient
import org.mockito.kotlin.*
import kotlin.test.assertFalse


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class BillImageAnalyzerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Mocks ---
    private lateinit var mockDetector: BillDetector
    private lateinit var mockImageProxy: ImageProxy
    private lateinit var mockImage: Image // Needed for null check path
    private lateinit var mockImageInfo: ImageInfo

    // Callback verification setup
    private var onResultsCalled: Boolean = false
    private val testOnResultsCallback: (List<BillInference>, ImageDimensions) -> Unit = { _, _ ->
        onResultsCalled = true
    }

    private lateinit var analyzer: BillImageAnalyzer

    @Before
    fun setupForEachTest() {
        // reset mocks and flags
        mockDetector = mock()
        mockImageProxy = mock()
        mockImage = mock()
        mockImageInfo = mock()
        onResultsCalled = false

        // mocking for ImageProxy methods used *before* conversion
        lenient().whenever(mockImageProxy.image).thenReturn(mockImage) // default to non-null image
        lenient().whenever(mockImageProxy.imageInfo).thenReturn(mockImageInfo)
        lenient().whenever(mockImageProxy.width).thenReturn(640)
        lenient().whenever(mockImageProxy.height).thenReturn(480)
        lenient().whenever(mockImageInfo.timestamp).thenReturn(12345L)

        analyzer = BillImageAnalyzer(
            detector = mockDetector,
            onResults = testOnResultsCallback,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun cleanupAfterTest() {
        analyzer.close()
    }


    @Test
    fun `analyze skips frames correctly and closes proxy`() = runTest(testDispatcher) {
        val skipFrames = 5 // from  Analyzer class
        // one less than the number to skip
        for (i in 0 until skipFrames) {
            val proxy: ImageProxy = mock { on { imageInfo }.thenReturn(mock()) }
            analyzer.analyze(proxy)
            verify(proxy).close()
        }

        // detector and callback were NOT involved during skipped frames
        verifyNoInteractions(mockDetector)
        assertFalse(onResultsCalled, "onResults should not be called during skip")
    }

    @Test
    fun `analyze handles null image from proxy and closes proxy`() = runTest(testDispatcher) {
        // frame skipping is handled
        val skipFrames = 5
        repeat(skipFrames) {
            analyzer.analyze(
                mock { on { imageInfo }.thenReturn(mock()) }
            )
        }

        // mockProxy returns null image
        whenever(mockImageProxy.image).thenReturn(null)

        analyzer.analyze(mockImageProxy)
        testDispatcher.scheduler.advanceUntilIdle()

        // the specific mock proxy used in *this* call was closed
        verify(mockImageProxy).close()
        // detector was never called
        verifyNoInteractions(mockDetector)
        // callback was not called
        assertFalse(onResultsCalled, "onResults should not be called for null image")
    }

    @Test
    fun `close cancels scope and closes detector`() = runTest(testDispatcher) {
        analyzer.close()
        testDispatcher.scheduler.advanceUntilIdle() // allow close actions to run

        // detector's close method was called
        verify(mockDetector).close()

        // attempting to analyze after close results in immediate proxy close and nothing else
        val proxyAfterClose: ImageProxy = mock { on { imageInfo }.thenReturn(mock()) }
        analyzer.analyze(proxyAfterClose)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(proxyAfterClose).close()
        // detector was not called AGAIN after close()
        verifyNoMoreInteractions(mockDetector)
    }

    @Test
    fun `close is idempotent`() = runTest(testDispatcher) {
        // Call close multiple times
        analyzer.close()
        analyzer.close()
        analyzer.close()
        testDispatcher.scheduler.advanceUntilIdle()

        // detector's close method was only called ONCE
        verify(mockDetector, times(1)).close()
    }
}