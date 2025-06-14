package com.android.social.media.social.mediaa.ndroid.test.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

object Utils {

    private lateinit var imageLoader: ImageLoader
    private lateinit var okHttpClient: OkHttpClient

    fun initializeOkHttpClient(context: Context) {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    fun initializeImageLoader(context: Context) {
        imageLoader = ImageLoader.Builder(context)
            .crossfade(true)
            .okHttpClient(okHttpClient)
            .build()
    }

    fun getImageUrl(imageId: Int, width: Int = 300, height: Int = 300): String {
        return "https://picsum.photos/id/$imageId/$width/$height"
    }

    suspend fun downloadImageCoroutines(context: Context, imageUrl: String): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(imageUrl) // Вказуємо URL для завантаження
            .allowHardware(false) // Може бути корисно для тестування продуктивності CPU, іноді вимикають апаратне прискорення
            .build()
        val result = imageLoader.execute(request) // Виконання запиту на завантаження

        if (result is SuccessResult && result.drawable is BitmapDrawable) {
            return (result.drawable as BitmapDrawable).bitmap
        } else {
            throw IllegalStateException("Could not load bitmap from URL: $imageUrl or result was not a BitmapDrawable")
        }
    }

    fun downloadImageBlocking(imageUrl: String): Bitmap {
        val request = Request.Builder().url(imageUrl).build()
        val response = okHttpClient.newCall(request).execute()

        if (response.isSuccessful) {
            val bytes = response.body?.bytes() ?: throw IOException("Empty response body")
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            throw IOException("Failed to download image: ${response.code} - ${response.message}")
        }
    }

    fun splitBitmap(bitmap: Bitmap, parts: Int): List<Pair<Bitmap, Int>> {
        if (parts <= 0) throw IllegalArgumentException("Кількість частин має бути більшою за 0")
        if (bitmap.isRecycled) {
            Log.e("Utils", "Спроба розділити перероблену бітмапу.")
            return emptyList()
        }

        val width = bitmap.width
        val height = bitmap.height
        val partWidth = width / parts
        val bitmapParts = mutableListOf<Pair<Bitmap, Int>>()

        for (i in 0 until parts) {
            val startX = i * partWidth
            val currentPartWidth = if (i == parts - 1) {
                width - startX
            } else {
                partWidth
            }

            if (currentPartWidth <= 0) {
                Log.w("Utils", "Частина зображення має нульову або від'ємну ширину на індексі $i")
                continue
            }

            try {
                val partBitmap = Bitmap.createBitmap(bitmap, startX, 0, currentPartWidth, height)
                bitmapParts.add(Pair(partBitmap, startX))
            } catch (e: Exception) {
                Log.e(
                    "Utils",
                    "Помилка при створенні частини бітмапу на індексі $i: ${e.message}",
                    e
                )
            }
        }
        Log.d("Utils", "Бітмап розділено на ${bitmapParts.size} частин.")
        return bitmapParts
    }


    fun processBitmapPartBlocking(partBitmap: Bitmap): Bitmap {
        if (partBitmap.isRecycled) {
            throw IllegalStateException("Cannot process recycled bitmap part")
        }

        val width = partBitmap.width
        val height = partBitmap.height

        val processedBitmap = Bitmap.createBitmap(width, height, requireNotNull(partBitmap.config))

        val pixels = IntArray(width * height)
        partBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            val tr = (0.393 * r + 0.769 * g + 0.189 * b).toInt().coerceIn(0, 255)
            val tg = (0.349 * r + 0.686 * g + 0.168 * b).toInt().coerceIn(0, 255)
            val tb = (0.272 * r + 0.534 * g + 0.131 * b).toInt().coerceIn(0, 255)

            pixels[i] = Color.rgb(tr, tg, tb)
        }

        processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        Log.d("Utils", "Оброблено частину ${width}x${height}")
        return processedBitmap
    }


    fun combineBitmaps(width: Int, height: Int, processedParts: List<Pair<Bitmap, Int>>): Bitmap {
        if (processedParts.isEmpty()) {
            Log.w("Utils", "Список оброблених частин порожній. Неможливо об'єднати.")
            return Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint()

        val sortedParts = processedParts.sortedBy { it.second }

        for ((partBitmap, startX) in sortedParts) {
            if (partBitmap.isRecycled) {
                Log.w("Utils", "Пропускаю перероблену частину (X=$startX)")
                continue
            }

            canvas.drawBitmap(partBitmap, startX.toFloat(), 0f, paint)
        }

        Log.d("Utils", "Об'єднано ${sortedParts.size} частин у ${width}x${height}")
        return resultBitmap
    }

    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
}