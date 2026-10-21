package id.abati.autocare.domain

import id.abati.autocare.data.ComponentInstallation
import id.abati.autocare.data.Reminder
import java.time.LocalDate
import kotlin.math.max

/** Status pengingat/komponen, diurutkan dari yang paling mendesak. */
enum class DueStatus(val label: String, val severity: Int) {
    OVERDUE("Terlambat", 5),
    DUE("Jatuh tempo", 4),
    DUE_SOON("Segera jatuh tempo", 3),
    UNKNOWN("Data belum lengkap", 2),
    UPCOMING("Akan datang", 1),
    PAUSED("Dijeda", 0),
    DONE("Selesai", 0),
    ;

    val needsAttention: Boolean get() = this == OVERDUE || this == DUE || this == DUE_SOON
}

/** Satu kali pekerjaan dikerjakan (tanggal + odometer bila diketahui). */
data class Completion(val day: Long, val km: Long?)

/** Aturan jadwal yang dievaluasi engine (dipetakan dari [Reminder] atau [ComponentInstallation]). */
data class DueRule(
    val recurring: Boolean,
    val intervalKm: Long? = null,
    val intervalMonths: Int? = null,
    val baseDay: Long? = null,
    val baseKm: Long? = null,
    val dueDay: Long? = null,
    val dueKm: Long? = null,
    val notifyBeforeKm: Long = 500,
    val notifyBeforeDays: Int = 14,
    val snoozeDay: Long? = null,
    val snoozeKm: Long? = null,
    val completedDay: Long? = null,
    val active: Boolean = true,
)

data class DueInfo(
    val status: DueStatus,
    val dueDay: Long?,
    val dueKm: Long?,
    /** Sisa hari (negatif = lewat). */
    val daysLeft: Long?,
    /** Sisa kilometer (negatif = lewat). */
    val kmLeft: Long?,
    /** Acuan terakhir dikerjakan (completion terbaru atau acuan awal). */
    val last: Completion?,
    /** Penjelasan jika data belum lengkap (tidak mengarang data). */
    val missing: String? = null,
    /** Perkiraan sisa hari hingga jatuh tempo kilometer, berdasarkan rata-rata pemakaian. */
    val kmDaysLeft: Long? = null,
    /** Perkiraan tanggal tercapai jatuh tempo kilometer. */
    val estimatedKmDay: Long? = null,
) {
    /** Progres 0..1 menuju jatuh tempo (dimensi yang paling dekat), null jika tidak bisa dihitung. */
    fun progress(today: Long, currentKm: Long?): Float? {
        val fractions = buildList {
            if (dueKm != null && currentKm != null && last?.km != null && dueKm > last.km) {
                add((currentKm - last.km).toFloat() / (dueKm - last.km))
            }
            if (dueDay != null && last != null && dueDay > last.day) {
                add((today - last.day).toFloat() / (dueDay - last.day))
            }
        }
        return fractions.maxOrNull()?.coerceIn(0f, 1f)
    }
}

/**
 * Perhitungan jatuh tempo pengingat.
 *
 * Aturan bisnis (PRD §4.3):
 * 1. Interval waktu dan/atau kilometer per pekerjaan.
 * 2. "Mana yang lebih dahulu": status mengikuti dimensi yang paling mendesak.
 * 3. Tahapan: segera jatuh tempo → jatuh tempo → terlambat.
 * 4. Acuan interval diperbarui dari pekerjaan selesai terbaru.
 * 5. Pengingat sekali tidak pernah berulang.
 * 7. Data yang belum diketahui tidak dikarang → status [DueStatus.UNKNOWN].
 */
object ReminderEngine {
    /** Toleransi setelah batas tercapai sebelum dianggap terlambat. */
    const val GRACE_DAYS = 7L
    const val GRACE_KM = 500L

    fun addMonths(day: Long, months: Int): Long = LocalDate.ofEpochDay(day).plusMonths(months.toLong()).toEpochDay()

    fun ruleOf(r: Reminder) = DueRule(
        recurring = r.recurring,
        intervalKm = r.intervalKm,
        intervalMonths = r.intervalMonths,
        baseDay = r.baseDay,
        baseKm = r.baseKm,
        dueDay = r.dueDay,
        dueKm = r.dueKm,
        notifyBeforeKm = r.notifyBeforeKm,
        notifyBeforeDays = r.notifyBeforeDays,
        snoozeDay = r.snoozeDay,
        snoozeKm = r.snoozeKm,
        completedDay = r.completedDay,
        active = r.active,
    )

    /** Komponen terpasang dievaluasi seperti pengingat berulang sejak tanggal/km pemasangan. */
    fun ruleOf(i: ComponentInstallation) = DueRule(
        recurring = true,
        intervalKm = i.expectedLifeKm,
        intervalMonths = i.expectedLifeMonths,
        baseDay = i.installedDay,
        baseKm = i.installedKm,
        dueDay = i.nextDay,
        dueKm = i.nextKm,
        notifyBeforeKm = 1000,
        notifyBeforeDays = 30,
        active = !i.removed,
    )

    /** Completion terbaru: tanggal paling akhir, lalu kilometer tertinggi. */
    fun latest(completions: List<Completion>): Completion? =
        completions.maxWithOrNull(compareBy<Completion>({ it.day }, { it.km ?: -1 }))

    /**
     * @param completions pekerjaan selesai yang terhubung (hanya dari kunjungan berstatus selesai).
     * @param currentKm odometer terkini kendaraan (null jika belum pernah dicatat).
     * @param kmPerDay rata-rata pemakaian untuk estimasi tanggal (opsional).
     */
    fun evaluate(
        rule: DueRule,
        completions: List<Completion>,
        currentKm: Long?,
        today: Long,
        kmPerDay: Double? = null,
    ): DueInfo {
        val latestDone = latest(completions)

        if (!rule.recurring && (rule.completedDay != null || latestDone != null)) {
            val last = latestDone ?: Completion(rule.completedDay!!, null)
            return DueInfo(DueStatus.DONE, null, null, null, null, last)
        }

        var dueDay: Long?
        var dueKm: Long?
        val last: Completion?
        if (!rule.recurring) {
            last = null
            dueDay = rule.dueDay
            dueKm = rule.dueKm
        } else if (latestDone != null) {
            last = latestDone
            dueDay = rule.intervalMonths?.let { addMonths(latestDone.day, it) }
            // Odometer saat dikerjakan tidak diketahui → jatuh tempo km tidak dikarang
            dueKm = if (rule.intervalKm != null && latestDone.km != null) latestDone.km + rule.intervalKm else null
        } else {
            last = if (rule.baseDay != null) Completion(rule.baseDay, rule.baseKm) else null
            dueDay = rule.dueDay ?: if (rule.intervalMonths != null && rule.baseDay != null) addMonths(rule.baseDay, rule.intervalMonths) else null
            dueKm = rule.dueKm ?: if (rule.intervalKm != null && rule.baseKm != null) rule.baseKm + rule.intervalKm else null
        }

        // Penundaan hanya memundurkan dimensi yang memang ada
        if (rule.snoozeDay != null && dueDay != null) dueDay = max(dueDay, rule.snoozeDay)
        if (rule.snoozeKm != null && dueKm != null) dueKm = max(dueKm, rule.snoozeKm)

        val daysLeft = dueDay?.let { it - today }
        val kmLeft = if (dueKm != null && currentKm != null) dueKm - currentKm else null
        val kmDays = if (kmLeft != null && kmPerDay != null && kmPerDay > 0.5) (kmLeft / kmPerDay).toLong() else null
        val estimate = kmDays?.let { today + max(0L, it) }

        if (!rule.active) return DueInfo(DueStatus.PAUSED, dueDay, dueKm, daysLeft, kmLeft, last, kmDaysLeft = kmDays, estimatedKmDay = estimate)

        if (dueDay == null && dueKm == null) {
            val missing = when {
                rule.intervalKm == null && rule.intervalMonths == null && !rule.recurring -> "Tentukan tanggal atau kilometer jatuh tempo"
                rule.intervalKm == null && rule.intervalMonths == null -> "Interval belum diatur"
                rule.intervalMonths == null -> "Kilometer terakhir dikerjakan belum diketahui"
                else -> "Tanggal terakhir dikerjakan belum diketahui"
            }
            return DueInfo(DueStatus.UNKNOWN, null, null, null, null, last, missing)
        }

        val byDay = daysLeft?.let { classify(it, rule.notifyBeforeDays.toLong(), GRACE_DAYS) }
        val byKm = kmLeft?.let { classify(it, rule.notifyBeforeKm, GRACE_KM) }
        val status = listOfNotNull(byDay, byKm).maxByOrNull { it.severity }
            ?: return DueInfo(DueStatus.UNKNOWN, dueDay, dueKm, daysLeft, null, last, "Catat odometer terkini untuk menghitung sisa kilometer")
        return DueInfo(status, dueDay, dueKm, daysLeft, kmLeft, last, kmDaysLeft = kmDays, estimatedKmDay = estimate)
    }

    /** [left] = sisa (hari/km); [warn] = ambang "segera"; [grace] = toleransi sebelum "terlambat". */
    fun classify(left: Long, warn: Long, grace: Long): DueStatus = when {
        left > warn -> DueStatus.UPCOMING
        left > 0 -> DueStatus.DUE_SOON
        left > -grace -> DueStatus.DUE
        else -> DueStatus.OVERDUE
    }

    /** Kunci tahap notifikasi; berubah jika status atau jatuh temponya berubah. */
    fun notifyKey(info: DueInfo): String = "${info.status.name}@${info.dueDay ?: "-"}/${info.dueKm ?: "-"}"
}
