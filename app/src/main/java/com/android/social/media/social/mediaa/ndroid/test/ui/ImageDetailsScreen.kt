package com.android.social.media.social.mediaa.ndroid.test.ui

import android.graphics.Bitmap
import android.util.Log
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageProcessor

enum class ProcessorType(val displayName: String) {
    COROUTINES("Kotlin Coroutines"),
    RX_JAVA("RxJava"),
    THREADS("Java Threads")
}

sealed class ProcessingImageSize(
    val width: Int,
    val height: Int,
    val label: String,
    val uiSize: Int
) {
    object Small : ProcessingImageSize(150, 150, "Small", 120)
    object Medium : ProcessingImageSize(640, 640, "Medium", 220)
    object Large : ProcessingImageSize(1080, 1080, "Large", 320)

    companion object {
        fun values(): List<ProcessingImageSize> = listOf(Small, Medium, Large)
    }
}

@Composable
fun ImageDetailScreen(
    initialBitmap: Bitmap,
    onBack: () -> Unit,
    coroutinesProcessor: ImageProcessor,
    rxJavaProcessor: ImageProcessor,
    threadsProcessor: ImageProcessor,
) {
    var selectedProcessorType by remember { mutableStateOf(ProcessorType.COROUTINES) }
    var selectedProcessingSize by remember { mutableStateOf<ProcessingImageSize>(ProcessingImageSize.Medium) }
    var processedBitmap by remember { mutableStateOf(initialBitmap) }
    var processingTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedProcessingSize, selectedProcessorType) {
        processedBitmap = initialBitmap
        processingTime = 0L
        processingErrorMessage = null
        isProcessing = false
    }

    val currentUISize = when (selectedProcessingSize) {
        ProcessingImageSize.Small -> 120.dp
        ProcessingImageSize.Medium -> 220.dp
        ProcessingImageSize.Large -> 320.dp
    }

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
                    ), enabled = !isProcessing
                ) {
                    Text(sizeOption.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Processing method:", modifier = Modifier.padding(horizontal = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProcessorType.values().forEach { processorType ->
                Button(
                    onClick = { selectedProcessorType = processorType },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedProcessorType == processorType) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ), enabled = !isProcessing
                ) {
                    Text(processorType.displayName)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = processedBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(currentUISize)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp)
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

                val currentProcessor = when (selectedProcessorType) {
                    ProcessorType.COROUTINES -> coroutinesProcessor
                    ProcessorType.RX_JAVA -> rxJavaProcessor
                    ProcessorType.THREADS -> threadsProcessor
                }
                currentProcessor.processSingleImageParallel(
                    originalBitmap = initialBitmap,
                    targetWidth = selectedProcessingSize.width,
                    targetHeight = selectedProcessingSize.height,
                    onSuccess = { bitmap, time ->
                        Log.d(
                            "ImageDetailScreen",
                            "Result bitmap size: ${bitmap.width}x${bitmap.height}"
                        )
                        processedBitmap = bitmap
                        processingTime = time
                        isProcessing = false
                    },
                    onError = { throwable ->
                        processingErrorMessage = "Error: ${throwable.message}"
                        isProcessing = false
                        Log.e("ImageDetailScreen", "Processing error", throwable)
                    }
                )
            },
            enabled = !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text("Apply filter (${selectedProcessorType.displayName})")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (processingTime > 0) {
            Text(
                "Process Time: $processingTime мс (${selectedProcessorType.displayName})",
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else if (!processingErrorMessage.isNullOrBlank()) {
            Text(
                "Error: $processingErrorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}