package com.android.social.media.social.mediaa.ndroid.test.data

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageProcessor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.measureNanoTime

class ThreadsImageProcessor : ImageProcessor {
    private val executorService: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    private val uiHandler = Handler(Looper.getMainLooper())

    private val activeFutures = mutableListOf<Future<*>>()

    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

    override fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val startTime = System.nanoTime()
        val latch = CountDownLatch(imageUrls.size)

        imageUrls.forEachIndexed { index, url ->
            val future = executorService.submit {
                try {
                    val bitmap = Utils.downloadImageBlocking(url)
                    uiHandler.post {
                        onProgress(index, bitmap)
                    }
                } catch (e: Exception) {
                    uiHandler.post {
                        onProgress(index, null)
                        onError(e)
                    }
                } finally {
                    latch.countDown()
                }
            }
            activeFutures.add(future)
        }

        val completionFuture = executorService.submit {
            try {
                latch.await()
                val endTime = System.nanoTime()
                uiHandler.post {
                    onComplete((endTime - startTime) / 1_000_000)
                }
            } catch (e: InterruptedException) {
                uiHandler.post {
                    onError(e)
                }
                Thread.currentThread().interrupt()
            }
        }
        activeFutures.add(completionFuture)
    }

    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val future = executorService.submit {
            try {
                val finalBitmap: Bitmap
                val timeMs: Long

                timeMs = measureNanoTime {
                    val resizedBitmap =
                        Utils.resizeBitmap(originalBitmap, targetWidth, targetHeight)
                    val resizedWidth = resizedBitmap.width
                    val resizedHeight = resizedBitmap.height
                    val parts = Utils.splitBitmap(resizedBitmap, NUM_PROCESSING_PARTS)

                    val processedPartsWithCoords = mutableListOf<Pair<Bitmap, Int>>()
                    val latch = CountDownLatch(parts.size)

                    parts.forEach { (partBitmap, originalX) ->
                        val partFuture = executorService.submit {
                            try {
                                val processedPart = Utils.processBitmapPartBlocking(partBitmap)
                                synchronized(processedPartsWithCoords) {
                                    processedPartsWithCoords.add(Pair(processedPart, originalX))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                latch.countDown()
                            }
                        }
                        activeFutures.add(partFuture)
                    }

                    latch.await()

                    finalBitmap = Utils.combineBitmaps(
                        resizedWidth,
                        resizedHeight,
                        processedPartsWithCoords
                    )
                }

                uiHandler.post {
                    onSuccess(finalBitmap, timeMs / 1_000_000)
                }
            } catch (e: Exception) {
                uiHandler.post {
                    onError(e)
                }
            }
        }
        activeFutures.add(future)
    }

    override fun cancelOperations() {
        for (future in activeFutures) {
            future.cancel(true)
        }
        activeFutures.clear()
    }
}