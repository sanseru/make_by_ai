package id.abati.perjadin.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Penyimpanan lokal berbasis satu file JSON di internal storage aplikasi.
 * State di-update sinkron di memori; penulisan ke disk dilakukan berurutan
 * pada satu thread background dengan AtomicFile agar file tidak korup.
 */
class Repository private constructor(dir: File) {

    private val file = AtomicFile(File(dir, "perjadin-data.json"))
    private val writer = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppData> = _state.asStateFlow()

    private fun load(): AppData = try {
        appDataFromJson(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    } catch (_: Exception) {
        AppData()
    }

    private fun update(transform: (AppData) -> AppData) {
        val json = _state.updateAndGet(transform).toJson().toString()
        writer.execute {
            val out = file.startWrite()
            try {
                out.write(json.toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    private fun updateTrip(tripId: String, transform: (Trip) -> Trip) = update { d ->
        d.copy(trips = d.trips.map { if (it.id == tripId) transform(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    fun setOnboardingDone() = update { it.copy(onboardingDone = true) }

    fun saveProfile(profile: Profile) = update { it.copy(profile = profile) }

    fun upsertTrip(trip: Trip) = update { d ->
        val exists = d.trips.any { it.id == trip.id }
        val t = trip.copy(updatedAt = System.currentTimeMillis())
        d.copy(trips = if (exists) d.trips.map { if (it.id == t.id) t else it } else listOf(t) + d.trips)
    }

    fun deleteTrip(id: String) = update { d -> d.copy(trips = d.trips.filterNot { it.id == id }) }

    fun setStatus(tripId: String, status: TripStatus) = updateTrip(tripId) { it.copy(status = status) }

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

    fun exportJson(): String = state.value.toJson().toString(2)

    /** Mengganti seluruh data dengan isi backup. Melempar exception jika format tidak valid. */
    fun importJson(text: String) {
        val parsed = appDataFromJson(JSONObject(text))
        update { parsed.copy(onboardingDone = true) }
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(context.applicationContext.filesDir).also { instance = it }
        }
    }
}
