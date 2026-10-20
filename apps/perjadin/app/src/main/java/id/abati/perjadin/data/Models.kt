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
    REPORTED("Dilaporkan"),
    CANCELLED("Dibatalkan");

    /** Masih perlu ditindaklanjuti (belum dilaporkan & tidak dibatalkan). */
    val isOpen: Boolean get() = this != REPORTED && this != CANCELLED

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
    /** Nama file tanda tangan (PNG) di folder branding/, kosong = belum ada. */
    val signatureFile: String = "",
    /** Nama file logo perusahaan di folder branding/, kosong = tanpa logo. */
    val logoFile: String = "",
) {
    val isComplete: Boolean get() = name.isNotBlank() && department.isNotBlank()
}

/** Dasar perhitungan anggaran. Per hari/malam mengikuti durasi perjalanan secara otomatis. */
enum class BudgetBasis(val label: String, val unit: String) {
    FIXED("Nominal tetap", ""),
    PER_DAY("Per hari", "hari"),
    PER_NIGHT("Per malam", "malam"),
    PER_QTY("Jumlah × tarif", "×");

    companion object {
        fun of(name: String?): BudgetBasis = entries.firstOrNull { it.name == name } ?: FIXED
    }
}

/** Plafon anggaran dari kantor untuk satu pos biaya. */
data class BudgetItem(
    val id: String = newId(),
    val categoryId: String = "TRANSPORT",
    val description: String = "",
    val amount: Long = 0,
    val basis: BudgetBasis = BudgetBasis.FIXED,
    /** Tarif per satuan (dipakai jika [basis] bukan FIXED). */
    val rate: Long = 0,
    /** Jumlah satuan manual (hanya untuk PER_QTY). */
    val qty: Long = 0,
) {
    /** Jumlah satuan efektif: hari = durasi, malam = durasi − 1. */
    fun qtyFor(durationDays: Long): Long = when (basis) {
        BudgetBasis.FIXED -> 0
        BudgetBasis.PER_DAY -> durationDays
        BudgetBasis.PER_NIGHT -> (durationDays - 1).coerceAtLeast(0)
        BudgetBasis.PER_QTY -> qty
    }

    fun recalc(durationDays: Long): BudgetItem =
        if (basis == BudgetBasis.FIXED) this else copy(amount = rate * qtyFor(durationDays))

    /** Rumus untuk ditampilkan, mis. "4 hari × Rp150.000"; null untuk nominal tetap. */
    fun formula(durationDays: Long, rupiah: (Long) -> String): String? = when (basis) {
        BudgetBasis.FIXED -> null
        BudgetBasis.PER_QTY -> "${qtyFor(durationDays)} × ${rupiah(rate)}"
        else -> "${qtyFor(durationDays)} ${basis.unit} × ${rupiah(rate)}"
    }
}

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
    /** Tanggal laporan diajukan ke kantor (epoch day), -1 = belum. */
    val submittedDay: Long = -1,
    /** Tanggal reimburse dibayar / sisa uang muka dikembalikan, -1 = belum selesai. */
    val settledDay: Long = -1,
    /** Nominal yang benar-benar diterima / dikembalikan saat penyelesaian. */
    val settledAmount: Long = 0,
    val settlementNote: String = "",
) {
    val totalBudget: Long get() = budgets.sumOf { it.amount }
    val totalActual: Long get() = expenses.sumOf { it.amount }

    /** Positif = masih tersisa, negatif = melebihi anggaran. */
    val remaining: Long get() = totalBudget - totalActual
    val durationDays: Long get() = endDay - startDay + 1
    val usage: Float get() = if (totalBudget <= 0) 0f else totalActual.toFloat() / totalBudget

    /** Positif = sisa uang muka dikembalikan, negatif = kantor membayar kekurangan. */
    val settlement: Long get() = advance - totalActual

    /** Positif = kantor wajib membayar ke pegawai (reimburse), negatif = pegawai mengembalikan ke kantor. */
    val amountDueToEmployee: Long get() = totalActual - advance
    val isSubmitted: Boolean get() = submittedDay >= 0
    val isSettled: Boolean get() = settledDay >= 0
    /** Masih ada uang yang harus diselesaikan dengan kantor. */
    val needsSettlement: Boolean get() = !isSettled && amountDueToEmployee != 0L && (totalActual > 0 || advance > 0)

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

    /** Menghitung ulang anggaran per hari/malam sesuai durasi saat ini. */
    fun withRecalculatedBudgets(): Trip = copy(budgets = budgets.map { it.recalc(durationDays) })

    /**
     * Salinan perjalanan sebagai template: anggaran disalin (per hari/malam dihitung ulang
     * untuk tanggal baru); pengeluaran, uang muka & status tidak ikut.
     */
    fun duplicate(odrNumber: String, startDay: Long, endDay: Long): Trip = Trip(
        odrNumber = odrNumber,
        purpose = purpose,
        origin = origin,
        destination = destination,
        startDay = startDay,
        endDay = endDay,
        travelerName = travelerName,
        employeeId = employeeId,
        department = department,
        notes = notes,
        budgets = budgets.map { it.copy(id = newId()) },
    ).withRecalculatedBudgets()
}

data class AppData(
    val profile: Profile = Profile(),
    val trips: List<Trip> = emptyList(),
    /** Semua kategori (bawaan & buatan pengguna) yang bisa diubah. Kosong = pakai bawaan. */
    val storedCategories: List<Category> = emptyList(),
    val onboardingDone: Boolean = false,
    val reminderEnabled: Boolean = false,
    /** Jam pengingat harian (0–23). */
    val reminderHour: Int = 20,
    /** Waktu ekspor backup terakhir (epoch millis), 0 = belum pernah. */
    val lastBackupAt: Long = 0,
) {
    fun trip(id: String): Trip? = trips.firstOrNull { it.id == id }

    val categories: List<Category> get() = storedCategories.ifEmpty { Category.BUILT_INS }

    /**
     * Perjalanan yang paling relevan hari ini (untuk widget & catat cepat):
     * yang sedang berlangsung → yang akan datang terdekat → yang terakhir selesai tapi belum dilaporkan.
     */
    fun focusTrip(today: Long): Trip? {
        val open = trips.filter { it.status.isOpen }
        return open.filter { today in it.startDay..it.endDay || it.status == TripStatus.ONGOING }.maxByOrNull { it.startDay }
            ?: open.filter { it.startDay > today }.minByOrNull { it.startDay }
            ?: open.maxByOrNull { it.endDay }
    }

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
    .put("signatureFile", signatureFile).put("logoFile", logoFile)

fun profileFromJson(o: JSONObject?) = if (o == null) Profile() else Profile(
    name = o.str("name"), employeeId = o.str("employeeId"), position = o.str("position"),
    department = o.str("department"), company = o.str("company"),
    approverName = o.str("approverName"), approverTitle = o.str("approverTitle"),
    signatureFile = o.str("signatureFile"), logoFile = o.str("logoFile"),
)

fun BudgetItem.toJson() = JSONObject()
    .put("id", id).put("category", categoryId).put("description", description).put("amount", amount)
    .put("basis", basis.name).put("rate", rate).put("qty", qty)

fun budgetFromJson(o: JSONObject) = BudgetItem(
    id = o.optString("id", newId()), categoryId = o.optString("category", Category.OTHER_ID),
    description = o.str("description"), amount = o.optLong("amount"),
    basis = BudgetBasis.of(o.optString("basis")), rate = o.optLong("rate"), qty = o.optLong("qty"),
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
    .put("submittedDay", submittedDay).put("settledDay", settledDay)
    .put("settledAmount", settledAmount).put("settlementNote", settlementNote)

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
    submittedDay = o.optLong("submittedDay", -1), settledDay = o.optLong("settledDay", -1),
    settledAmount = o.optLong("settledAmount"), settlementNote = o.str("settlementNote"),
)

fun Category.toJson() = JSONObject()
    .put("id", id).put("label", label).put("example", example).put("color", color).put("icon", icon).put("builtIn", builtIn)

fun categoryFromJson(o: JSONObject) = Category(
    id = o.optString("id", newId()), label = o.str("label"), example = o.str("example"),
    color = o.optLong("color", 0xFF64748B), icon = o.optString("icon", "more"),
    builtIn = o.optBoolean("builtIn", false),
)

fun AppData.toJson(): JSONObject = JSONObject()
    .put("version", DATA_VERSION)
    .put("categories", JSONArray(storedCategories.map { it.toJson() }))
    .put("profile", profile.toJson())
    .put("onboardingDone", onboardingDone)
    .put("reminderEnabled", reminderEnabled).put("reminderHour", reminderHour)
    .put("lastBackupAt", lastBackupAt)
    .put("trips", JSONArray(trips.map { it.toJson() }))

fun appDataFromJson(o: JSONObject) = AppData(
    profile = profileFromJson(o.optJSONObject("profile")),
    trips = o.optJSONArray("trips").mapObjects(::tripFromJson),
    storedCategories = o.optJSONArray("categories").mapObjects(::categoryFromJson).let { list ->
        // v2 hanya menyimpan kategori buatan pengguna; v3 menyimpan semua kategori
        if (o.optInt("version", 1) < 3 && list.isNotEmpty()) Category.BUILT_INS + list else list
    },
    onboardingDone = o.optBoolean("onboardingDone", false),
    reminderEnabled = o.optBoolean("reminderEnabled", false),
    reminderHour = o.optInt("reminderHour", 20),
    lastBackupAt = o.optLong("lastBackupAt", 0),
)

// ---------- Versi data, migrasi & validasi ----------

/**
 * Riwayat versi format data:
 * 1 = awal (tanpa field version), kategori enum bawaan.
 * 2 = "categories" berisi kategori buatan pengguna saja.
 * 3 = "categories" berisi semua kategori (bawaan + buatan pengguna).
 * 4 = dasar anggaran, foto struk, status reimburse, pengingat, logo/tanda tangan, lastBackupAt.
 * Semua field baru opsional dengan nilai default, sehingga data lama selalu terbaca.
 */
const val DATA_VERSION = 4

class UnsupportedDataVersionException(val found: Int) :
    Exception("Data dibuat oleh versi aplikasi yang lebih baru (format $found, didukung maks $DATA_VERSION). Perbarui aplikasi Perjadin terlebih dulu.")

fun dataVersionOf(o: JSONObject): Int = o.optInt("version", 1)

/** Membaca data dari teks JSON, menolak format yang lebih baru dari aplikasi ini. */
fun parseAppData(text: String): AppData {
    val o = JSONObject(text)
    val version = dataVersionOf(o)
    if (version > DATA_VERSION) throw UnsupportedDataVersionException(version)
    return appDataFromJson(o)
}

/**
 * Memeriksa konsistensi data sebelum dipakai (mis. saat restore).
 * Mengembalikan daftar masalah; kosong berarti data valid.
 */
fun validateAppData(data: AppData): List<String> {
    val issues = mutableListOf<String>()
    data.trips.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { issues += "ID perjalanan ganda: $it" }
    data.categories.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { issues += "ID kategori ganda: $it" }
    data.trips.forEach { t ->
        val name = t.odrNumber.ifBlank { t.id }
        if (t.endDay < t.startDay) issues += "$name: tanggal pulang sebelum tanggal berangkat"
        if (t.advance < 0) issues += "$name: uang muka negatif"
        if (t.budgets.any { it.amount < 0 || it.rate < 0 || it.qty < 0 }) issues += "$name: ada anggaran bernilai negatif"
        if (t.expenses.any { it.amount < 0 }) issues += "$name: ada pengeluaran bernilai negatif"
        if (t.budgets.map { it.id }.toSet().size != t.budgets.size) issues += "$name: ID anggaran ganda"
        if (t.expenses.map { it.id }.toSet().size != t.expenses.size) issues += "$name: ID pengeluaran ganda"
    }
    // Nama file aset harus nama file biasa, bukan path (mencegah path traversal)
    val fileNames = data.trips.flatMap { t -> t.expenses.map { it.photo } } + data.profile.logoFile + data.profile.signatureFile
    fileNames.filter { it.isNotBlank() && !isSafeFileName(it) }.forEach { issues += "Nama file aset tidak valid: $it" }
    return issues
}

private val SAFE_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")

/** Nama file tanpa direktori, tanpa "..", hanya karakter aman. */
fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name) && !name.contains("..")

