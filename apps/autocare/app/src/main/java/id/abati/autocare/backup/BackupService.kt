package id.abati.autocare.backup

import android.content.Context
import android.net.Uri
import id.abati.autocare.BuildConfig
import id.abati.autocare.app
import id.abati.autocare.data.Repository
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.util.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Jembatan BackupArchive ↔ Storage Access Framework & database. */
class BackupService(private val context: Context) {
    private val archive = BackupArchive(
        AttachmentStore.dir(context),
        File(context.cacheDir, "backup-work").apply { mkdirs() },
        BuildConfig.VERSION_NAME,
    )

    /** Menulis backup lengkap ke [uri] (dipilih pengguna) dan mencatat waktu backup. */
    suspend fun export(uri: Uri): BackupArchive.ExportResult = withContext(Dispatchers.IO) {
        val data = context.app.repository.snapshot()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Tidak bisa menulis ke lokasi tersebut")
        val result = out.use { archive.export(data, it) }
        context.app.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        result
    }

    /** Menyalin file pilihan pengguna ke cache lalu memeriksanya tanpa mengubah data aktif. */
    suspend fun inspect(uri: Uri): BackupArchive.Preview = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "backup-work/incoming.zip")
        tmp.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupArchive.BackupException("File tidak bisa dibuka.")
        input.use { i -> tmp.outputStream().use { i.copyTo(it) } }
        try {
            archive.inspect(tmp)
        } finally {
            tmp.delete()
        }
    }

    /** Menerapkan backup yang sudah diperiksa: database dulu (transaksi), baru file lampiran. */
    suspend fun apply(preview: BackupArchive.Preview, mode: Repository.RestoreMode) = withContext(Dispatchers.IO) {
        val repo = context.app.repository
        repo.restore(preview.data, mode)
        archive.installAttachments(preview, mode == Repository.RestoreMode.REPLACE, repo.referencedFiles())
        Reminders.checkNow(context)
    }
}
