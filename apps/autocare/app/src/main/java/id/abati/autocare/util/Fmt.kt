package id.abati.autocare.util

import id.abati.autocare.domain.DueInfo
import id.abati.autocare.domain.DueStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Format angka, tanggal & kilometer gaya Indonesia (Rp1.250.000, 48.000 km, 21 Okt 2026). */
object Fmt {
    val locale: Locale = Locale.forLanguageTag("id-ID")

    private val dShort = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val dLong = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val dMonth = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val dMonthShort = DateTimeFormatter.ofPattern("MMM", locale)

    // NumberFormat tidak thread-safe, jadi dibuat per pemanggilan.
    fun number(v: Long): String = NumberFormat.getNumberInstance(locale).format(v)

    fun rupiah(v: Long): String = if (v < 0) "-Rp" + number(-v) else "Rp" + number(v)

    fun km(v: Long?): String = if (v == null) "– km" else "${number(v)} km"

    fun date(day: Long?): String = if (day == null) "–" else LocalDate.ofEpochDay(day).format(dShort)
    fun dateLong(day: Long): String = LocalDate.ofEpochDay(day).format(dLong)
    fun month(day: Long): String = LocalDate.ofEpochDay(day).format(dMonth)
    fun monthShort(month: Int): String = LocalDate.of(2000, month, 1).format(dMonthShort)

    /** Mengambil digit saja dari input pengguna, maksimal 13 digit. Null jika kosong. */
    fun parseDigits(s: String): Long? = s.filter(Char::isDigit).take(13).toLongOrNull()

    /** Input angka ditampilkan dengan pemisah ribuan saat diketik (48000 → 48.000). */
    fun groupDigits(s: String): String = parseDigits(s)?.let(::number) ?: ""

    fun today(): Long = LocalDate.now().toEpochDay()

    fun relativeDays(days: Long): String = when {
        days == 0L -> "hari ini"
        days == 1L -> "besok"
        days == -1L -> "kemarin"
        days > 0 && days < 60 -> "$days hari lagi"
        days > 0 -> "±${days / 30} bulan lagi"
        -days < 60 -> "lewat ${-days} hari"
        else -> "lewat ±${-days / 30} bulan"
    }

    fun relativeKm(km: Long): String = if (km >= 0) "${number(km)} km lagi" else "lewat ${number(abs(km))} km"

    /** Ringkasan jatuh tempo, mis. "48.000 km · 1 Apr 2027" dan sisa "500 km lagi · 5 bulan lagi". */
    fun dueSummary(info: DueInfo): String {
        if (info.status == DueStatus.DONE) return "Selesai ${date(info.last?.day)}"
        if (info.status == DueStatus.UNKNOWN && info.dueDay == null && info.dueKm == null) return info.missing ?: "Data belum lengkap"
        return listOfNotNull(info.dueKm?.let { km(it) }, info.dueDay?.let { date(it) }).joinToString(" atau ")
    }

    fun remainingSummary(info: DueInfo): String {
        if (info.status == DueStatus.DONE) return ""
        val parts = listOfNotNull(info.kmLeft?.let(::relativeKm), info.daysLeft?.let(::relativeDays))
        val est = info.estimatedKmDay?.takeIf { info.daysLeft == null && (info.kmLeft ?: 0) > 0 }?.let { "perkiraan ${date(it)}" }
        return (parts + listOfNotNull(est)).joinToString(" · ").ifEmpty { info.missing ?: "" }
    }
}
