package com.example.billvision

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for individual Composables
 * ANDROID DEVICE'S SCREEN MUST BE ON FOR THESE TESTS TO PASS!
 */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class ComposableTests {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mainScreen_displaysExpectedContent() { // Renamed slightly for clarity
        composeTestRule.setContent {
            MainScreen(onCameraButtonClicked = {})
        }
        composeTestRule
            .onNodeWithContentDescription("Open the camera to identify bills")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Point the camera towards a US dollar bill to identify its denomination.")
            .assertIsDisplayed()
    }

    @Test
    fun mainScreen_clickingButton_callsCallback() { // Renamed slightly for clarity
        var wasClicked = false
        composeTestRule.setContent {
            MainScreen(onCameraButtonClicked = { wasClicked = true })
        }
        composeTestRule
            .onNodeWithContentDescription("Open the camera to identify bills")
            .performClick()
        assertTrue("onCameraButtonClicked callback was not invoked", wasClicked) // Added assertion message
    }

    @Test
    fun permissionScreen_whenDenied_showsInitialPrompt() { // Renamed slightly for clarity
        composeTestRule.setContent {
            PermissionScreen(
                cameraPermissionState = object : PermissionState {
                    override val permission = Manifest.permission.CAMERA
                    override val status = PermissionStatus.Denied(shouldShowRationale = false)
                    override fun launchPermissionRequest() {}
                }
            )
        }
        composeTestRule
            .onNodeWithText("Welcome to BillVision! To get started, please grant us camera permission so the app can see and identify dollar bills.")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Grant Camera Permission")
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_whenRationaleNeeded_showsRationalePrompt() {
        val mockPermissionState = object : PermissionState {
            override val permission: String = Manifest.permission.CAMERA
            override val status: PermissionStatus = PermissionStatus.Denied(shouldShowRationale = true)
            override fun launchPermissionRequest() {}
        }

        composeTestRule.setContent {
            PermissionScreen(cameraPermissionState = mockPermissionState)
        }

        composeTestRule
            .onNodeWithText("To identify dollar bills, BillVision needs permission to access your camera. Please grant the camera permission.")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Grant Camera Permission")
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_clickingGrantButton_callsLaunchPermissionRequest() {
        var permissionRequested = false
        val mockPermissionState = object : PermissionState {
            override val permission: String = Manifest.permission.CAMERA
            override val status: PermissionStatus = PermissionStatus.Denied(shouldShowRationale = false)
            override fun launchPermissionRequest() {
                permissionRequested = true
            }
        }

        composeTestRule.setContent {
            PermissionScreen(cameraPermissionState = mockPermissionState)
        }

        composeTestRule
            .onNodeWithText("Grant Camera Permission")
            .performClick()

        assertTrue("launchPermissionRequest was not called", permissionRequested) // Added assertion message
    }
}