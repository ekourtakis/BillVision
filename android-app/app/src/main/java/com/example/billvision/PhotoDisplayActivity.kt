package com.example.billvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import coil.compose.rememberAsyncImagePainter
import java.io.File

class PhotoDisplayActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PHOTO_PATH = "photo_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)
        if (photoPath == null) {
            finish()
            return
        }

        setContent {
            PhotoDisplay(photoPath)
        }
    }
}

@Composable
private fun PhotoDisplay(photoPath: String) {
    val painter: Painter = rememberAsyncImagePainter(File(photoPath))
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painter, contentDescription = "Photo taken")
    }
} 