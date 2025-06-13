package com.android.social.media.social.mediaa.ndroid.test.data

import android.graphics.Bitmap
import com.android.social.media.social.mediaa.ndroid.test.domain.repository.ImageDownloader
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlin.system.measureNanoTime

class RxJavaImageProcessor : ImageDownloader {

    // CompositeDisposable для управління всіма підписками та їх скасування
    private val disposables = CompositeDisposable()

    // Кількість частин, на які буде ділитися зображення для обробки
    private val NUM_PROCESSING_PARTS = Runtime.getRuntime().availableProcessors()

    /**
     * Паралельно завантажує список зображень, використовуючи OkHttp та RxJava Schedulers.io().
     * Кожне завантаження виконується на окремому потоці з пулу IO.
     */
    override fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val startTime = System.nanoTime()

        val disposable = Observable.fromIterable(imageUrls)
            .flatMap({ url ->
                Observable.fromCallable {
                    Utils.downloadImageBlocking(url)
                }.subscribeOn(Schedulers.io())
            }, true, imageUrls.size)
            .toList()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ bitmaps ->
                val endTime = System.nanoTime()
                bitmaps.forEachIndexed { index, bitmap ->
                    onProgress(index, bitmap)
                }
                onComplete((endTime - startTime) / 1_000_000)
            }, { error ->
                onError(error)
            })
        // ВИПРАВЛЕННЯ: Додаємо Disposable вручну
        disposables.add(disposable)
    }

    /**
     * Паралельно обробляє (застосовує фільтр) одне зображення, розділяючи його на частини,
     * використовуючи RxJava Schedulers.computation().
     */
    override fun processSingleImageParallel(
        originalBitmap: Bitmap,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val disposable = Observable.fromCallable {
            val originalWidth = originalBitmap.width
            val originalHeight = originalBitmap.height
            val parts = Utils.splitBitmap(originalBitmap, NUM_PROCESSING_PARTS)

            // ВИПРАВЛЕННЯ: Явно зберігаємо результат time у змінній та finalBitmap
            var finalBitmap: Bitmap? = null
            val timeNanos = measureNanoTime {
                val processedPartsObservable = Observable.fromIterable(parts)
                    .flatMap({ (partBitmap, originalX) ->
                        Observable.fromCallable {
                            val processedPart = Utils.processBitmapPartBlocking(partBitmap)
                            Pair(processedPart, originalX)
                        }.subscribeOn(Schedulers.computation())
                    }, true, NUM_PROCESSING_PARTS)

                val processedPartsList = processedPartsObservable.toList().blockingGet()

                finalBitmap =
                    Utils.combineBitmaps(originalWidth, originalHeight, processedPartsList)
            }
            // ВИПРАВЛЕННЯ: Повертаємо Pair явно з timeNanos та finalBitmap
            Pair(timeNanos, finalBitmap)
        }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            // ВИПРАВЛЕННЯ: Явно вказуємо імена параметрів в лямбді subscribe
            .subscribe({ (timeNanos, finalBitmap) ->
                onSuccess(finalBitmap, timeNanos / 1_000_000)
            }, { error ->
                onError(error)
            })
        // ВИПРАВЛЕННЯ: Додаємо Disposable вручну
        disposables.add(disposable)
    }

    override fun cancelOperations() {
        disposables.clear()
    }
}