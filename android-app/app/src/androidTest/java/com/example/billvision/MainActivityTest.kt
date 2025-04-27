package com.example.billvision

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

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
}
