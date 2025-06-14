package com.android.social.media.social.mediaa.ndroid.test.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageDownloader

sealed class ProcessingImageSize(val width: Int, val height: Int, val label: String) {
    object Small : ProcessingImageSize(150, 150, "Small")
    object Medium : ProcessingImageSize(640, 640, "Medium")
    object Large : ProcessingImageSize(1080, 1080, "Large")

    companion object {
        fun values(): List<ProcessingImageSize> = listOf(Small, Medium, Large)
    }
}

@Composable
fun ImageDetailScreen(
    initialBitmap: Bitmap,
    onBack: () -> Unit,
    coroutinesProcessor: ImageDownloader,
    rxJavaProcessor: ImageDownloader,
    threadsProcessor: ImageDownloader,
) {
    var selectedProcessingSize by remember { mutableStateOf<ProcessingImageSize>(ProcessingImageSize.Medium) }
    var processedBitmap by remember { mutableStateOf(initialBitmap) }
    var processingTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text("Image sizes:", modifier = Modifier.padding(horizontal = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProcessingImageSize.values().forEach { sizeOption ->
                Button(
                    onClick = { selectedProcessingSize = sizeOption },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedProcessingSize == sizeOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),  enabled = !isProcessing
                ) { Text(sizeOption.label) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.DarkGray.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = processedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(50.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                processingTime = 0L
                isProcessing = true
                processingErrorMessage = null

                coroutinesProcessor.processSingleImageParallel(
                    originalBitmap = initialBitmap,
                    targetWidth = selectedProcessingSize.width,
                    targetHeight = selectedProcessingSize.height,
                    onSuccess = { bitmap, time ->
                        processedBitmap = bitmap
                        processingTime = time
                        isProcessing = false
                    },
                    onError = { throwable ->
                        processingErrorMessage = "Error: ${throwable.message}"
                        isProcessing = false
                    }
                )
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text("Apply filter (Coroutines)")
        }
        Button(
            onClick = {
                processingTime = 0L
                isProcessing = true
                processingErrorMessage = null

                rxJavaProcessor.processSingleImageParallel(
                    originalBitmap = initialBitmap,
                    targetWidth = selectedProcessingSize.width,
                    targetHeight = selectedProcessingSize.height,
                    onSuccess = { bitmap, time ->
                        processedBitmap = bitmap
                        processingTime = time
                        isProcessing = false
                    },
                    onError = { throwable ->
                        processingErrorMessage = "Error: ${throwable.message}"
                        isProcessing = false
                    }
                )
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text("Apply filter (Rx Java)")
        }
        Button(
            onClick = {
                processingTime = 0L
                isProcessing = true
                processingErrorMessage = null

                threadsProcessor.processSingleImageParallel(
                    originalBitmap = initialBitmap,
                    targetWidth = selectedProcessingSize.width,
                    targetHeight = selectedProcessingSize.height,
                    onSuccess = { bitmap, time ->
                        processedBitmap = bitmap
                        processingTime = time
                        isProcessing = false
                    },
                    onError = { throwable ->
                        processingErrorMessage = "Error: ${throwable.message}"
                        isProcessing = false
                    }
                )
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text("Apply filter (Java Thread)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (processingTime > 0) {
            Text("Час обробки: $processingTime мс", modifier = Modifier.padding(horizontal = 8.dp))
        } else if (!processingErrorMessage.isNullOrBlank()) {
            Text(
                "Помилка: $processingErrorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}