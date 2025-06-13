package com.android.social.media.social.mediaa.ndroid.test.data

import android.content.Context
import android.graphics.Bitmap
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureNanoTime

class CoroutinesOkHttpImageProcessor(private val context: Context) : ImageDownloader {
    private val job = SupervisorJob()

    // Використовуємо Dispatchers.IO для мережевих операцій (блокуючих)
    // та Dispatchers.Default для CPU-інтенсивної обробки.
    private val scope = CoroutineScope(Dispatchers.IO + job) // Основний скоуп для завантаження

    // Кількість частин для паралельної обробки зображення (CPU-інтенсивна частина)
    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

    /**
     * Паралельно завантажує список зображень, використовуючи OkHttp (блокуючі виклики,
     * обгорнуті в корутини на Dispatchers.IO).
     */
    override fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            val startTime = System.nanoTime()
            val deferredBitmaps = mutableListOf<Deferred<Bitmap?>>()

            imageUrls.forEachIndexed { index, url ->
                deferredBitmaps.add(
                    async(Dispatchers.IO) { // КОЖНЕ завантаження запускається паралельно на Dispatchers.IO
                        try {
                            // !!! Тут використовуємо БЛОКУЮЧУ функцію OkHttp, але виконуємо її на фоновому потоці Dispatchers.IO !!!
                            val bitmap = Utils.downloadImageBlocking(url)
                            withContext(Dispatchers.Main) {
                                onProgress(index, bitmap) // Повідомляємо про прогрес
                            }
                            bitmap
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                onProgress(
                                    index,
                                    null
                                ) // Повідомляємо, що це зображення не завантажилося
                                onError(e) // Повідомляємо про помилку
                            }
                            null
                        }
                    }
                )
            }

            // `awaitAll()` чекає на завершення всіх паралельних завантажень
            deferredBitmaps.awaitAll()
            val endTime = System.nanoTime()
            withContext(Dispatchers.Main) {
                onComplete((endTime - startTime) / 1_000_000) // Повідомляємо про загальний час
            }
        }
    }

    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            try {
                // Оголошуємо змінну 'finalBitmap' та 'time' тут,
                // щоб вони були доступні для withContext(Dispatchers.Main)
                val finalBitmap: Bitmap
                val timeMs: Long // Змінимо назву на timeMs, щоб було зрозуміліше, що це мілісекунди

                // Весь блок, який ми хочемо виміряти, обгортаємо measureNanoTime
                // і результат зберігаємо у змінній timeMs
                timeMs = measureNanoTime {
                    // Розділяємо зображення на частини
                    val parts = Utils.splitBitmap(originalBitmap, NUM_PROCESSING_PARTS)
                    val originalWidth = originalBitmap.width
                    val originalHeight = originalBitmap.height

                    // Паралельна обробка кожної частини
                    val processedPartsWithCoords = mutableListOf<Deferred<Pair<Bitmap, Int>>>()
                    parts.forEach { (partBitmap, originalX) ->
                        processedPartsWithCoords.add(
                            async(Dispatchers.Default) { // Кожна частина обробляється на Dispatchers.Default (для CPU)
                                val processedPart = Utils.processBitmapPartCoroutines(partBitmap)
                                Pair(processedPart, originalX)
                            }
                        )
                    }

                    // Чекаємо завершення всіх паралельних обробок
                    val results = processedPartsWithCoords.awaitAll()

                    // Збираємо оброблені частини назад
                    finalBitmap = Utils.combineBitmaps(originalWidth, originalHeight, results)
                } // measureNanoTime завершується тут, і його результат зберігається в timeMs

                // Тепер finalBitmap та timeMs доступні для використання
                withContext(Dispatchers.Main) {
                    onSuccess(finalBitmap, timeMs / 1_000_000) // Використовуємо timeMs
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    override fun cancelOperations() {
        job.cancelChildren()
    }
}