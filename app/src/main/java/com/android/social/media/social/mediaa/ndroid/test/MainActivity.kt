package com.android.social.media.social.mediaa.ndroid.test

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.social.media.social.mediaa.ndroid.test.data.CoroutinesImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.CoroutinesOkHttpImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.RxJavaImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.ThreadsImageProcessor
import com.android.social.media.social.mediaa.ndroid.test.data.Utils
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageDownloader
import com.android.social.media.social.mediaa.ndroid.test.ui.theme.SocialMediaAndroidTestTheme
import java.lang.Math.log

const val NUMBER_OF_IMAGES = 100

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ініціалізуємо Coil ImageLoader ОДИН РАЗ при старті додатку
        // Це важливо для CoilImageProcessor
        Utils.initializeImageLoader(applicationContext)

        setContent {
            SocialMediaAndroidTestTheme { // Застосовуємо вашу тему
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent() // Основний Composable для нашого додатка
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current

    val coroutinesProcessor = remember { CoroutinesImageProcessor(context) }
    val coroutinesOkHttpProcessor = remember { CoroutinesOkHttpImageProcessor(context) }
    val rxJavaProcessor = remember { RxJavaImageProcessor() }
    val threadsProcessor = remember { ThreadsImageProcessor() }

    // Змінні стану для відображення часу завантаження
    var coroutinesCoilDownloadTime by remember { mutableStateOf("N/A") }
    var coroutinesOkHttpDownloadTime by remember { mutableStateOf("N/A") }
    var rxJavaDownloadTime by remember { mutableStateOf("N/A") }
    var threadsDownloadTime by remember { mutableStateOf("N/A") }

    // Змінні стану для відображення часу обробки
    var coroutinesCoilProcessTime by remember { mutableStateOf("N/A") }
    var coroutinesOkHttpProcessTime by remember { mutableStateOf("N/A") }
    var rxJavaProcessTime by remember { mutableStateOf("N/A") }
    var threadsProcessTime by remember { mutableStateOf("N/A") }

    val imageUrls = remember {
        (1..NUMBER_OF_IMAGES).map { id -> Utils.getImageUrl(id) }
    }

    val downloadedBitmaps = remember { mutableStateMapOf<Int, Bitmap?>() }
    var currentImageProcessor by remember { mutableStateOf<ImageDownloader?>(null) }
    var lastProcessedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Завантаження 100 зображень (I/O Bound):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Coroutines (OkHttp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                Log.i("Coroutines!!!", "--- Запускаємо тест завантаження: Coroutines (OkHttp) ---")
                currentImageProcessor = coroutinesOkHttpProcessor // Встановлюємо активний процесор
                coroutinesOkHttpProcessor.downloadImages(
                    imageUrls = imageUrls,
                    onProgress = { index, bitmap ->
                        downloadedBitmaps[index] = bitmap
                    },
                    onComplete = { time ->
                        coroutinesOkHttpDownloadTime = "${time}ms"
                        Log.i("Coroutines!!!", "Coroutines (OkHttp) завершено за ${time}ms.")
                    },
                    onError = { error ->
                        Log.i("Coroutines!!!", "Помилка Coroutines (OkHttp): ${error.message}")
                    }
                )
            }) { Text("Coroutines (OkHttp)") }
            Text("Час: $coroutinesOkHttpDownloadTime", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    Log.i("RX!!!", "--- Запускаємо тест завантаження: RxJava ---")
                    currentImageProcessor = rxJavaProcessor // Встановлюємо активний процесор
                    rxJavaProcessor.downloadImages(
                        imageUrls = imageUrls,
                        onProgress = { index, bitmap ->
                            downloadedBitmaps[index] =
                                bitmap // RxJava onProgress буде викликатися для кожного Bitmap
                        },
                        onComplete = { time ->
                            rxJavaDownloadTime = "${time}ms"
                            Log.i("RX!!!", "RxJava завершено за ${time}ms.")
                        },
                        onError = { error ->
                            Log.i("RX!!!", "Помилка RxJava: ${error.message}")
                        }
                    )
                }) { Text("RxJava") }
                Text("Час: $rxJavaDownloadTime", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Java Threads
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                Log.i("THREADS!!!", "--- Запускаємо тест завантаження: Java Threads ---")
                currentImageProcessor = threadsProcessor // Встановлюємо активний процесор
                threadsProcessor.downloadImages(
                    imageUrls = imageUrls,
                    onProgress = { index, bitmap ->
                        downloadedBitmaps[index] = bitmap
                    },
                    onComplete = { time ->
                        threadsDownloadTime = "${time}ms"
                        Log.i("THREADS!!!", "Java Threads завершено за ${time}ms.")
                    },
                    onError = { error ->
                        Log.i("THREADS!!!", "Помилка Java Threads: ${error.message}")
                    }
                )
            }) { Text("Java Threads") }
            Text("Час: $threadsDownloadTime", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))


        DisposableEffect(Unit) {
            onDispose {
                coroutinesProcessor.cancelOperations()
                coroutinesOkHttpProcessor.cancelOperations()
                rxJavaProcessor.cancelOperations()
                threadsProcessor.cancelOperations()
                Log.i("Cancellation State: ", "All coroutines are cancelled")
            }
        }
    }
}

