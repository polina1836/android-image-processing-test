package com.android.social.media.social.mediaa.ndroid.test.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
            .crossfade(true) // Додає плавний перехід між зображеннями
            .okHttpClient(okHttpClient) // Використовуємо той самий OkHttpClient для консистентності
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

        // Перевіряємо, чи результат успішний і чи є він BitmapDrawable
        if (result is SuccessResult && result.drawable is BitmapDrawable) {
            return (result.drawable as BitmapDrawable).bitmap
        } else {
            throw IllegalStateException("Could not load bitmap from URL: $imageUrl or result was not a BitmapDrawable")
        }
    }

    fun downloadImageBlocking(imageUrl: String): Bitmap {
        val request = Request.Builder().url(imageUrl).build()
        val response = okHttpClient.newCall(request).execute() // Блокуючий мережевий виклик

        if (response.isSuccessful) {
            val bytes = response.body?.bytes() ?: throw IOException("Empty response body")
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) // Декодуємо байти в Bitmap
        } else {
            throw IOException("Failed to download image: ${response.code} - ${response.message}")
        }
    }

    // --- Функції для ПАРАЛЕЛЬНОЇ ОБРОБКИ ОДНОГО ЗОБРАЖЕННЯ ---

    /**
     * Розділяє оригінальний Bitmap на вказану кількість вертикальних смуг.
     * @param originalBitmap Оригінальний Bitmap для розділення.
     * @param numParts Кількість частин, на які потрібно розділити зображення.
     * @return Список Pair<Bitmap, Int>, де перший елемент - це частина зображення,
     * а другий - її оригінальна X-координата у вихідному зображенні.
     */
    fun splitBitmap(originalBitmap: Bitmap, numParts: Int): List<Pair<Bitmap, Int>> {
        val width = originalBitmap.width
        val height = originalBitmap.height
        // Визначаємо приблизну ширину кожної частини
        val partWidth = width / numParts
        val parts = mutableListOf<Pair<Bitmap, Int>>()

        for (i in 0 until numParts) {
            val x = i * partWidth // Початкова X-координата для поточної частини
            // Обчислюємо фактичну ширину поточної частини. Остання частина може бути ширшою,
            // щоб захопити будь-який залишок пікселів.
            val currentPartWidth = if (i == numParts - 1) width - x else partWidth
            // Створюємо новий Bitmap для поточної частини
            val part = Bitmap.createBitmap(originalBitmap, x, 0, currentPartWidth, height)
            parts.add(Pair(part, x)) // Додаємо частину та її оригінальну X-координату
        }
        return parts
    }

    /**
     * Застосовує інверсійний фільтр до однієї частини зображення.
     * Ця функція симулює CPU-інтенсивну обробку і є блокуючою.
     * Призначена для використання з Java Threads або RxJava (в Schedulers.computation()).
     * @param partBitmap Частина Bitmap для обробки.
     * @return Оброблена частина Bitmap.
     */
    fun processBitmapPartBlocking(partBitmap: Bitmap): Bitmap {
        // Додаємо невелику затримку для симуляції інтенсивної CPU-роботи.
        // Це дозволить чіткіше побачити ефект від паралелізму.
        TimeUnit.MILLISECONDS.sleep(Random.Default.nextLong(50, 150)) // Випадкова затримка
        return applyInversionFilter(partBitmap) // Застосовуємо фільтр
    }

    /**
     * Застосовує інверсійний фільтр до однієї частини зображення асинхронно.
     * Ця функція симулює CPU-інтенсивну обробку і є 'suspend'.
     * Призначена для використання з Kotlin Coroutines.
     * @param partBitmap Частина Bitmap для обробки.
     * @return Оброблена частина Bitmap.
     */
    suspend fun processBitmapPartCoroutines(partBitmap: Bitmap): Bitmap {
        // Додаємо невелику затримку для симуляції інтенсивної CPU-роботи.
        delay(Random.Default.nextLong(50, 150)) // Випадкова затримка
        return applyInversionFilter(partBitmap) // Застосовуємо фільтр
    }

    /**
     * Збирає оброблені частини зображення назад в єдиний Bitmap.
     * @param originalWidth Оригінальна ширина повного зображення.
     * @param originalHeight Оригінальна висота повного зображення.
     * @param parts Список Pair<Bitmap, Int>, де перший елемент - оброблена частина,
     * а другий - її оригінальна X-координата.
     * @return Повністю зібраний та оброблений Bitmap.
     */
    fun combineBitmaps(originalWidth: Int, originalHeight: Int, parts: List<Pair<Bitmap, Int>>): Bitmap {
        // Створюємо новий порожній Bitmap для результату
        val resultBitmap = Bitmap.createBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap) // Створюємо Canvas для малювання на ньому

        // Важливо: сортуємо частини за їх оригінальними X-координатами, щоб зібрати їх у правильному порядку
        val sortedParts = parts.sortedBy { it.second }

        // Малюємо кожну оброблену частину на Canvas, використовуючи її оригінальні координати
        for ((part, x) in sortedParts) {
            canvas.drawBitmap(part, x.toFloat(), 0f, null)
        }
        return resultBitmap
    }

    // --- Допоміжна функція: Застосування фільтру інверсії ---

    /**
     * Застосовує інверсійний фільтр (негатив) до наданого Bitmap.
     * @param bitmap Вхідний Bitmap.
     * @return Новий Bitmap з застосованим інверсійним фільтром.
     */
    private fun applyInversionFilter(bitmap: Bitmap): Bitmap {
        // Створюємо копію Bitmap, щоб не змінювати оригінал, і встановлюємо ARGB_8888 для підтримки прозорості
        val processedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = processedBitmap.width
        val height = processedBitmap.height

        // Проходимо по кожному пікселю і інвертуємо його кольори
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = processedBitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                // Інверсія кольорів (255 - значення)
                val invertedRed = 255 - red
                val invertedGreen = 255 - green
                val invertedBlue = 255 - blue

                // Встановлюємо новий інвертований піксель
                processedBitmap.setPixel(x, y, Color.argb(alpha, invertedRed, invertedGreen, invertedBlue))
            }
        }
        return processedBitmap
    }
}