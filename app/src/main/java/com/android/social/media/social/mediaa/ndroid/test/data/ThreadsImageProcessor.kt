package com.android.social.media.social.mediaa.ndroid.test.data

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageDownloader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.measureNanoTime

class ThreadsImageProcessor : ImageDownloader {

    // Пул потоків для паралельного виконання завдань.
    // Використовуємо NewFixedThreadPool з кількістю потоків, рівною кількості доступних ядер CPU.
    // Це хороший компроміс для CPU-інтенсивних завдань. Для I/O-інтенсивних можна використовувати більший пул
    // або CachedThreadPool, але для нашого прикладу з 100 завантаженнями FixedThreadPool теж покаже паралелізм.
    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    // Handler для повернення результатів на головний (UI) потік Android
    private val uiHandler = Handler(Looper.getMainLooper())

    // Список для відстеження всіх активних Futures, щоб їх можна було скасувати
    private val activeFutures = mutableListOf<Future<*>>()

    // Кількість частин, на які буде ділитися зображення для обробки
    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

    /**
     * Паралельно завантажує список зображень, використовуючи окремі потоки з ExecutorService.
     * Кожне зображення завантажується блокуючим способом на своєму фоновому потоці.
     */
    override fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val startTime = System.nanoTime()
        // CountDownLatch для очікування завершення всіх 100 завантажень
        val latch = CountDownLatch(imageUrls.size)

        // `synchronized` блок для безпечного доступу до `downloadedBitmaps`
        // Це не є строго необхідним для `onProgress` у даній логіці, але корисно для розуміння.
        // Для `onProgress` ми просто викликаємо колбек, а не модифікуємо спільний список.

        imageUrls.forEachIndexed { index, url ->
            // Відправляємо кожне завантаження як окреме завдання до ExecutorService
            val future = executorService.submit {
                try {
                    // !!! Блокуюче завантаження одного зображення OkHttp !!!
                    val bitmap = Utils.downloadImageBlocking(url)
                    // Повертаємося на UI-потік, щоб повідомити про прогрес
                    uiHandler.post {
                        onProgress(index, bitmap) // Повідомляємо, що це зображення завантажено
                    }
                } catch (e: Exception) {
                    uiHandler.post {
                        onProgress(index, null) // Повідомляємо, що це зображення не завантажилося
                        onError(e) // Повідомляємо про помилку
                    }
                } finally {
                    latch.countDown() // Зменшуємо лічильник після завершення завдання (незалежно від успіху/помилки)
                }
            }
            activeFutures.add(future) // Додаємо Future до списку для можливого скасування
        }

        // Відправляємо окреме завдання до ExecutorService, яке чекатиме завершення всіх завантажень
        val completionFuture = executorService.submit {
            try {
                latch.await() // Чекаємо, доки лічильник не дійде до нуля (всі завантаження завершаться)
                val endTime = System.nanoTime()
                uiHandler.post {
                    onComplete((endTime - startTime) / 1_000_000) // Повідомляємо загальний час на UI-потоці
                }
            } catch (e: InterruptedException) {
                // Обробка переривання (наприклад, якщо cancelOperations викликано)
                uiHandler.post {
                    onError(e)
                }
                Thread.currentThread().interrupt() // Відновлюємо статус переривання
            }
        }
        activeFutures.add(completionFuture)
    }

    /**
     * Паралельно обробляє одне зображення, розділяючи його на частини,
     * використовуючи окремі потоки з ExecutorService.
     */
    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        // Запускаємо весь процес обробки одного зображення на фоновому потоці
        val future = executorService.submit {
            try {
                val finalBitmap: Bitmap // Змінна для збереження результату обробки
                val timeMs: Long // Змінна для збереження часу обробки в мс

                timeMs = measureNanoTime { // Вимірюємо час обробки
                    val originalWidth = originalBitmap.width
                    val originalHeight = originalBitmap.height
                    val parts = Utils.splitBitmap(originalBitmap, NUM_PROCESSING_PARTS)

                    val processedPartsWithCoords = mutableListOf<Pair<Bitmap, Int>>()
                    // CountDownLatch для очікування завершення обробки всіх частин
                    val latch = CountDownLatch(parts.size)

                    parts.forEach { (partBitmap, originalX) ->
                        // Кожну частину відправляємо на обробку окремим завданням
                        val partFuture = executorService.submit {
                            try {
                                // !!! Блокуюча функція обробки частини !!!
                                val processedPart = Utils.processBitmapPartBlocking(partBitmap)
                                synchronized(processedPartsWithCoords) { // Синхронізація для безпечного доступу до списку
                                    processedPartsWithCoords.add(Pair(processedPart, originalX))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace() // Виводимо помилку в консоль
                            } finally {
                                latch.countDown() // Зменшуємо лічильник
                            }
                        }
                        activeFutures.add(partFuture) // Додаємо Future до списку
                    }

                    latch.await() // Чекаємо, доки всі частини не будуть оброблені

                    // Комбінуємо оброблені частини назад в єдиний Bitmap
                    finalBitmap = Utils.combineBitmaps(
                        originalWidth,
                        originalHeight,
                        processedPartsWithCoords
                    )
                }

                // Повертаємося на UI-потік, щоб повідомити про успіх
                uiHandler.post {
                    onSuccess(finalBitmap, timeMs / 1_000_000)
                }
            } catch (e: Exception) {
                uiHandler.post {
                    onError(e) // Повідомляємо про помилку на UI-потоці
                }
            }
        }
        activeFutures.add(future) // Додаємо головний Future обробки до списку
    }

    /**
     * Скасовує всі активні завдання, відправлені до ExecutorService.
     * Спроба скасувати завдання (mayInterruptIfRunning = true).
     */
    override fun cancelOperations() {
        for (future in activeFutures) {
            future.cancel(true) // Спроба перервати виконання потоків
        }
        activeFutures.clear() // Очищаємо список
    }
}