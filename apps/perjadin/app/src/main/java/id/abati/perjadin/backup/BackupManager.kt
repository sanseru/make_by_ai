package id.abati.perjadin.backup

import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.isSafeFileName
import id.abati.perjadin.data.parseAppData
import id.abati.perjadin.data.toJson
import id.abati.perjadin.data.validateAppData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup lengkap Perjadin dalam satu arsip ZIP:
 *
 * ```
 * manifest.json            format, waktu ekspor, versi app, daftar aset + SHA-256
 * data.json                seluruh data (perjalanan, kategori, profil, pengaturan)
 * assets/receipts/<file>   foto struk
 * assets/logo/<file>       logo perusahaan
 * assets/signatures/<file> tanda tangan
 * ```
 *
 * Aset dirujuk dengan nama file (bukan path absolut) sehingga bisa dipulihkan di HP lain.
 * Backup JSON lama (v1.0.x) tetap bisa dipulihkan (tanpa aset).
 *
 * Kelas ini tidak memakai API Android agar bisa diuji dengan unit test JVM.
 */
class BackupManager(
    /** Folder foto struk aplikasi (filesDir/receipts). */
    private val receiptsDir: File,
    /** Folder logo & tanda tangan aplikasi (filesDir/branding). */
    private val brandingDir: File,
    /** Folder kerja sementara (cacheDir) untuk ekstraksi sebelum diterapkan. */
    private val workDir: File,
    private val appVersion: String,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_ENTRY_BYTES = 30L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 600L * 1024 * 1024
        const val MAX_ENTRIES = 10_000
        private const val RECEIPTS = "assets/receipts/"
        private const val LOGO = "assets/logo/"
        private const val SIGNATURES = "assets/signatures/"
    }

    class BackupException(message: String) : Exception(message)

    /** Hasil pemeriksaan backup sebelum diterapkan. */
    data class Preview(
        val data: AppData,
        val isFullBackup: Boolean,
        val exportedAt: Long,
        val appVersion: String,
        val tripCount: Int,
        val expenseCount: Int,
        val photoCount: Int,
        val warnings: List<String>,
        /** Folder berisi aset yang sudah diekstrak & diverifikasi (null untuk backup JSON lama). */
        internal val stagedAssets: File?,
    )

    // ---------------------------------------------------------------- ekspor

    /** Menulis backup lengkap ke [out]. Hanya aset yang dirujuk data & benar-benar ada yang disertakan. */
    fun export(data: AppData, out: OutputStream, now: Long = System.currentTimeMillis()) {
        val assets = mutableListOf<Pair<String, File>>()
        data.trips.flatMap { t -> t.expenses.map { it.photo } }.filter { it.isNotBlank() }.distinct().forEach { name ->
            File(receiptsDir, name).takeIf { it.isFile }?.let { assets += RECEIPTS + name to it }
        }
        data.profile.logoFile.takeIf { it.isNotBlank() }?.let { File(brandingDir, it) }?.takeIf { it.isFile }
            ?.let { assets += LOGO + it.name to it }
        data.profile.signatureFile.takeIf { it.isNotBlank() }?.let { File(brandingDir, it) }?.takeIf { it.isFile }
            ?.let { assets += SIGNATURES + it.name to it }

        val dataBytes = data.toJson().toString(2).toByteArray(Charsets.UTF_8)
        val manifest = JSONObject()
            .put("app", "Perjadin")
            .put("formatVersion", FORMAT_VERSION)
            .put("appVersion", appVersion)
            .put("exportedAt", now)
            .put("data", JSONObject().put("path", "data.json").put("sha256", sha256(dataBytes)).put("size", dataBytes.size))
            .put(
                "assets",
                JSONArray(
                    assets.map { (path, f) ->
                        JSONObject().put("path", path).put("sha256", sha256(f)).put("size", f.length())
                    },
                ),
            )

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(dataBytes)
            zip.closeEntry()
            assets.forEach { (path, f) ->
                zip.putNextEntry(ZipEntry(path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ---------------------------------------------------------------- inspeksi

    /**
     * Memeriksa file backup (ZIP baru atau JSON lama) TANPA mengubah data aplikasi.
     * Melempar [BackupException] dengan pesan yang bisa ditampilkan ke pengguna jika tidak valid.
     */
    fun inspect(file: File): Preview {
        if (!file.isFile || file.length() == 0L) throw BackupException("File backup kosong atau tidak ditemukan.")
        val isZip = file.inputStream().use { s -> ByteArray(4).also { s.read(it) } }
            .contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        return if (isZip) inspectZip(file) else inspectLegacyJson(file)
    }

    private fun inspectLegacyJson(file: File): Preview {
        if (file.length() > MAX_ENTRY_BYTES) throw BackupException("File terlalu besar untuk backup JSON.")
        val data = parseData(file.readText(Charsets.UTF_8))
        val photoRefs = data.trips.sumOf { t -> t.expenses.count { it.photo.isNotBlank() } }
        val warnings = buildList {
            add("Backup format lama (.json): foto struk, logo & tanda tangan tidak termasuk.")
            if (photoRefs > 0) add("$photoRefs pengeluaran merujuk foto; foto hanya tampil jika file masih ada di HP ini.")
        }
        return preview(data, full = false, exportedAt = 0, version = "-", warnings = warnings, staged = null)
    }

    private fun inspectZip(file: File): Preview {
        val staging = File(workDir, "restore-staging").apply { deleteRecursively(); mkdirs() }
        try {
            var manifestText: String? = null
            var dataText: String? = null
            val digests = mutableMapOf<String, String>()
            var total = 0L
            var count = 0

            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ENTRIES) throw BackupException("Backup berisi terlalu banyak file.")
                    if (entry.isDirectory) continue
                    val name = entry.name
                    when {
                        name == "manifest.json" -> manifestText = readLimited(zip, name).toString(Charsets.UTF_8)
                        name == "data.json" -> dataText = readLimited(zip, name).also { digests[name] = sha256(it) }.toString(Charsets.UTF_8)
                        else -> {
                            val folder = assetFolder(name) ?: throw BackupException("Isi backup tidak dikenal: $name")
                            val fileName = name.removePrefix(folder)
                            // Cegah path traversal (mis. "../../") & nama aneh
                            if (!isSafeFileName(fileName)) throw BackupException("Nama file tidak aman di dalam backup: $name")
                            val target = File(File(staging, folder), fileName).apply { parentFile?.mkdirs() }
                            val digest = MessageDigest.getInstance("SHA-256")
                            var size = 0L
                            target.outputStream().use { out ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n < 0) break
                                    size += n
                                    total += n
                                    if (size > MAX_ENTRY_BYTES) throw BackupException("File terlalu besar di dalam backup: $name")
                                    if (total > MAX_TOTAL_BYTES) throw BackupException("Ukuran backup melebihi batas aman.")
                                    digest.update(buf, 0, n)
                                    out.write(buf, 0, n)
                                }
                            }
                            digests[name] = digest.digest().toHex()
                        }
                    }
                }
            }

            val manifest = manifestText?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: throw BackupException("Backup tidak memiliki manifest.json yang valid.")
            val format = manifest.optInt("formatVersion", -1)
            if (format < 1) throw BackupException("Format backup tidak dikenal.")
            if (format > FORMAT_VERSION) {
                throw BackupException("Backup dibuat oleh versi aplikasi yang lebih baru (format $format). Perbarui aplikasi Perjadin terlebih dulu.")
            }
            val text = dataText ?: throw BackupException("Backup tidak memiliki data.json.")

            // Verifikasi checksum semua file yang tercatat di manifest
            val expectedData = manifest.optJSONObject("data")?.optString("sha256").orEmpty()
            if (expectedData.isNotEmpty() && expectedData != digests["data.json"]) {
                throw BackupException("data.json rusak (checksum tidak cocok).")
            }
            val listed = manifest.optJSONArray("assets") ?: JSONArray()
            val listedPaths = mutableSetOf<String>()
            for (i in 0 until listed.length()) {
                val a = listed.getJSONObject(i)
                val path = a.optString("path")
                listedPaths += path
                val actual = digests[path] ?: throw BackupException("File hilang dari backup: $path")
                if (actual != a.optString("sha256")) throw BackupException("File rusak di dalam backup (checksum tidak cocok): $path")
            }
            val unlisted = digests.keys.filter { it != "data.json" && it !in listedPaths }
            if (unlisted.isNotEmpty()) throw BackupException("Backup berisi file yang tidak tercatat: ${unlisted.first()}")

            val data = parseData(text)

            // Pastikan semua rujukan aset tersedia
            val warnings = mutableListOf<String>()
            val missingPhotos = data.trips.flatMap { t -> t.expenses.map { it.photo } }
                .filter { it.isNotBlank() && !File(File(staging, RECEIPTS), it).isFile }
            if (missingPhotos.isNotEmpty()) warnings += "${missingPhotos.size} foto struk tidak ada di backup (sudah hilang saat ekspor)."
            if (data.profile.logoFile.isNotBlank() && !File(File(staging, LOGO), data.profile.logoFile).isFile) warnings += "Logo tidak ada di backup."
            if (data.profile.signatureFile.isNotBlank() && !File(File(staging, SIGNATURES), data.profile.signatureFile).isFile) {
                warnings += "Tanda tangan tidak ada di backup."
            }

            return preview(
                data, full = true,
                exportedAt = manifest.optLong("exportedAt"),
                version = manifest.optString("appVersion", "-"),
                warnings = warnings, staged = staging,
            )
        } catch (e: BackupException) {
            staging.deleteRecursively()
            throw e
        } catch (e: java.util.zip.ZipException) {
            staging.deleteRecursively()
            throw BackupException("File ZIP rusak: ${e.message}")
        }
    }

    private fun parseData(text: String): AppData {
        val data = try {
            parseAppData(text)
        } catch (e: id.abati.perjadin.data.UnsupportedDataVersionException) {
            throw BackupException(e.message ?: "Versi data tidak didukung.")
        } catch (e: Exception) {
            throw BackupException("Isi data backup tidak valid: ${e.message}")
        }
        val issues = validateAppData(data)
        if (issues.isNotEmpty()) {
            throw BackupException("Data backup tidak konsisten:\n• " + issues.take(5).joinToString("\n• ") + if (issues.size > 5) "\n…" else "")
        }
        return data
    }

    private fun preview(data: AppData, full: Boolean, exportedAt: Long, version: String, warnings: List<String>, staged: File?) = Preview(
        data = data,
        isFullBackup = full,
        exportedAt = exportedAt,
        appVersion = version,
        tripCount = data.trips.size,
        expenseCount = data.trips.sumOf { it.expenses.size },
        photoCount = staged?.let { File(it, RECEIPTS).listFiles()?.size } ?: 0,
        warnings = warnings,
        stagedAssets = staged,
    )

    // ---------------------------------------------------------------- penerapan

    /**
     * Menerapkan backup yang sudah diperiksa. [commitData] harus menyimpan data secara sinkron;
     * jika gagal, folder aset dikembalikan seperti semula (data lama tetap utuh).
     */
    fun apply(preview: Preview, commitData: (AppData) -> Unit) {
        val staged = preview.stagedAssets
        if (staged == null) {
            // Backup JSON lama: aset di HP ini dibiarkan apa adanya
            commitData(preview.data)
            return
        }
        val oldReceipts = File(receiptsDir.path + ".old")
        val oldBranding = File(brandingDir.path + ".old")
        oldReceipts.deleteRecursively()
        oldBranding.deleteRecursively()
        try {
            if (receiptsDir.exists() && !receiptsDir.renameTo(oldReceipts)) throw BackupException("Gagal menyiapkan folder foto.")
            if (brandingDir.exists() && !brandingDir.renameTo(oldBranding)) throw BackupException("Gagal menyiapkan folder logo.")
            receiptsDir.mkdirs()
            brandingDir.mkdirs()
            File(staged, RECEIPTS).listFiles()?.forEach { it.copyTo(File(receiptsDir, it.name), overwrite = true) }
            // Logo & tanda tangan disimpan bersama di folder branding (dirujuk dengan nama file unik)
            File(staged, LOGO).listFiles()?.forEach { it.copyTo(File(brandingDir, it.name), overwrite = true) }
            File(staged, SIGNATURES).listFiles()?.forEach { it.copyTo(File(brandingDir, it.name), overwrite = true) }
            commitData(preview.data)
        } catch (e: Exception) {
            // Rollback folder aset
            receiptsDir.deleteRecursively()
            brandingDir.deleteRecursively()
            oldReceipts.renameTo(receiptsDir)
            oldBranding.renameTo(brandingDir)
            throw e
        }
        oldReceipts.deleteRecursively()
        oldBranding.deleteRecursively()
        staged.deleteRecursively()
    }

    fun discard(preview: Preview) {
        preview.stagedAssets?.deleteRecursively()
    }

    // ---------------------------------------------------------------- util

    private fun assetFolder(name: String): String? = listOf(RECEIPTS, LOGO, SIGNATURES).firstOrNull { name.startsWith(it) }

    private fun readLimited(input: InputStream, name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_ENTRY_BYTES) throw BackupException("File terlalu besar di dalam backup: $name")
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
