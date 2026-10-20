package id.abati.perjadin.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Helper berbagi file PDF ke WhatsApp, Email, atau aplikasi lain. */
object ShareHelper {

    private const val MIME_PDF = "application/pdf"

    fun uriOf(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun sendIntent(context: Context, file: File, subject: String, text: String): Intent {
        val uri = uriOf(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData diperlukan agar izin baca URI ikut diteruskan lewat chooser
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Menampilkan pilihan semua aplikasi (WhatsApp, Telegram, Drive, Email, dll). */
    fun shareAny(context: Context, file: File, subject: String, text: String) {
        context.startActivity(Intent.createChooser(sendIntent(context, file, subject, text), "Bagikan laporan PDF"))
    }

    /** Langsung membuka WhatsApp / WhatsApp Business. Fallback ke chooser jika tidak terpasang. */
    fun shareWhatsApp(context: Context, file: File, subject: String, text: String) {
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(sendIntent(context, file, subject, text).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // coba paket berikutnya
            }
        }
        Toast.makeText(context, "WhatsApp tidak ditemukan, pilih aplikasi lain", Toast.LENGTH_SHORT).show()
        shareAny(context, file, subject, text)
    }

    /** Hanya menampilkan aplikasi email (Gmail, Outlook, dll) dengan PDF sebagai lampiran. */
    fun shareEmail(context: Context, file: File, subject: String, text: String) {
        val intent = sendIntent(context, file, subject, text).apply {
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Kirim via Email"))
        } catch (_: ActivityNotFoundException) {
            shareAny(context, file, subject, text)
        }
    }

    fun viewImage(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(context, file), "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada aplikasi galeri untuk membuka foto", Toast.LENGTH_LONG).show()
        }
    }

    fun view(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriOf(context, file), MIME_PDF)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada aplikasi pembaca PDF di perangkat", Toast.LENGTH_LONG).show()
        }
    }
}
