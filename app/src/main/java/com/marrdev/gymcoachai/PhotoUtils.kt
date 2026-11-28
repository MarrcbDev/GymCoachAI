package com.marrdev.gymcoachai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Foto de perfil liviana: recorte cuadrado de 320 px en WebP con calidad 80 (≈10–25 KB, antes ≈150 KB).
 * Al ser tan pequeña también se puede sincronizar dentro del documento del perfil en Firestore,
 * sin necesidad de Firebase Storage.
 */
object PhotoUtils {
    private const val SIZE = 320
    private const val QUALITY = 80
    /** Fotos guardadas por versiones anteriores (768 px, JPEG 90) que conviene comprimir otra vez. */
    private const val HEAVY_BYTES = 80_000L

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    /** Copia la foto elegida a almacenamiento privado ya reducida. Devuelve la ruta o null si no se pudo leer. */
    suspend fun saveProfilePhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val square = decodeSquare(context, uri)
            val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.webp")
            file.outputStream().use { square.compress(webp(), QUALITY, it) }
            file.absolutePath
        }.getOrNull()
    }

    /** Si la foto guardada es pesada (versiones anteriores), la comprime y devuelve la ruta nueva; si no, null. */
    suspend fun recompressIfHeavy(context: Context, path: String): String? = withContext(Dispatchers.IO) {
        val old = File(path)
        if (path.isBlank() || !old.exists() || old.length() <= HEAVY_BYTES || old.parentFile != context.filesDir) return@withContext null
        saveProfilePhoto(context, Uri.fromFile(old))
    }

    /** Foto lista para subir: (base64, hash). null si no hay foto o no se puede leer. */
    suspend fun encodeForCloud(path: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(path)
            if (path.isBlank() || !f.exists()) return@runCatching null
            val bytes = f.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP) to sha1(bytes)
        }.getOrNull()
    }

    /** Guarda en almacenamiento privado una foto recibida de la nube (base64). */
    suspend fun saveFromCloud(context: Context, base64: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val file = File(context.filesDir, "profile_${System.currentTimeMillis()}.webp")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    fun hashOf(path: String): String? = runCatching {
        val f = File(path)
        if (path.isBlank() || !f.exists()) null else sha1(f.readBytes())
    }.getOrNull()

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Decodifica la imagen ya reducida (sin cargarla entera en memoria) y la recorta al centro en un cuadrado. */
    private fun decodeSquare(context: Context, uri: Uri): Bitmap {
        val source: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val shortest = minOf(info.size.width, info.size.height)
                if (shortest > SIZE) {
                    val f = SIZE.toFloat() / shortest
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
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Imagen ilegible")
            rotateByExif(context, uri, decoded)
        }
        val side = minOf(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        return if (side > SIZE) Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true) else cropped
    }

    private fun rotateByExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
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
