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

    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

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
                    async(Dispatchers.IO) {
                        try {
                            val bitmap = Utils.downloadImageBlocking(url)
                            withContext(Dispatchers.Main) {
                                onProgress(index, bitmap)
                            }
                            bitmap
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                onProgress(
                                    index,
                                    null
                                )
                                onError(e)
                            }
                            null
                        }
                    }
                )
            }

            deferredBitmaps.awaitAll()
            val endTime = System.nanoTime()
            withContext(Dispatchers.Main) {
                onComplete((endTime - startTime) / 1_000_000) // Повідомляємо про загальний час
            }
        }
    }

    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            try {
                val finalBitmap: Bitmap
                val timeMs: Long
                timeMs = measureNanoTime {
                    val resizedBitmap = Utils.resizeBitmap(originalBitmap, targetWidth, targetHeight)
                    val parts = Utils.splitBitmap(originalBitmap, NUM_PROCESSING_PARTS)
                    val originalWidth = originalBitmap.width
                    val originalHeight = originalBitmap.height

                    val processedPartsWithCoords = mutableListOf<Deferred<Pair<Bitmap, Int>>>()
                    parts.forEach { (partBitmap, originalX) ->
                        processedPartsWithCoords.add(
                            async(Dispatchers.Default) { // Кожна частина обробляється на Dispatchers.Default (для CPU)
                                val processedPart = Utils.processBitmapPartBlocking(partBitmap)
                                Pair(processedPart, originalX)
                            }
                        )
                    }
                    val results = processedPartsWithCoords.awaitAll()
                    finalBitmap = Utils.combineBitmaps(originalWidth, originalHeight, results)
                }
                withContext(Dispatchers.Main) {
                    onSuccess(finalBitmap, timeMs / 1_000_000)
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