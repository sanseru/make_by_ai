package id.abati.perjadin.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import id.abati.perjadin.data.newId
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Penyimpanan foto struk di internal storage (filesDir/receipts).
 * Foto dikompres ke JPEG maks 1600px agar hemat ruang & PDF tidak terlalu besar.
 */
object ReceiptStore {
    private const val MAX_PX = 1600

    fun dir(context: Context): File = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    /** File sementara untuk hasil kamera (cache/camera, dibagikan lewat FileProvider). */
    fun newCameraFile(context: Context): File =
        File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture_${System.currentTimeMillis()}.jpg")

    /** Menyalin + mengompres gambar dari [uri]; mengembalikan nama file. Jalankan di background thread. */
    fun importImage(context: Context, uri: Uri): String {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "Gambar tidak bisa dibaca" }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Gambar tidak bisa dibaca")

        // Foto kamera sering tersimpan miring; putar sesuai orientasi EXIF
        val degrees = try {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        val bitmap = if (degrees != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
        } else {
            decoded
        }

        val name = "${newId()}.jpg"
        FileOutputStream(file(context, name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        return name
    }

    /** Memuat foto dengan sisi terpanjang kira-kira [maxPx] (hemat memori). */
    fun load(context: Context, name: String, maxPx: Int): Bitmap? {
        val f = file(context, name)
        if (name.isBlank() || !f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
