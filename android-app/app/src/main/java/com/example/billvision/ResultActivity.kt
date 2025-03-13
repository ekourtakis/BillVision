package com.example.billvision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val inference = intent.getSerializableExtra(MainActivity.EXTRA_INFERENCE, BillInference::class.java)
        if (inference == null) {
            finish()
            return
        }

        var bitmap = BitmapFactory.decodeFile(inference.photoPath)

        // make the image square
        val dimension = bitmap.height.coerceAtMost(bitmap.width)
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, dimension, dimension)

        setContent {
            ResultDisplay(
                BillLabel.fromIndex(inference.billLabelIndex).name,
                inference.confidence,
                bitmap
            )
        }
    }

    @Composable
    private fun ResultDisplay(
        name: String, confidence: Float, bitmap: Bitmap
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Photo taken")

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Bill: $name",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Confidence: %.2f".format(confidence),
                fontSize = 20.sp
            )
            Button(
                onClick = { finish() }
            ) {
                Text("Take another photo")
            }
        }

    }
}
