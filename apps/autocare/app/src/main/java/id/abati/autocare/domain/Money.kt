package id.abati.autocare.domain

import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Perhitungan biaya dengan integer rupiah. Pembulatan konsisten: HALF_UP ke rupiah penuh,
 * dilakukan per baris pekerjaan (sama seperti nota bengkel), bukan di total.
 */
object Money {
    /** Harga satuan × jumlah (dalam seperseribu), dibulatkan ke rupiah terdekat. */
    fun lineTotal(unitPrice: Long, quantityMilli: Long): Long =
        BigDecimal.valueOf(unitPrice)
            .multiply(BigDecimal.valueOf(quantityMilli))
            .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
            .longValueExact()

    fun partsCost(t: ServiceTask): Long = lineTotal(t.unitPrice, t.quantityMilli)

    fun taskTotal(t: ServiceTask): Long = partsCost(t) + t.laborCost

    data class VisitCost(val parts: Long, val labor: Long, val extraFee: Long, val discount: Long) {
        val subtotal: Long get() = parts + labor + extraFee
        val total: Long get() = subtotal - discount
    }

    fun visitCost(v: ServiceVisit, tasks: List<ServiceTask>): VisitCost = VisitCost(
        parts = tasks.sumOf(::partsCost),
        labor = tasks.sumOf { it.laborCost },
        extraFee = v.extraFee,
        discount = v.discount,
    )

    /** "4,5" / "4.5" / "2" → seperseribu (4500 / 2000). Null jika tidak valid atau ≤ 0. */
    fun parseQuantity(input: String): Long? {
        val s = input.trim().replace(',', '.')
        if (s.isEmpty()) return null
        val bd = s.toBigDecimalOrNull() ?: return null
        if (bd.signum() <= 0 || bd > BigDecimal.valueOf(1_000_000)) return null
        return bd.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    /** 4500 → "4,5"; 2000 → "2". */
    fun formatQuantity(milli: Long): String {
        val bd = BigDecimal.valueOf(milli).divide(BigDecimal.valueOf(1000)).stripTrailingZeros()
        return bd.toPlainString().replace('.', ',')
    }
}
