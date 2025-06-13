package com.android.social.media.social.mediaa.ndroid.test.domain.repository

import android.graphics.Bitmap

interface ImageDownloader {
    fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit, // Індекс та завантажений Bitmap (або null, якщо помилка)
        onComplete: (Long) -> Unit, // Загальний час завантаження в мс
        onError: (Throwable) -> Unit // Помилка, якщо виникла
    )

    fun processSingleImageParallel(
        originalBitmap: Bitmap,
        onSuccess: (Bitmap, Long) -> Unit, // Оброблений Bitmap та час обробки в мс
        onError: (Throwable) -> Unit
    )

    /**
     * Скасовує всі активні операції, запущені цим завантажувачем/обробником.
     * Це важливо для управління життєвим циклом і уникнення витоків пам'яті.
     */
    fun cancelOperations()

}