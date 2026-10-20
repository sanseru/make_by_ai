package id.abati.perjadin.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Format angka & tanggal gaya Indonesia (Rp1.250.000, 12 Okt 2026). */
object Fmt {
    val locale: Locale = Locale.forLanguageTag("id-ID")
    const val DAY_MS = 86_400_000L

    private val dShort = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val dLong = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val dDayMonth = DateTimeFormatter.ofPattern("d MMM", locale)

    // NumberFormat tidak thread-safe, jadi dibuat per pemanggilan.
    fun number(v: Long): String = NumberFormat.getNumberInstance(locale).format(v)

    fun rupiah(v: Long): String = if (v < 0) "-Rp" + number(-v) else "Rp" + number(v)

    fun date(day: Long): String = LocalDate.ofEpochDay(day).format(dShort)
    fun dateLong(day: Long): String = LocalDate.ofEpochDay(day).format(dLong)

    fun range(start: Long, end: Long): String {
        if (start == end) return date(start)
        val s = LocalDate.ofEpochDay(start)
        val e = LocalDate.ofEpochDay(end)
        return if (s.year == e.year) "${s.format(dDayMonth)} – ${e.format(dShort)}" else "${date(start)} – ${date(end)}"
    }

    fun percent(part: Long, whole: Long): String =
        if (whole <= 0) "–" else "${(part * 1000 / whole).let { "${it / 10},${it % 10}" }}%"

    /** Mengambil digit saja dari input pengguna, maksimal 13 digit (± 9 triliun). */
    fun parseDigits(s: String): Long = s.filter(Char::isDigit).take(13).toLongOrNull() ?: 0L

    fun today(): Long = LocalDate.now().toEpochDay()
}
