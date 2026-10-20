package id.abati.perjadin.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONObject
import id.abati.perjadin.widget.TripWidget
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Penyimpanan lokal berbasis satu file JSON di internal storage aplikasi.
 *
 * Perlindungan data:
 * - Penulisan berurutan di satu thread background memakai AtomicFile (tidak setengah tertulis).
 * - Sebelum penulisan pertama tiap sesi, file yang terakhir valid disalin ke `perjadin-data.prev.json`.
 * - File yang gagal dibaca TIDAK ditimpa: disimpan sebagai `perjadin-data.corrupt-<waktu>.json`,
 *   lalu dicoba dipulihkan dari salinan `.prev`.
 * - Data dari versi aplikasi yang lebih baru dibuka read-only agar tidak rusak karena downgrade.
 * - Sebelum migrasi format lama, salinan asli disimpan di folder `auto-backups/`.
 */
class Repository private constructor(private val appContext: Context) {

    private val dataFile = File(appContext.filesDir, "perjadin-data.json")
    private val prevFile = File(appContext.filesDir, "perjadin-data.prev.json")
    private val file = AtomicFile(dataFile)
    private val writer = Executors.newSingleThreadExecutor()

    /** Pesan untuk pengguna tentang masalah data saat dibuka (ditampilkan sekali). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** false = data tidak boleh ditulis (mis. format lebih baru dari aplikasi). */
    @Volatile
    private var writable = true
    private var loadedFromDisk = false
    private var prevSaved = false

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppData> = _state.asStateFlow()

    val autoBackupDir: File get() = File(appContext.filesDir, "auto-backups").apply { mkdirs() }

    private fun load(): AppData {
        if (!dataFile.exists() && !File(dataFile.path + ".bak").exists()) return AppData()
        return try {
            val text = String(file.readFully(), Charsets.UTF_8)
            val version = dataVersionOf(JSONObject(text))
            if (version < DATA_VERSION) {
                File(autoBackupDirUnsafe(), "pre-migration-v$version-${System.currentTimeMillis()}.json").writeText(text)
            }
            parseAppData(text).also { loadedFromDisk = true }
        } catch (e: UnsupportedDataVersionException) {
            writable = false
            _notice.value = "${e.message}\n\nData dibuka dalam mode baca-saja agar tidak rusak."
            AppData(onboardingDone = true)
        } catch (e: Exception) {
            // Jangan pernah menimpa file yang rusak: amankan dulu salinannya
            val quarantine = File(appContext.filesDir, "perjadin-data.corrupt-${System.currentTimeMillis()}.json")
            runCatching { dataFile.copyTo(quarantine, overwrite = true) }
            val recovered = runCatching { parseAppData(prevFile.readText()) }.getOrNull()
            if (recovered != null) {
                _notice.value = "File data utama rusak dan telah dipulihkan dari salinan terakhir yang valid. " +
                    "File rusak disimpan sebagai ${quarantine.name}."
                recovered
            } else {
                _notice.value = "File data tidak dapat dibaca (${e.message}). Salinannya disimpan sebagai ${quarantine.name}. " +
                    "Pulihkan dari file backup melalui Pengaturan → Pulihkan dari Backup."
                AppData(onboardingDone = true)
            }
        }
    }

    // Dipakai saat konstruksi (sebelum properti lazy lain siap)
    private fun autoBackupDirUnsafe(): File = File(appContext.filesDir, "auto-backups").apply { mkdirs() }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun writeToDisk(json: String) {
        // Salinan file valid terakhir sebelum penulisan pertama di sesi ini
        if (!prevSaved && loadedFromDisk && dataFile.exists()) {
            runCatching { dataFile.copyTo(prevFile, overwrite = true) }
            prevSaved = true
        }
        val out = file.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            loadedFromDisk = true
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun update(transform: (AppData) -> AppData) {
        if (!writable) return
        val json = _state.updateAndGet(transform).toJson().toString()
        writer.execute {
            runCatching { writeToDisk(json) }
            // Segarkan widget layar utama setiap data berubah
            runCatching { TripWidget.updateAll(appContext) }
        }
    }

    /**
     * Mengganti seluruh data lalu menunggu sampai benar-benar tersimpan di disk
     * (dipakai oleh restore, agar kegagalan tulis bisa di-rollback oleh pemanggil).
     */
    fun replaceAllBlocking(data: AppData) {
        check(writable) { "Data sedang dalam mode baca-saja" }
        val json = data.toJson().toString()
        writer.submit(Callable { writeToDisk(json) }).get()
        _state.value = data
        writer.execute { runCatching { TripWidget.updateAll(appContext) } }
    }

    /** Menunggu semua penulisan yang tertunda selesai (sebelum backup/restore). */
    fun flush() {
        writer.submit(Callable { }).get()
    }

    private fun updateTrip(tripId: String, transform: (Trip) -> Trip) = update { d ->
        d.copy(trips = d.trips.map { if (it.id == tripId) transform(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun setOnboardingDone() = update { it.copy(onboardingDone = true) }

    fun saveProfile(profile: Profile) = update { it.copy(profile = profile) }

    fun upsertTrip(trip: Trip) = update { d ->
        val exists = d.trips.any { it.id == trip.id }
        // Anggaran per hari/malam ikut berubah jika tanggal perjalanan diubah
        val t = trip.withRecalculatedBudgets().copy(updatedAt = System.currentTimeMillis())
        d.copy(trips = if (exists) d.trips.map { if (it.id == t.id) t else it } else listOf(t) + d.trips)
    }

    /** Menyalin perjalanan sebagai template; mengembalikan id perjalanan baru. */
    fun duplicateTrip(sourceId: String, odrNumber: String, startDay: Long, endDay: Long): String? {
        val source = state.value.trip(sourceId) ?: return null
        val copy = source.duplicate(odrNumber, startDay, endDay)
        upsertTrip(copy)
        return copy.id
    }

    fun deleteTrip(id: String) = update { d -> d.copy(trips = d.trips.filterNot { it.id == id }) }

    /** Menandai "Dilaporkan" otomatis mengisi tanggal pengajuan jika belum ada. */
    fun setStatus(tripId: String, status: TripStatus) = updateTrip(tripId) { t ->
        val submitted = if (status == TripStatus.REPORTED && !t.isSubmitted) LocalDate.now().toEpochDay() else t.submittedDay
        t.copy(status = status, submittedDay = submitted)
    }

    fun updateSettlement(tripId: String, submittedDay: Long, settledDay: Long, settledAmount: Long, note: String) =
        updateTrip(tripId) { it.copy(submittedDay = submittedDay, settledDay = settledDay, settledAmount = settledAmount, settlementNote = note) }

    fun setReminder(enabled: Boolean, hour: Int) = update { it.copy(reminderEnabled = enabled, reminderHour = hour) }

    fun upsertBudget(tripId: String, item: BudgetItem) = updateTrip(tripId) { t ->
        val list = if (t.budgets.any { it.id == item.id }) t.budgets.map { if (it.id == item.id) item else it } else t.budgets + item
        t.copy(budgets = list)
    }

    fun deleteBudget(tripId: String, id: String) = updateTrip(tripId) { t -> t.copy(budgets = t.budgets.filterNot { it.id == id }) }

    fun upsertExpense(tripId: String, e: Expense) = updateTrip(tripId) { t ->
        val list = if (t.expenses.any { it.id == e.id }) t.expenses.map { if (it.id == e.id) e else it } else t.expenses + e
        t.copy(expenses = list.sortedBy { it.day })
    }

    fun deleteExpense(tripId: String, id: String) = updateTrip(tripId) { t -> t.copy(expenses = t.expenses.filterNot { it.id == id }) }

    fun upsertCategory(c: Category) = update { d ->
        val base = d.categories
        val list = if (base.any { it.id == c.id }) base.map { if (it.id == c.id) c else it } else base + c
        d.copy(storedCategories = list)
    }

    /** Hanya menghapus kategori yang tidak dipakai, dan minimal 1 kategori harus tersisa. */
    fun deleteCategory(id: String): Boolean {
        val d = state.value
        if (d.isCategoryUsed(id) || d.categories.size <= 1) return false
        update { it.copy(storedCategories = it.categories.filterNot { c -> c.id == id }) }
        return true
    }

    /** Menghapus file foto struk yang tidak lagi dirujuk pengeluaran mana pun. */
    fun cleanupReceipts(receiptDir: File) {
        val used = state.value.trips.flatMap { t -> t.expenses.map { it.photo } }.filter { it.isNotBlank() }.toSet()
        writer.execute {
            receiptDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
        }
    }

    fun loadDemo() = update { d -> d.copy(trips = DemoData.trips(d.profile) + d.trips) }

    fun clearAll() = update { AppData(onboardingDone = true) }

    fun markBackedUp(time: Long = System.currentTimeMillis()) = update { it.copy(lastBackupAt = time) }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext).also { instance = it }
        }
    }
}
