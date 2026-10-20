package id.abati.perjadin.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Kategori biaya perjalanan dinas. [color] dipakai di UI dan PDF. */
enum class Category(val label: String, val example: String, val color: Long) {
    TRANSPORT("Tiket Transportasi", "Contoh: Tiket pesawat Jakarta–Medan PP", 0xFF2563EB),
    HOTEL("Penginapan", "Contoh: Hotel Santika Medan 3 malam", 0xFF7C3AED),
    PER_DIEM("Uang Harian", "Contoh: Uang harian 4 hari × Rp150.000", 0xFF0F766E),
    MEAL("Konsumsi", "Contoh: Makan siang dengan tim cabang", 0xFFEA580C),
    LOCAL_TRANSPORT("Transport Lokal", "Contoh: Taksi bandara Kualanamu → hotel", 0xFFCA8A04),
    COMMUNICATION("Komunikasi", "Contoh: Paket data / pulsa selama dinas", 0xFF0891B2),
    OTHER("Lain-lain", "Contoh: Laundry, bagasi tambahan, fotokopi", 0xFF64748B);

    companion object {
        fun of(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

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
    val category: Category = Category.TRANSPORT,
    val description: String = "",
    val amount: Long = 0,
)

/** Pengeluaran riil (realisasi) yang dibayar selama perjalanan. */
data class Expense(
    val id: String = newId(),
    val day: Long,
    val category: Category = Category.MEAL,
    val description: String = "",
    val amount: Long = 0,
    val paymentMethod: String = PAYMENT_METHODS.first(),
    val receiptNo: String = "",
    val note: String = "",
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

    fun budgetFor(c: Category): Long = budgets.filter { it.category == c }.sumOf { it.amount }
    fun actualFor(c: Category): Long = expenses.filter { it.category == c }.sumOf { it.amount }

    /** Kategori yang punya anggaran atau pengeluaran, urut sesuai enum. */
    val activeCategories: List<Category>
        get() = Category.entries.filter { c -> budgets.any { it.category == c } || expenses.any { it.category == c } }

    val route: String
        get() = listOf(origin, destination).filter { it.isNotBlank() }.joinToString(" → ")
}

data class AppData(
    val profile: Profile = Profile(),
    val trips: List<Trip> = emptyList(),
    val onboardingDone: Boolean = false,
) {
    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }
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
    .put("id", id).put("category", category.name).put("description", description).put("amount", amount)

fun budgetFromJson(o: JSONObject) = BudgetItem(
    id = o.optString("id", newId()), category = Category.of(o.optString("category")),
    description = o.str("description"), amount = o.optLong("amount"),
)

fun Expense.toJson() = JSONObject()
    .put("id", id).put("day", day).put("category", category.name).put("description", description)
    .put("amount", amount).put("paymentMethod", paymentMethod).put("receiptNo", receiptNo).put("note", note)

fun expenseFromJson(o: JSONObject) = Expense(
    id = o.optString("id", newId()), day = o.optLong("day"), category = Category.of(o.optString("category")),
    description = o.str("description"), amount = o.optLong("amount"),
    paymentMethod = o.optString("paymentMethod", PAYMENT_METHODS.first()),
    receiptNo = o.str("receiptNo"), note = o.str("note"),
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

fun AppData.toJson(): JSONObject = JSONObject()
    .put("version", 1)
    .put("profile", profile.toJson())
    .put("onboardingDone", onboardingDone)
    .put("trips", JSONArray(trips.map { it.toJson() }))

fun appDataFromJson(o: JSONObject) = AppData(
    profile = profileFromJson(o.optJSONObject("profile")),
    trips = o.optJSONArray("trips").mapObjects(::tripFromJson),
    onboardingDone = o.optBoolean("onboardingDone", false),
)
