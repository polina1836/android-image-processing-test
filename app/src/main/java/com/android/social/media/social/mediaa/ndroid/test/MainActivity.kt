package com.android.social.media.social.mediaa.ndroid.test

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.social.media.social.mediaa.ndroid.test.data.CoroutinesOkHttpImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.RxJavaImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.ThreadsImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.Utils
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.ui.ImageDetailScreen
import com.android.social.media.social.mediaa.ndroid.test.ui.ScreenState
import com.android.social.media.social.mediaa.ndroid.test.ui.theme.SocialMediaAndroidTestTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils.initializeOkHttpClient(applicationContext)
        Utils.initializeImageLoader(applicationContext)

        setContent {
            SocialMediaAndroidTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    ImageDownloaderScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDownloaderScreen() {

    var currentScreen: ScreenState by remember { mutableStateOf(ScreenState.ImageGrid) }
    val coroutineScope = rememberCoroutineScope()
    val coroutinesOkHttpProcessor = remember { CoroutinesOkHttpImageProcessor() }
    val rxJavaProcessor = remember { RxJavaImageProcessor() }
    val threadsProcessor = remember { ThreadsImageProcessor() }

    val coroutinesOkHttpDownloadTime = remember { mutableStateOf("N/A") }
    val rxJavaDownloadTime = remember { mutableStateOf("N/A") }
    val threadsDownloadTime = remember { mutableStateOf("N/A") }

    val downloadedBitmaps = remember { mutableStateMapOf<Int, Bitmap?>() }
    var downloadTime by remember { mutableStateOf("N/A") }
    var isDownloading by remember { mutableStateOf(false) }
    var currentTestName by remember { mutableStateOf("") }
    var selectedNumberOfImages by remember { mutableStateOf(100) }

    val logger = remember { mutableStateListOf<String>() }
    fun log(message: String) {
        Log.d("ImageDownloaderScreen", message)
        logger.add(0, message)
        if (logger.size > 20) logger.removeAt(logger.size - 1)
    }

    when (val screen = currentScreen) {
        ScreenState.ImageGrid -> {

            DisposableEffect(Unit) {
                onDispose {
                    coroutinesOkHttpProcessor.cancelOperations()
                    rxJavaProcessor.cancelOperations()
                    threadsProcessor.cancelOperations()
                    Log.d("ImageDownloaderScreen", "Всі операції завантаження скасовано.")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val startDownloadTest: (ImageProcessor, String, MutableState<String>) -> Unit =
                    { processor, name, timeState ->
                        if (!isDownloading) {
                            isDownloading = true
                            currentTestName = name
                            timeState.value = "Downloading..."
                            downloadedBitmaps.clear()
                            log("--- Starting download test: $name ---")

                            coroutineScope.launch {
                                processor.downloadImages(
                                    imageUrls = (0 until selectedNumberOfImages).map { id ->
                                        Utils.getImageUrl(
                                            id + 1
                                        )
                                    },
                                    onProgress = { index, bitmap ->
                                        downloadedBitmaps[index] = bitmap
                                    },
                                    onComplete = { time ->
                                        timeState.value = "${time}мс"
                                        isDownloading = false
                                        log("$name завершено за ${time}мс.")
                                    },
                                    onError = { error ->
                                        timeState.value = "Помилка!"
                                        isDownloading = false
                                        log("Помилка в $name: ${error.localizedMessage ?: "Невідома помилка"}")
                                    }
                                )
                            }
                        }
                    }

                Text(
                    "Select image count:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { selectedNumberOfImages = 100 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedNumberOfImages == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !isDownloading
                    ) { Text("100") }
                    Button(
                        onClick = { selectedNumberOfImages = 500 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedNumberOfImages == 500) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !isDownloading
                    ) { Text("500") }
                    Button(
                        onClick = { selectedNumberOfImages = 1000 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedNumberOfImages == 1000) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        enabled = !isDownloading
                    ) { Text("1000") }
                }

                Text(
                    "Download 100 Images:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                startDownloadTest(
                                    coroutinesOkHttpProcessor,
                                    "Coroutines (OkHttp)",
                                    coroutinesOkHttpDownloadTime
                                )
                            },
                            enabled = !isDownloading
                        ) { Text("Coroutines (OkHttp)") }
                        Text(
                            "Time: ${coroutinesOkHttpDownloadTime.value}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                startDownloadTest(
                                    rxJavaProcessor,
                                    "RxJava",
                                    rxJavaDownloadTime
                                )
                            },
                            enabled = !isDownloading
                        ) { Text("RxJava") }
                        Text(
                            "Time: ${rxJavaDownloadTime.value}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = {
                                startDownloadTest(
                                    threadsProcessor,
                                    "Java Threads",
                                    threadsDownloadTime
                                )
                            },
                            enabled = !isDownloading
                        ) { Text("Java Threads") }
                        Text(
                            "Time: ${threadsDownloadTime.value}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isDownloading) {
                    val progress =
                        downloadedBitmaps.size.toFloat() / selectedNumberOfImages.toFloat()
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Downloading ($currentTestName): ${downloadedBitmaps.size}/${selectedNumberOfImages}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (downloadedBitmaps.isNotEmpty()) {
                    Text(
                        "Download completed ($currentTestName)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        "Press a button to start downloading images.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(List(selectedNumberOfImages) { it }) { index, _ ->
                        val bitmap = downloadedBitmaps[index]
                        ImageItem(bitmap = bitmap, index = index, onClick = { clickedBitmap ->
                            if (clickedBitmap != null) {
                                currentScreen = ScreenState.ImageDetail(index, clickedBitmap)
                            }
                        })
                    }
                }
            }
        }

        is ScreenState.ImageDetail -> {
            ImageDetailScreen(
                initialBitmap = screen.bitmap,
                onBack = {
                    currentScreen = ScreenState.ImageGrid
                },
                coroutinesProcessor = coroutinesOkHttpProcessor,
                rxJavaProcessor = rxJavaProcessor,
                threadsProcessor = threadsProcessor,
            )
        }
    }
}

@Composable
fun ImageItem(bitmap: Bitmap?, index: Int, onClick: (Bitmap?) -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray)
            .clickable(onClick = { onClick(bitmap) }),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Image ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.LightGray)
            )
        }
    }
}