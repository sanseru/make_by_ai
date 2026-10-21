package id.abati.autocare.domain

import id.abati.autocare.data.AppData
import id.abati.autocare.data.Component
import id.abati.autocare.data.ComponentInstallation
import id.abati.autocare.data.Expense
import id.abati.autocare.data.InspectionRecord
import id.abati.autocare.data.OdometerSource
import id.abati.autocare.data.Reminder
import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import id.abati.autocare.data.TaskAction
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import java.time.LocalDate

/*
 * Perhitungan turunan dari AppData (pure Kotlin, diuji dengan unit test JVM).
 */

// ---------------------------------------------------------------- odometer

/** Satu pembacaan odometer, dari log manual, kunjungan servis, inspeksi, atau data awal. */
data class Reading(
    val vehicleId: String,
    val day: Long,
    val km: Long,
    val source: OdometerSource,
    /** ID log odometer manual (hanya log manual/awal yang bisa dihapus dari layar odometer). */
    val logId: String? = null,
    val refId: String? = null,
    val note: String = "",
)

fun AppData.readings(vehicleId: String): List<Reading> = buildList {
    odometerLogs.filter { it.vehicleId == vehicleId }
        .forEach { add(Reading(vehicleId, it.day, it.km, it.source, logId = it.id, note = it.note)) }
    visits.filter { it.vehicleId == vehicleId && it.status == VisitStatus.DONE && it.odometer != null }
        .forEach { add(Reading(vehicleId, it.day, it.odometer!!, OdometerSource.SERVICE, refId = it.id, note = it.workshopName)) }
    inspections.filter { it.vehicleId == vehicleId && it.odometer != null }
        .forEach { add(Reading(vehicleId, it.day, it.odometer!!, OdometerSource.INSPECTION, refId = it.id)) }
    expenses.filter { it.vehicleId == vehicleId && it.odometer != null }
        .forEach { add(Reading(vehicleId, it.day, it.odometer!!, OdometerSource.EXPENSE, refId = it.id, note = it.description)) }
}.sortedWith(compareBy<Reading>({ it.day }, { it.km }))

/** Odometer terkini = pembacaan dengan tanggal terbaru (bila sama, km tertinggi). */
fun AppData.currentOdometer(vehicleId: String): Reading? = readings(vehicleId).lastOrNull()

/**
 * Peringatan bila odometer baru lebih rendah dari catatan pada tanggal yang sama/sebelumnya,
 * atau lebih tinggi dari catatan pada tanggal setelahnya (US-02).
 */
fun AppData.odometerWarning(vehicleId: String, day: Long, km: Long, ignoreRefId: String? = null): String? {
    val others = readings(vehicleId).filter { (it.refId == null || it.refId != ignoreRefId) && (it.logId == null || it.logId != ignoreRefId) }
    val before = others.filter { it.day <= day }.maxByOrNull { it.km }
    if (before != null && km < before.km) {
        return "Lebih rendah dari catatan sebelumnya (${before.km} km). Periksa kembali angka odometer."
    }
    val after = others.filter { it.day > day }.minByOrNull { it.km }
    if (after != null && km > after.km) {
        return "Lebih tinggi dari catatan pada tanggal setelahnya (${after.km} km). Periksa tanggal & angka."
    }
    return null
}

/** Rata-rata km per hari dari pembacaan ±1 tahun terakhir (null jika data belum cukup). */
fun AppData.kmPerDay(vehicleId: String, today: Long): Double? {
    val recent = readings(vehicleId).filter { it.day >= today - 365 }
    if (recent.size < 2) return null
    val first = recent.first()
    val last = recent.last()
    val days = last.day - first.day
    if (days < 14 || last.km <= first.km) return null
    return (last.km - first.km).toDouble() / days
}

// ---------------------------------------------------------------- reminder

data class ReminderState(val reminder: Reminder, val vehicle: Vehicle?, val info: DueInfo)

/** Pekerjaan selesai (kunjungan berstatus selesai, bukan sekadar rencana) yang terhubung ke reminder. */
fun AppData.completionsOf(reminderId: String): List<Completion> =
    (tasksByReminder[reminderId] ?: emptyList())
        .filter { it.action != TaskAction.PLANNED }
        .mapNotNull { t -> visitById[t.visitId]?.takeIf { it.status == VisitStatus.DONE } }
        .map { Completion(it.day, it.odometer) }

fun AppData.reminderInfo(r: Reminder, today: Long): DueInfo =
    ReminderEngine.evaluate(
        ReminderEngine.ruleOf(r),
        completionsOf(r.id),
        currentOdometer(r.vehicleId)?.km,
        today,
        kmPerDay(r.vehicleId, today),
    )

/** Status semua reminder (opsional per kendaraan), urut dari yang paling mendesak. */
fun AppData.reminderStates(vehicleId: String?, today: Long, includeArchived: Boolean = false): List<ReminderState> =
    reminders
        .filter { vehicleId == null || it.vehicleId == vehicleId }
        .filter { includeArchived || vehicleById[it.vehicleId]?.archived != true }
        .map { ReminderState(it, vehicleById[it.vehicleId], reminderInfo(it, today)) }
        .sortedWith(reminderOrder)

val reminderOrder: Comparator<ReminderState> = compareByDescending<ReminderState> { it.info.status.severity }
    .thenBy { it.info.status == DueStatus.DONE }
    .thenBy { urgencyDays(it.info) }
    .thenBy { it.reminder.title }

/** Perkiraan hari menuju jatuh tempo, gabungan dimensi tanggal & km (untuk pengurutan). */
private fun urgencyDays(i: DueInfo): Long = listOfNotNull(
    i.daysLeft,
    i.kmDaysLeft ?: i.kmLeft?.let { it / 40 }, // tanpa estimasi: anggap ±40 km/hari
).minOrNull() ?: Long.MAX_VALUE

// ---------------------------------------------------------------- komponen

data class ComponentState(
    val component: Component,
    /** Episode yang sedang terpasang (null jika belum/tidak ada). */
    val current: ComponentInstallation?,
    /** Seluruh episode, terbaru di atas. */
    val history: List<ComponentInstallation>,
    val info: DueInfo?,
) {
    /** Label kondisi untuk dashboard: normal / segera diperiksa / terlambat diganti. */
    val conditionLabel: String
        get() = when (info?.status) {
            DueStatus.OVERDUE, DueStatus.DUE -> "Terlambat diganti"
            DueStatus.DUE_SOON -> "Segera diperiksa"
            null, DueStatus.UNKNOWN -> if (current == null) "Belum terpasang" else "Normal"
            else -> "Normal"
        }
}

private val installationOrder = compareByDescending<ComponentInstallation> { it.installedDay ?: Long.MIN_VALUE }
    .thenByDescending { it.installedKm ?: Long.MIN_VALUE }
    .thenByDescending { it.createdAt }

fun AppData.componentState(c: Component, today: Long): ComponentState {
    val history = (installationsByComponent[c.id] ?: emptyList()).sortedWith(installationOrder)
    val current = history.firstOrNull { !it.removed }
    val info = current?.takeIf { it.expectedLifeKm != null || it.expectedLifeMonths != null || it.nextDay != null || it.nextKm != null }
        ?.let {
            ReminderEngine.evaluate(ReminderEngine.ruleOf(it), emptyList(), currentOdometer(c.vehicleId)?.km, today, kmPerDay(c.vehicleId, today))
        }
    return ComponentState(c, current, history, info)
}

fun AppData.componentStates(vehicleId: String, today: Long): List<ComponentState> =
    components.filter { it.vehicleId == vehicleId }
        .map { componentState(it, today) }
        .sortedWith(compareByDescending<ComponentState> { it.info?.status?.severity ?: -1 }.thenBy { it.component.name.lowercase() })

/** Pekerjaan servis yang menyentuh komponen (pemasangan, pemeriksaan, perbaikan) untuk timeline. */
fun AppData.tasksOfComponent(componentId: String): List<Pair<ServiceTask, ServiceVisit>> =
    tasks.filter { it.componentId == componentId }
        .mapNotNull { t -> visitById[t.visitId]?.let { t to it } }
        .sortedByDescending { it.second.day }

// ---------------------------------------------------------------- biaya

/** Periode filter biaya/riwayat, inklusif. null = tanpa batas. */
data class Period(val from: Long? = null, val to: Long? = null) {
    operator fun contains(day: Long): Boolean = (from == null || day >= from) && (to == null || day <= to)

    companion object {
        val ALL = Period()
        fun month(today: Long): Period {
            val d = LocalDate.ofEpochDay(today)
            return Period(d.withDayOfMonth(1).toEpochDay(), d.withDayOfMonth(d.lengthOfMonth()).toEpochDay())
        }
        fun year(year: Int): Period =
            Period(LocalDate.of(year, 1, 1).toEpochDay(), LocalDate.of(year, 12, 31).toEpochDay())
        fun lastDays(today: Long, days: Long): Period = Period(today - days + 1, today)
    }
}

enum class CostGroup(val label: String) {
    PARTS("Suku cadang"), LABOR("Jasa"), EXTRA("Biaya tambahan"), DISCOUNT("Diskon"), OTHER_EXPENSE("Pengeluaran lain")
}

/** Satu entri biaya untuk rekap; diskon bernilai negatif. */
data class CostEntry(
    val vehicleId: String,
    val day: Long,
    val group: CostGroup,
    /** Kategori tampilan (kategori pengeluaran atau jenis kunjungan). */
    val label: String,
    val amount: Long,
    val refId: String,
)

fun AppData.visitCost(v: ServiceVisit): Money.VisitCost = Money.visitCost(v, tasksByVisit[v.id] ?: emptyList())

/** Semua biaya nyata: kunjungan berstatus selesai + pengeluaran lain. Draft tidak dihitung. */
fun AppData.costEntries(vehicleId: String?, period: Period): List<CostEntry> = buildList {
    visits.filter { it.status == VisitStatus.DONE && (vehicleId == null || it.vehicleId == vehicleId) && it.day in period }
        .forEach { v ->
            val c = visitCost(v)
            val label = v.type.label
            if (c.parts != 0L) add(CostEntry(v.vehicleId, v.day, CostGroup.PARTS, label, c.parts, v.id))
            if (c.labor != 0L) add(CostEntry(v.vehicleId, v.day, CostGroup.LABOR, label, c.labor, v.id))
            if (c.extraFee != 0L) add(CostEntry(v.vehicleId, v.day, CostGroup.EXTRA, label, c.extraFee, v.id))
            if (c.discount != 0L) add(CostEntry(v.vehicleId, v.day, CostGroup.DISCOUNT, label, -c.discount, v.id))
        }
    expenses.filter { (vehicleId == null || it.vehicleId == vehicleId) && it.day in period }
        .forEach { add(CostEntry(it.vehicleId, it.day, CostGroup.OTHER_EXPENSE, it.category.label, it.amount, it.id)) }
}

data class CostSummary(
    val total: Long,
    val byGroup: Map<CostGroup, Long>,
    /** Total per label (jenis kunjungan / kategori pengeluaran), terbesar di atas. */
    val byLabel: List<Pair<String, Long>>,
    val visitCount: Int,
)

fun summarize(entries: List<CostEntry>): CostSummary = CostSummary(
    total = entries.sumOf { it.amount },
    byGroup = CostGroup.entries.associateWith { g -> entries.filter { it.group == g }.sumOf { it.amount } }.filterValues { it != 0L },
    byLabel = entries.groupBy { it.label }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second },
    visitCount = entries.filter { it.group != CostGroup.OTHER_EXPENSE }.map { it.refId }.distinct().size,
)

/** Total per bulan (index 0 = Januari) untuk satu tahun. */
fun monthlyTotals(entries: List<CostEntry>, year: Int): LongArray {
    val out = LongArray(12)
    entries.forEach {
        val d = LocalDate.ofEpochDay(it.day)
        if (d.year == year) out[d.monthValue - 1] += it.amount
    }
    return out
}

// ---------------------------------------------------------------- riwayat & pencarian

sealed class HistoryEntry {
    abstract val id: String
    abstract val vehicleId: String
    abstract val day: Long
    abstract val km: Long?
    abstract val createdAt: Long

    data class Visit(val visit: ServiceVisit, val tasks: List<ServiceTask>, val total: Long) : HistoryEntry() {
        override val id get() = visit.id
        override val vehicleId get() = visit.vehicleId
        override val day get() = visit.day
        override val km get() = visit.odometer
        override val createdAt get() = visit.createdAt
    }

    data class Spend(val expense: Expense) : HistoryEntry() {
        override val id get() = expense.id
        override val vehicleId get() = expense.vehicleId
        override val day get() = expense.day
        override val km get() = expense.odometer
        override val createdAt get() = expense.createdAt
    }

    data class Inspection(val record: InspectionRecord, val problems: Int, val attention: Int) : HistoryEntry() {
        override val id get() = record.id
        override val vehicleId get() = record.vehicleId
        override val day get() = record.day
        override val km get() = record.odometer
        override val createdAt get() = record.createdAt
    }
}

enum class HistoryKind(val label: String) { ALL("Semua"), VISIT("Servis"), EXPENSE("Pengeluaran"), INSPECTION("Inspeksi") }

data class HistoryFilter(
    val vehicleId: String? = null,
    val query: String = "",
    val period: Period = Period.ALL,
    val kind: HistoryKind = HistoryKind.ALL,
    val visitType: id.abati.autocare.data.VisitType? = null,
    val workshop: String? = null,
)

/**
 * Timeline riwayat terfilter. Pencarian mencakup nama pekerjaan, merek/nomor komponen,
 * bengkel, catatan, nomor invoice, tanggal (yyyy-mm-dd) dan kilometer.
 */
fun AppData.history(filter: HistoryFilter): List<HistoryEntry> {
    val q = filter.query.trim().lowercase()
    // "48.000", "48000 km" → cari berdasarkan kilometer
    val kmQuery = q.replace(".", "").replace("km", "").trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    fun matchesKm(km: Long?) = kmQuery != null && km != null && km.toString().contains(kmQuery)
    fun matchesText(vararg parts: String?) = q.isEmpty() || parts.any { it != null && it.lowercase().contains(q) }

    val entries = buildList<HistoryEntry> {
        if (filter.kind == HistoryKind.ALL || filter.kind == HistoryKind.VISIT) {
            visits.forEach { v ->
                val ts = tasksByVisit[v.id] ?: emptyList()
                add(HistoryEntry.Visit(v, ts, visitCost(v).total))
            }
        }
        if (filter.kind == HistoryKind.ALL || filter.kind == HistoryKind.EXPENSE) expenses.forEach { add(HistoryEntry.Spend(it)) }
        if (filter.kind == HistoryKind.ALL || filter.kind == HistoryKind.INSPECTION) {
            inspections.forEach { r ->
                val res = resultsByInspection[r.id] ?: emptyList()
                add(
                    HistoryEntry.Inspection(
                        r,
                        res.count { it.status == id.abati.autocare.data.CheckStatus.PROBLEM },
                        res.count { it.status == id.abati.autocare.data.CheckStatus.ATTENTION },
                    ),
                )
            }
        }
    }

    return entries.filter { e ->
        (filter.vehicleId == null || e.vehicleId == filter.vehicleId) &&
            vehicleById[e.vehicleId] != null &&
            e.day in filter.period &&
            when (e) {
                is HistoryEntry.Visit ->
                    (filter.visitType == null || e.visit.type == filter.visitType) &&
                        (filter.workshop == null || e.visit.workshopName.equals(filter.workshop, ignoreCase = true))
                else -> filter.visitType == null && filter.workshop == null
            } &&
            (
                q.isEmpty() || matchesKm(e.km) || LocalDate.ofEpochDay(e.day).toString().contains(q) ||
                    when (e) {
                        is HistoryEntry.Visit -> matchesText(
                            e.visit.workshopName, e.visit.mechanic, e.visit.invoiceNumber, e.visit.notes,
                            e.visit.complaint, e.visit.diagnosis, e.visit.recommendation, e.visit.type.label,
                        ) || e.tasks.any { t ->
                            matchesText(t.name, t.brand, t.partNumber, t.notes, t.componentId?.let { componentById[it]?.name })
                        }
                        is HistoryEntry.Spend -> matchesText(e.expense.description, e.expense.category.label, e.expense.paymentMethod)
                        is HistoryEntry.Inspection -> matchesText(e.record.notes, "inspeksi") ||
                            (resultsByInspection[e.record.id] ?: emptyList()).any { matchesText(it.name, it.note) }
                    }
                )
    }.sortedWith(compareByDescending<HistoryEntry> { it.day }.thenByDescending { it.createdAt })
}

/** Nama bengkel unik dari riwayat (untuk filter & saran input). */
fun AppData.workshopNames(): List<String> =
    (workshops.map { it.name } + visits.map { it.workshopName })
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
