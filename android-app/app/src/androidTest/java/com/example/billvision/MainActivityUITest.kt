package com.example.billvision

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun whenCameraPermissionGranted_mainScreenIsDisplayed() {
        composeTestRule.setContent {
            MainScreen(onCameraButtonClicked = {})
        }

        composeTestRule
            .onNodeWithContentDescription("Open the camera to identify bills")
            .assertIsDisplayed()
    }

    @Test
    fun whenCameraPermissionNotGranted_permissionScreenIsDisplayed() {
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
    }

}
