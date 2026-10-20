package id.abati.perjadin.backup

import android.content.Context
import android.net.Uri
import id.abati.perjadin.BuildConfig
import id.abati.perjadin.data.Repository
import java.io.File

/** Penghubung [BackupManager] dengan Android (SAF Uri, folder aplikasi, repository). */
object BackupService {
    private const val MAX_IMPORT_BYTES = BackupManager.MAX_TOTAL_BYTES
    private const val KEEP_AUTO_BACKUPS = 5

    fun manager(context: Context) = BackupManager(
        receiptsDir = File(context.filesDir, "receipts"),
        brandingDir = File(context.filesDir, "branding"),
        workDir = context.cacheDir,
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** Ekspor backup lengkap ke lokasi pilihan pengguna (Storage Access Framework). Jalankan di background. */
    fun exportTo(context: Context, repo: Repository, uri: Uri) {
        repo.flush()
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Tidak bisa menulis ke lokasi tersebut")
        out.use { manager(context).export(repo.state.value, it) }
        repo.markBackedUp()
    }

    /** Menyalin file pilihan pengguna ke cache agar bisa diperiksa dengan aman. Jalankan di background. */
    fun copyToCache(context: Context, uri: Uri): File {
        val target = File(context.cacheDir, "restore-incoming")
        val input = context.contentResolver.openInputStream(uri) ?: error("File tidak bisa dibuka")
        input.use { src ->
            target.outputStream().use { dst ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) throw BackupManager.BackupException("File backup terlalu besar.")
                    dst.write(buf, 0, n)
                }
            }
        }
        return target
    }

    /** Backup otomatis lokal (mis. sebelum restore). Hanya [KEEP_AUTO_BACKUPS] terbaru yang disimpan. */
    fun autoBackup(context: Context, repo: Repository, label: String): File {
        repo.flush()
        val file = File(repo.autoBackupDir, "$label-${System.currentTimeMillis()}.zip")
        file.outputStream().use { manager(context).export(repo.state.value, it) }
        repo.autoBackupDir.listFiles { f -> f.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_AUTO_BACKUPS)
            ?.forEach { it.delete() }
        return file
    }

    fun latestPreRestore(repo: Repository): File? =
        repo.autoBackupDir.listFiles { f -> f.name.startsWith("pre-restore-") && f.name.endsWith(".zip") }
            ?.maxByOrNull { it.lastModified() }

    /**
     * Menerapkan backup: membuat backup otomatis data saat ini lebih dulu, lalu mengganti data & aset.
     * Jika gagal di tengah jalan, data dan aset lama tetap utuh. Jalankan di background.
     */
    fun restore(context: Context, repo: Repository, preview: BackupManager.Preview, makeSafetyBackup: Boolean = true) {
        if (makeSafetyBackup) autoBackup(context, repo, "pre-restore")
        manager(context).apply(preview) { data ->
            repo.replaceAllBlocking(
                data.copy(
                    onboardingDone = true,
                    // Waktu backup terakhir = waktu file backup ini dibuat
                    lastBackupAt = maxOf(data.lastBackupAt, preview.exportedAt),
                ),
            )
        }
    }
}
