package com.android.social.media.social.mediaa.ndroid.test.data

import android.graphics.Bitmap
import android.util.Log
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageProcessor
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.schedulers.Schedulers

sealed class DownloadResult {
    data class Success(val bitmap: Bitmap) : DownloadResult()
    object Failed : DownloadResult()
}

class RxJavaImageProcessor : ImageProcessor {

    private val disposables = CompositeDisposable()

    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

    override fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val startTime = System.nanoTime()
        val disposable = Observable.fromIterable(imageUrls.withIndex())
            .flatMap({ (index, url) ->
                Observable.fromCallable {
                    try {
                        val bitmap = Utils.downloadImageBlocking(url)
                        DownloadResult.Success(bitmap)
                    } catch (e: Exception) {
                        DownloadResult.Failed
                    }
                }.subscribeOn(Schedulers.io())
                    .doOnNext { result ->
                        when (result) {
                            is DownloadResult.Success -> onProgress(index, result.bitmap)
                            is DownloadResult.Failed -> onProgress(index, null)
                        }
                    }.onErrorReturn { error ->
                        DownloadResult.Failed
                    }
            }, true, imageUrls.size)
            .toList()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ results ->
                val endTime = System.nanoTime()
                val successfulDownloads = results.count { it is DownloadResult.Success }
                onComplete((endTime - startTime) / 1_000_000)
            }, { error ->
                if (error is CompositeException) {
                    error.exceptions.forEachIndexed { i, e ->
                        Log.e("RxJavaImageProcessor", "  Inner Exception ${i + 1}: ${e.message}", e)
                    }
                }
                onError(error)
            })

        disposables.add(disposable)
    }

    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val startTime = System.nanoTime()

        val disposable = Single.fromCallable {
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height
            val parts = Utils.splitBitmap(originalBitmap, NUM_PROCESSING_PARTS)

            Log.d("RxJavaImageProcessor", "Starting processing of ${parts.size} parts")

            Triple(originalWidth, originalHeight, parts)
        }
            .subscribeOn(Schedulers.io())
            .flatMap { (originalWidth, originalHeight, parts) ->

                val processedParts = parts.withIndex().map { (partIndex, partData) ->
                    val (partBitmap, originalX) = partData
                    Single.fromCallable {
                        Log.d("RxJavaImageProcessor", "Processing part $partIndex")
                        try {
                            val processedPart = Utils.processBitmapPartBlocking(partBitmap)
                            Pair(processedPart, originalX)
                        } catch (e: Exception) {
                            Log.e(
                                "RxJavaImageProcessor",
                                "Error processing part $partIndex: ${e.message}",
                                e
                            )
                            throw e
                        }
                    }.subscribeOn(Schedulers.computation())
                }

                Single.zip(processedParts) { resultsArray ->
                    val results = resultsArray.map { it as Pair<Bitmap, Int> }
                    val finalBitmap = Utils.combineBitmaps(originalWidth, originalHeight, results)
                    val timeNanos = System.nanoTime() - startTime

                    Log.d("RxJavaImageProcessor", "Successfully processed image")
                    Pair(timeNanos, finalBitmap)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (timeNanos, finalBitmap) ->
                onSuccess(finalBitmap, timeNanos / 1_000_000)
            }, { error ->
                Log.e(
                    "RxJavaImageProcessor",
                    "Error in processSingleImageParallel: ${error.message}",
                    error
                )
                onError(error)
            })
        disposables.add(disposable)
    }

    override fun cancelOperations() {
        disposables.clear()
    }
}