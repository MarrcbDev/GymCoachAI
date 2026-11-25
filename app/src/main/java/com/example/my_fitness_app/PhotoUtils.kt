package com.example.my_fitness_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PhotoUtils {
    private const val MAX_SIDE = 768

    /**
     * Copia la foto elegida a almacenamiento privado, reducida a 768 px y con la orientación
     * correcta. Devuelve la ruta del archivo nuevo o null si no se pudo leer la imagen.
     */
    suspend fun saveProfilePhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_SIDE) {
                        val f = MAX_SIDE.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * f).toInt().coerceAtLeast(1),
                            (info.size.height * f).toInt().coerceAtLeast(1),
                        )
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: error("Imagen ilegible")
            }
            val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    /** Borra una foto anterior propia de la app (nunca archivos ajenos). */
    fun deleteOld(context: Context, path: String) {
        if (path.isBlank()) return
        val f = File(path)
        if (f.exists() && f.parentFile == context.filesDir) f.delete()
    }

    suspend fun load(path: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        if (path.isBlank() || !File(path).exists()) null else runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
}
