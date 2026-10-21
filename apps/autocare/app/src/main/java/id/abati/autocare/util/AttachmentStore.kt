package id.abati.autocare.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import id.abati.autocare.data.Repository
import id.abati.autocare.data.newId
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Penyimpanan lampiran (foto nota, invoice, dokumen, foto kendaraan) di internal storage
 * `filesDir/attachments`. Database hanya menyimpan nama file, sehingga backup bisa
 * menyertakan file aslinya dan memulihkannya di HP lain.
 */
object AttachmentStore {
    private const val MAX_PX = 1800
    const val MAX_FILE_BYTES = 25L * 1024 * 1024

    data class Imported(val fileName: String, val displayName: String, val mimeType: String, val size: Long)

    fun dir(context: Context): File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun exists(context: Context, name: String): Boolean = name.isNotBlank() && file(context, name).isFile

    fun cleaner(context: Context) = Repository.FileCleaner { names ->
        names.filter { it.isNotBlank() }.forEach { file(context, it).delete() }
    }

    /** File sementara untuk hasil kamera (cache/camera, dibagikan lewat FileProvider). */
    fun newCameraFile(context: Context): File =
        File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture_${System.currentTimeMillis()}.jpg")

    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Menyalin file dari [uri]. Gambar dikompres ke JPEG (maks 1800px, orientasi EXIF dibetulkan);
     * dokumen lain (PDF, dll) disalin apa adanya. Jalankan di background thread.
     */
    fun import(context: Context, uri: Uri): Imported {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var display = "lampiran"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { display = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (mime.startsWith("image/") && mime != "image/gif") {
            val name = importImage(context, uri)
            return Imported(name, display, "image/jpeg", file(context, name).length())
        }
        require(size <= MAX_FILE_BYTES) { "File terlalu besar (maks 25 MB)" }
        val ext = display.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5)
        val name = newId() + if (ext.isNotEmpty()) ".$ext" else ""
        val target = file(context, name)
        var copied = 0L
        cr.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    copied += n
                    if (copied > MAX_FILE_BYTES) {
                        out.close()
                        target.delete()
                        throw IllegalArgumentException("File terlalu besar (maks 25 MB)")
                    }
                    out.write(buf, 0, n)
                }
            }
        } ?: error("File tidak bisa dibaca")
        return Imported(name, display, mime, copied)
    }

    private fun importImage(context: Context, uri: Uri): String {
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
        FileOutputStream(file(context, name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return name
    }

    /** Memuat gambar dengan sisi terpanjang kira-kira [maxPx] (hemat memori). Null jika file hilang/rusak. */
    fun loadBitmap(context: Context, name: String, maxPx: Int): Bitmap? {
        if (!exists(context, name)) return null
        val f = file(context, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Membuka lampiran dengan aplikasi lain. Menampilkan pesan jelas bila file hilang. */
    fun open(context: Context, name: String, mime: String) {
        if (!exists(context, name)) {
            Toast.makeText(context, "File lampiran tidak ditemukan di perangkat. Pulihkan dari backup lengkap.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(context, file(context, name)), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_LONG).show()
        }
    }
}
