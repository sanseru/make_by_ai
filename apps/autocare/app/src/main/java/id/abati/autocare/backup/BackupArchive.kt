package id.abati.autocare.backup

import id.abati.autocare.data.AppData
import id.abati.autocare.data.isSafeFileName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Backup lengkap AutoCare dalam satu arsip ZIP (.autocare.zip):
 *
 * ```
 * manifest.json              format, versi skema, waktu ekspor, jumlah data, SHA-256 tiap file
 * data.json                  seluruh data (kendaraan, servis, reminder, komponen, biaya, ...)
 * attachments/<nama file>    foto nota, invoice, dokumen, foto kendaraan
 * ```
 *
 * Restore selalu diperiksa dulu ([inspect]) TANPA menyentuh data aktif: versi format,
 * kelengkapan arsip, checksum, dan integritas relasi data. Kelas ini tidak memakai API
 * Android sehingga diuji dengan unit test JVM.
 */
class BackupArchive(
    /** Folder lampiran aplikasi (filesDir/attachments). */
    private val attachmentsDir: File,
    /** Folder kerja sementara (cacheDir) untuk ekstraksi sebelum diterapkan. */
    private val workDir: File,
    private val appVersion: String,
) {
    companion object {
        const val APP = "AutoCare"
        const val FORMAT_VERSION = 1
        const val MAX_ENTRY_BYTES = 40L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
        const val MAX_ENTRIES = 20_000
        private const val DATA = "data.json"
        private const val MANIFEST = "manifest.json"
        private const val ATTACHMENTS = "attachments/"

        val json = Json {
            ignoreUnknownKeys = true // kolom baru dari versi aplikasi lebih baru diabaikan dengan aman
            encodeDefaults = true
            prettyPrint = true
        }

        fun fileName(nowMillis: Long): String {
            val d = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault())
            return "AutoCare-backup-%04d%02d%02d-%02d%02d.zip".format(d.year, d.monthValue, d.dayOfMonth, d.hour, d.minute)
        }
    }

    class BackupException(message: String) : Exception(message)

    @Serializable
    data class FileEntry(val path: String, val sha256: String, val size: Long)

    @Serializable
    data class Manifest(
        val app: String,
        val formatVersion: Int,
        val appVersion: String = "",
        val exportedAt: Long = 0,
        val counts: Map<String, Int> = emptyMap(),
        val data: FileEntry,
        val attachments: List<FileEntry> = emptyList(),
    )

    /** Hasil pemeriksaan backup sebelum diterapkan. */
    class Preview(
        val data: AppData,
        val manifest: Manifest,
        val warnings: List<String>,
        /** Folder berisi lampiran yang sudah diekstrak & diverifikasi. */
        val stagedDir: File,
    ) {
        val vehicleCount get() = data.vehicles.size
        val visitCount get() = data.visits.size
        val attachmentCount get() = stagedDir.listFiles()?.size ?: 0
    }

    // ---------------------------------------------------------------- ekspor

    /** Menulis backup lengkap ke [out]. Lampiran yang dirujuk tapi filenya hilang dicatat di hasil. */
    fun export(data: AppData, out: OutputStream, now: Long = System.currentTimeMillis()): ExportResult {
        val referenced = (data.attachments.map { it.fileName } + data.vehicles.map { it.photo })
            .filter { it.isNotBlank() && isSafeFileName(it) }
            .distinct()
        val present = referenced.map { File(attachmentsDir, it) }.filter { it.isFile }
        val missing = referenced.size - present.size

        val dataBytes = json.encodeToString(AppData.serializer(), data).toByteArray(Charsets.UTF_8)
        val manifest = Manifest(
            app = APP,
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion,
            exportedAt = now,
            counts = counts(data),
            data = FileEntry(DATA, sha256(dataBytes), dataBytes.size.toLong()),
            attachments = present.map { FileEntry(ATTACHMENTS + it.name, sha256(it), it.length()) },
        )

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(Manifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(dataBytes)
            zip.closeEntry()
            present.forEach { f ->
                zip.putNextEntry(ZipEntry(ATTACHMENTS + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return ExportResult(present.size, missing)
    }

    data class ExportResult(val attachmentCount: Int, val missingAttachments: Int)

    // ---------------------------------------------------------------- inspeksi

    /**
     * Memeriksa arsip backup TANPA mengubah data aplikasi.
     * Melempar [BackupException] dengan pesan untuk pengguna jika tidak valid.
     */
    fun inspect(file: File): Preview {
        if (!file.isFile || file.length() == 0L) throw BackupException("File backup kosong atau tidak ditemukan.")
        val staging = File(workDir, "restore-staging").apply { deleteRecursively(); mkdirs() }
        val zip = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw BackupException("File bukan arsip backup AutoCare yang valid (.zip).")
        }
        zip.use { z ->
            val entries = z.entries().toList()
            if (entries.size > MAX_ENTRIES) throw BackupException("Arsip berisi terlalu banyak file.")

            val manifestEntry = z.getEntry(MANIFEST) ?: throw BackupException("manifest.json tidak ditemukan. Bukan backup AutoCare.")
            val manifest = try {
                json.decodeFromString(Manifest.serializer(), readEntry(z, manifestEntry).toString(Charsets.UTF_8))
            } catch (e: SerializationException) {
                throw BackupException("manifest.json rusak: ${e.message?.take(120)}")
            } catch (e: IllegalArgumentException) {
                throw BackupException("manifest.json rusak: ${e.message?.take(120)}")
            }
            if (manifest.app != APP) throw BackupException("Backup ini bukan dari aplikasi AutoCare.")
            if (manifest.formatVersion > FORMAT_VERSION) {
                throw BackupException("Backup dibuat oleh versi aplikasi yang lebih baru (format ${manifest.formatVersion}). Perbarui aplikasi terlebih dahulu.")
            }

            // data.json + checksum
            val dataEntry = z.getEntry(manifest.data.path) ?: throw BackupException("data.json tidak ditemukan di arsip.")
            val dataBytes = readEntry(z, dataEntry)
            if (sha256(dataBytes) != manifest.data.sha256) throw BackupException("data.json rusak (checksum tidak cocok).")
            val data = try {
                json.decodeFromString(AppData.serializer(), dataBytes.toString(Charsets.UTF_8))
            } catch (e: SerializationException) {
                throw BackupException("Isi data.json tidak valid: ${e.message?.take(160)}")
            } catch (e: IllegalArgumentException) {
                throw BackupException("Isi data.json tidak valid: ${e.message?.take(160)}")
            }

            val errors = BackupValidator.validate(data)
            if (errors.isNotEmpty()) {
                throw BackupException("Data backup tidak konsisten:\n• " + errors.take(5).joinToString("\n• ") + if (errors.size > 5) "\n• (+${errors.size - 5} lainnya)" else "")
            }

            // Lampiran: hanya path yang tercantum di manifest, nama file aman, checksum cocok
            var total = dataBytes.size.toLong()
            val warnings = mutableListOf<String>()
            manifest.attachments.forEach { fe ->
                val name = fe.path.removePrefix(ATTACHMENTS)
                if (!fe.path.startsWith(ATTACHMENTS) || !isSafeFileName(name)) throw BackupException("Nama lampiran tidak aman: ${fe.path}")
                val entry = z.getEntry(fe.path)
                if (entry == null) {
                    warnings += "Lampiran $name tercantum di manifest tetapi tidak ada di arsip."
                    return@forEach
                }
                val bytes = readEntry(z, entry)
                total += bytes.size
                if (total > MAX_TOTAL_BYTES) throw BackupException("Ukuran arsip melebihi batas aman.")
                if (sha256(bytes) != fe.sha256) throw BackupException("Lampiran $name rusak (checksum tidak cocok).")
                File(staging, name).writeBytes(bytes)
            }

            val staged = staging.list()?.toSet() ?: emptySet()
            val missing = (data.attachments.map { it.fileName } + data.vehicles.map { it.photo })
                .filter { it.isNotBlank() && it !in staged }
                .distinct()
            if (missing.isNotEmpty()) {
                warnings += "${missing.size} lampiran tidak ikut di backup; catatannya tetap dipulihkan dan ditandai \"file tidak ditemukan\"."
            }
            return Preview(data, manifest, warnings, staging)
        }
    }

    /**
     * Memindahkan lampiran hasil [inspect] ke folder aplikasi. Dipanggil SETELAH data
     * berhasil dipulihkan di database. Untuk mode ganti, file yang tidak lagi dirujuk dibersihkan.
     */
    fun installAttachments(preview: Preview, replace: Boolean, referenced: Set<String>) {
        attachmentsDir.mkdirs()
        preview.stagedDir.listFiles()?.forEach { f ->
            val target = File(attachmentsDir, f.name)
            if (replace || !target.exists()) f.copyTo(target, overwrite = true)
        }
        if (replace) {
            attachmentsDir.listFiles()?.forEach { if (it.isFile && it.name !in referenced) it.delete() }
        }
        preview.stagedDir.deleteRecursively()
    }

    // ---------------------------------------------------------------- util

    private fun readEntry(z: ZipFile, e: ZipEntry): ByteArray {
        if (e.isDirectory) throw BackupException("Entri arsip tidak valid: ${e.name}")
        if (e.size > MAX_ENTRY_BYTES) throw BackupException("File ${e.name} terlalu besar.")
        z.getInputStream(e).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var read = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                read += n
                // Proteksi zip bomb: ukuran deklarasi bisa dipalsukan
                if (read > MAX_ENTRY_BYTES) throw BackupException("File ${e.name} terlalu besar.")
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }

    private fun counts(d: AppData) = mapOf(
        "vehicles" to d.vehicles.size,
        "visits" to d.visits.size,
        "tasks" to d.tasks.size,
        "reminders" to d.reminders.size,
        "components" to d.components.size,
        "expenses" to d.expenses.size,
        "attachments" to d.attachments.size,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}

/** Pemeriksaan integritas relasi data sebelum restore. */
object BackupValidator {
    fun validate(d: AppData): List<String> = buildList {
        fun <T> dupes(name: String, list: List<T>, id: (T) -> String) {
            val dup = list.groupBy(id).filterValues { it.size > 1 }.keys
            if (dup.isNotEmpty()) add("ID $name ganda: ${dup.first()}")
        }
        dupes("kendaraan", d.vehicles) { it.id }
        dupes("odometer", d.odometerLogs) { it.id }
        dupes("kategori", d.categories) { it.id }
        dupes("item katalog", d.items) { it.id }
        dupes("reminder", d.reminders) { it.id }
        dupes("bengkel", d.workshops) { it.id }
        dupes("kunjungan", d.visits) { it.id }
        dupes("pekerjaan", d.tasks) { it.id }
        dupes("komponen", d.components) { it.id }
        dupes("pemasangan", d.installations) { it.id }
        dupes("pengeluaran", d.expenses) { it.id }
        dupes("lampiran", d.attachments) { it.id }
        dupes("inspeksi", d.inspections) { it.id }
        dupes("hasil inspeksi", d.inspectionResults) { it.id }

        val vehicles = d.vehicles.map { it.id }.toSet()
        val visits = d.visits.associateBy { it.id }
        val categories = d.categories.map { it.id }.toSet()
        val components = d.components.map { it.id }.toSet()
        val inspections = d.inspections.map { it.id }.toSet()

        fun orphan(what: String, count: Int) { if (count > 0) add("$count $what merujuk data yang tidak ada") }
        orphan("catatan odometer", d.odometerLogs.count { it.vehicleId !in vehicles })
        orphan("item katalog", d.items.count { it.categoryId !in categories })
        orphan("reminder", d.reminders.count { it.vehicleId !in vehicles })
        orphan("kunjungan servis", d.visits.count { it.vehicleId !in vehicles })
        orphan("pekerjaan servis", d.tasks.count { visits[it.visitId] == null })
        orphan("komponen", d.components.count { it.vehicleId !in vehicles })
        orphan("pemasangan komponen", d.installations.count { it.componentId !in components })
        orphan("pengeluaran", d.expenses.count { it.vehicleId !in vehicles })
        orphan("lampiran", d.attachments.count { it.vehicleId !in vehicles })
        orphan("inspeksi", d.inspections.count { it.vehicleId !in vehicles })
        orphan("hasil inspeksi", d.inspectionResults.count { it.inspectionId !in inspections })

        // Data kendaraan tidak boleh tercampur antarprofil
        val mixed = d.tasks.count { t -> visits[t.visitId]?.let { it.vehicleId != t.vehicleId } == true }
        if (mixed > 0) add("$mixed pekerjaan tercatat pada kendaraan yang berbeda dari kunjungannya")

        val unsafe = (d.attachments.map { it.fileName } + d.vehicles.map { it.photo }.filter { it.isNotBlank() })
            .filterNot(::isSafeFileName)
        if (unsafe.isNotEmpty()) add("Nama file lampiran tidak aman: ${unsafe.first()}")

        if (d.visits.any { it.extraFee < 0 || it.discount < 0 } || d.tasks.any { it.unitPrice < 0 || it.laborCost < 0 || it.quantityMilli <= 0 } ||
            d.expenses.any { it.amount < 0 }
        ) {
            add("Terdapat nilai biaya/jumlah negatif")
        }
    }
}
