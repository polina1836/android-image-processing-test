package com.android.social.media.social.mediaa.ndroid.test.domain.repository

import android.graphics.Bitmap

interface ImageDownloader {
    fun downloadImages(
        imageUrls: List<String>,
        onProgress: (Int, Bitmap?) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Throwable) -> Unit
    )

    fun processSingleImageParallel(
        originalBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        onSuccess: (Bitmap, Long) -> Unit,
        onError: (Throwable) -> Unit
    )
    fun cancelOperations()
}