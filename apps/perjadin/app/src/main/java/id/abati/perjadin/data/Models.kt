package id.abati.perjadin.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/**
 * Kategori biaya. Kategori bawaan memakai id lama (nama enum versi 1) agar data
 * lama tetap kompatibel; kategori buatan pengguna memakai id acak & [builtIn] = false.
 * [icon] adalah kunci ikon (lihat CategoryIcons di UI), [color] warna ARGB.
 */
data class Category(
    val id: String,
    val label: String,
    val example: String,
    val color: Long,
    val icon: String,
    val builtIn: Boolean = false,
) {
    companion object {
        val BUILT_INS = listOf(
            Category("TRANSPORT", "Tiket Transportasi", "Contoh: Tiket pesawat Jakarta–Medan PP", 0xFF2563EB, "flight", true),
            Category("HOTEL", "Penginapan", "Contoh: Hotel Santika Medan 3 malam", 0xFF7C3AED, "hotel", true),
            Category("PER_DIEM", "Uang Harian", "Contoh: Uang harian 4 hari × Rp150.000", 0xFF0F766E, "wallet", true),
            Category("MEAL", "Konsumsi", "Contoh: Makan siang dengan tim cabang", 0xFFEA580C, "meal", true),
            Category("LOCAL_TRANSPORT", "Transport Lokal", "Contoh: Taksi bandara Kualanamu → hotel", 0xFFCA8A04, "taxi", true),
            Category("COMMUNICATION", "Komunikasi", "Contoh: Paket data / pulsa selama dinas", 0xFF0891B2, "wifi", true),
            Category("OTHER", "Lain-lain", "Contoh: Laundry, bagasi tambahan, fotokopi", 0xFF64748B, "more", true),
        )
        const val OTHER_ID = "OTHER"

        /** Pengganti untuk id yang tidak dikenal (mis. kategori sudah dihapus). */
        fun unknown(id: String) = Category(id, "Kategori terhapus", "", 0xFF94A3B8, "more")
    }
}

/** Cari kategori berdasarkan id; id tak dikenal menjadi "Kategori terhapus". */
fun List<Category>.find(id: String): Category = firstOrNull { it.id == id } ?: Category.unknown(id)

/** Daftar warna pilihan untuk kategori buatan pengguna. */
val CATEGORY_COLORS = listOf(
    0xFF2563EB, 0xFF7C3AED, 0xFF0F766E, 0xFFEA580C, 0xFFCA8A04,
    0xFF0891B2, 0xFFDB2777, 0xFF16A34A, 0xFFDC2626, 0xFF64748B,
)

enum class TripStatus(val label: String) {
    PLANNED("Rencana"),
    ONGOING("Berlangsung"),
    DONE("Selesai"),
    REPORTED("Dilaporkan");

    companion object {
        fun of(name: String?): TripStatus = entries.firstOrNull { it.name == name } ?: PLANNED
    }
}

val PAYMENT_METHODS = listOf(
    "Tunai",
    "Kartu Debit",
    "Kartu Kredit Perusahaan",
    "Kartu Kredit Pribadi",
    "E-Wallet (GoPay/OVO/DANA)",
    "Transfer Bank",
    "Ditagihkan langsung ke kantor",
)

data class Profile(
    val name: String = "",
    val employeeId: String = "",
    val position: String = "",
    val department: String = "",
    val company: String = "",
    val approverName: String = "",
    val approverTitle: String = "",
) {
    val isComplete: Boolean get() = name.isNotBlank() && department.isNotBlank()
}

/** Plafon anggaran dari kantor untuk satu pos biaya. */
data class BudgetItem(
    val id: String = newId(),
    val categoryId: String = "TRANSPORT",
    val description: String = "",
    val amount: Long = 0,
)

/** Pengeluaran riil (realisasi) yang dibayar selama perjalanan. */
data class Expense(
    val id: String = newId(),
    val day: Long,
    val categoryId: String = "MEAL",
    val description: String = "",
    val amount: Long = 0,
    val paymentMethod: String = PAYMENT_METHODS.first(),
    val receiptNo: String = "",
    val note: String = "",
    /** Nama file foto struk di folder receipts/ (kosong = tanpa foto). */
    val photo: String = "",
)

data class Trip(
    val id: String = newId(),
    val odrNumber: String = "",
    val purpose: String = "",
    val origin: String = "",
    val destination: String = "",
    val startDay: Long,
    val endDay: Long,
    val travelerName: String = "",
    val employeeId: String = "",
    val department: String = "",
    val advance: Long = 0,
    val status: TripStatus = TripStatus.PLANNED,
    val notes: String = "",
    val budgets: List<BudgetItem> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalBudget: Long get() = budgets.sumOf { it.amount }
    val totalActual: Long get() = expenses.sumOf { it.amount }

    /** Positif = masih tersisa, negatif = melebihi anggaran. */
    val remaining: Long get() = totalBudget - totalActual
    val durationDays: Long get() = endDay - startDay + 1
    val usage: Float get() = if (totalBudget <= 0) 0f else totalActual.toFloat() / totalBudget

    /** Positif = sisa uang muka dikembalikan, negatif = kantor membayar kekurangan. */
    val settlement: Long get() = advance - totalActual

    fun budgetFor(categoryId: String): Long = budgets.filter { it.categoryId == categoryId }.sumOf { it.amount }
    fun actualFor(categoryId: String): Long = expenses.filter { it.categoryId == categoryId }.sumOf { it.amount }

    /** Kategori yang punya anggaran atau pengeluaran, urut sesuai daftar kategori. */
    fun activeCategories(all: List<Category>): List<Category> {
        val used = (budgets.map { it.categoryId } + expenses.map { it.categoryId }).toSet()
        val known = all.filter { it.id in used }
        val orphan = used.filter { id -> all.none { it.id == id } }.map(Category::unknown)
        return known + orphan
    }

    val route: String
        get() = listOf(origin, destination).filter { it.isNotBlank() }.joinToString(" → ")
}

data class AppData(
    val profile: Profile = Profile(),
    val trips: List<Trip> = emptyList(),
    /** Semua kategori (bawaan & buatan pengguna) yang bisa diubah. Kosong = pakai bawaan. */
    val storedCategories: List<Category> = emptyList(),
    val onboardingDone: Boolean = false,
) {
    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }

    val categories: List<Category> get() = storedCategories.ifEmpty { Category.BUILT_INS }

    /** Semua pengeluaran dari seluruh perjalanan, terbaru dulu (untuk riwayat isian cepat). */
    val expenseHistory: List<Expense>
        get() = trips.sortedByDescending { it.updatedAt }.flatMap { it.expenses.reversed() }

    fun isCategoryUsed(id: String): Boolean =
        trips.any { t -> t.budgets.any { it.categoryId == id } || t.expenses.any { it.categoryId == id } }
}

// ---------- JSON (org.json bawaan Android, tanpa dependensi tambahan) ----------

private fun JSONObject.str(key: String) = optString(key, "")

fun Profile.toJson() = JSONObject()
    .put("name", name).put("employeeId", employeeId).put("position", position)
    .put("department", department).put("company", company)
    .put("approverName", approverName).put("approverTitle", approverTitle)

fun profileFromJson(o: JSONObject?) = if (o == null) Profile() else Profile(
    name = o.str("name"), employeeId = o.str("employeeId"), position = o.str("position"),
    department = o.str("department"), company = o.str("company"),
    approverName = o.str("approverName"), approverTitle = o.str("approverTitle"),
)

fun BudgetItem.toJson() = JSONObject()
    .put("id", id).put("category", categoryId).put("description", description).put("amount", amount)

fun budgetFromJson(o: JSONObject) = BudgetItem(
    id = o.optString("id", newId()), categoryId = o.optString("category", Category.OTHER_ID),
    description = o.str("description"), amount = o.optLong("amount"),
)

fun Expense.toJson() = JSONObject()
    .put("id", id).put("day", day).put("category", categoryId).put("description", description)
    .put("amount", amount).put("paymentMethod", paymentMethod).put("receiptNo", receiptNo).put("note", note)
    .put("photo", photo)

fun expenseFromJson(o: JSONObject) = Expense(
    id = o.optString("id", newId()), day = o.optLong("day"), categoryId = o.optString("category", Category.OTHER_ID),
    description = o.str("description"), amount = o.optLong("amount"),
    paymentMethod = o.optString("paymentMethod", PAYMENT_METHODS.first()),
    receiptNo = o.str("receiptNo"), note = o.str("note"), photo = o.str("photo"),
)

fun Trip.toJson(): JSONObject = JSONObject()
    .put("id", id).put("odrNumber", odrNumber).put("purpose", purpose)
    .put("origin", origin).put("destination", destination)
    .put("startDay", startDay).put("endDay", endDay)
    .put("travelerName", travelerName).put("employeeId", employeeId).put("department", department)
    .put("advance", advance).put("status", status.name).put("notes", notes)
    .put("budgets", JSONArray(budgets.map { it.toJson() }))
    .put("expenses", JSONArray(expenses.map { it.toJson() }))
    .put("createdAt", createdAt).put("updatedAt", updatedAt)

private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

fun tripFromJson(o: JSONObject) = Trip(
    id = o.optString("id", newId()), odrNumber = o.str("odrNumber"), purpose = o.str("purpose"),
    origin = o.str("origin"), destination = o.str("destination"),
    startDay = o.optLong("startDay"), endDay = o.optLong("endDay"),
    travelerName = o.str("travelerName"), employeeId = o.str("employeeId"), department = o.str("department"),
    advance = o.optLong("advance"), status = TripStatus.of(o.optString("status")), notes = o.str("notes"),
    budgets = o.optJSONArray("budgets").mapObjects(::budgetFromJson),
    expenses = o.optJSONArray("expenses").mapObjects(::expenseFromJson),
    createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
)

fun Category.toJson() = JSONObject()
    .put("id", id).put("label", label).put("example", example).put("color", color).put("icon", icon).put("builtIn", builtIn)

fun categoryFromJson(o: JSONObject) = Category(
    id = o.optString("id", newId()), label = o.str("label"), example = o.str("example"),
    color = o.optLong("color", 0xFF64748B), icon = o.optString("icon", "more"),
    builtIn = o.optBoolean("builtIn", false),
)

fun AppData.toJson(): JSONObject = JSONObject()
    .put("version", 3)
    .put("categories", JSONArray(storedCategories.map { it.toJson() }))
    .put("profile", profile.toJson())
    .put("onboardingDone", onboardingDone)
    .put("trips", JSONArray(trips.map { it.toJson() }))

fun appDataFromJson(o: JSONObject) = AppData(
    profile = profileFromJson(o.optJSONObject("profile")),
    trips = o.optJSONArray("trips").mapObjects(::tripFromJson),
    storedCategories = o.optJSONArray("categories").mapObjects(::categoryFromJson).let { list ->
        // v2 hanya menyimpan kategori buatan pengguna; v3 menyimpan semua kategori
        if (o.optInt("version", 1) < 3 && list.isNotEmpty()) Category.BUILT_INS + list else list
    },
    onboardingDone = o.optBoolean("onboardingDone", false),
)
