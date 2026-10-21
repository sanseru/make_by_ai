package id.abati.autocare

import id.abati.autocare.domain.Completion
import id.abati.autocare.domain.DueRule
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.ReminderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReminderEngineTest {
    private fun day(s: String) = LocalDate.parse(s).toEpochDay()
    private val today = day("2026-10-21")

    private val oil = DueRule(recurring = true, intervalKm = 5_000, intervalMonths = 6, notifyBeforeKm = 500, notifyBeforeDays = 14)

    @Test
    fun `contoh PRD - 43000 km, sekarang 47500 km, interval 5000 → segera, sisa 500 km`() {
        val info = ReminderEngine.evaluate(
            DueRule(recurring = true, intervalKm = 5_000, baseDay = day("2026-08-01"), baseKm = 43_000, notifyBeforeKm = 500),
            emptyList(), currentKm = 47_500, today = today,
        )
        assertEquals(48_000L, info.dueKm)
        assertEquals(500L, info.kmLeft)
        assertEquals(DueStatus.DUE_SOON, info.status)
    }

    @Test
    fun `mana yang lebih dahulu - waktu tercapai lebih dulu meski km masih jauh`() {
        val info = ReminderEngine.evaluate(
            oil.copy(baseDay = day("2026-04-01"), baseKm = 40_000), emptyList(), currentKm = 41_000, today = today,
        )
        assertEquals(day("2026-10-01"), info.dueDay)
        assertEquals(DueStatus.OVERDUE, info.status) // lewat 20 hari > toleransi 7 hari
        assertEquals(4_000L, info.kmLeft)
    }

    @Test
    fun `mana yang lebih dahulu - km tercapai lebih dulu`() {
        val info = ReminderEngine.evaluate(
            oil.copy(baseDay = day("2026-09-01"), baseKm = 40_000), emptyList(), currentKm = 45_200, today = today,
        )
        assertEquals(DueStatus.DUE, info.status) // lewat 200 km, masih dalam toleransi 500 km
    }

    @Test
    fun `tahapan status`() {
        assertEquals(DueStatus.UPCOMING, ReminderEngine.classify(1_000, 500, 500))
        assertEquals(DueStatus.DUE_SOON, ReminderEngine.classify(500, 500, 500))
        assertEquals(DueStatus.DUE_SOON, ReminderEngine.classify(1, 500, 500))
        assertEquals(DueStatus.DUE, ReminderEngine.classify(0, 500, 500))
        assertEquals(DueStatus.DUE, ReminderEngine.classify(-499, 500, 500))
        assertEquals(DueStatus.OVERDUE, ReminderEngine.classify(-500, 500, 500))
    }

    @Test
    fun `servis terbaru menggantikan acuan awal`() {
        val rule = oil.copy(baseDay = day("2025-01-01"), baseKm = 30_000)
        val info = ReminderEngine.evaluate(
            rule,
            listOf(Completion(day("2026-05-01"), 43_000), Completion(day("2026-01-01"), 38_000)),
            currentKm = 44_000, today = today,
        )
        assertEquals(48_000L, info.dueKm)
        assertEquals(day("2026-11-01"), info.dueDay)
        assertEquals(DueStatus.DUE_SOON, info.status) // 11 hari lagi ≤ ambang 14 hari
    }

    @Test
    fun `km servis tidak diketahui - jatuh tempo km tidak dikarang`() {
        val info = ReminderEngine.evaluate(
            oil, listOf(Completion(day("2026-09-01"), null)), currentKm = 44_000, today = today,
        )
        assertNull(info.dueKm)
        assertEquals(day("2027-03-01"), info.dueDay)
        assertEquals(DueStatus.UPCOMING, info.status)
    }

    @Test
    fun `pengingat parsial tanpa data terakhir - status data belum lengkap`() {
        val info = ReminderEngine.evaluate(oil, emptyList(), currentKm = 44_000, today = today)
        assertEquals(DueStatus.UNKNOWN, info.status)
        assertNotNull(info.missing)
        assertNull(info.dueDay)
        assertNull(info.dueKm)
    }

    @Test
    fun `odometer belum dicatat - jatuh tempo km ada tapi sisa tidak diketahui`() {
        val info = ReminderEngine.evaluate(
            DueRule(recurring = true, intervalKm = 5_000, baseDay = day("2026-01-01"), baseKm = 10_000), emptyList(), currentKm = null, today = today,
        )
        assertEquals(DueStatus.UNKNOWN, info.status)
        assertEquals(15_000L, info.dueKm)
    }

    @Test
    fun `pengingat sekali tidak menjadi berulang setelah selesai`() {
        val once = DueRule(recurring = false, dueDay = day("2026-10-28"))
        assertEquals(DueStatus.DUE_SOON, ReminderEngine.evaluate(once, emptyList(), null, today).status)
        val done = ReminderEngine.evaluate(once, listOf(Completion(day("2026-10-25"), 47_000)), null, today)
        assertEquals(DueStatus.DONE, done.status)
        assertNull(done.dueDay)
        assertEquals(DueStatus.DONE, ReminderEngine.evaluate(once.copy(completedDay = today), emptyList(), null, today).status)
    }

    @Test
    fun `jadwal pertama eksplisit dipakai sebelum ada servis`() {
        val info = ReminderEngine.evaluate(oil.copy(dueKm = 48_000), emptyList(), currentKm = 47_000, today = today)
        assertEquals(48_000L, info.dueKm)
        assertEquals(DueStatus.UPCOMING, info.status)
    }

    @Test
    fun `penundaan memundurkan jatuh tempo`() {
        val rule = oil.copy(baseDay = day("2026-04-01"), baseKm = 43_000, snoozeDay = day("2026-11-30"), snoozeKm = 49_000)
        val info = ReminderEngine.evaluate(rule, emptyList(), currentKm = 47_000, today = today)
        assertEquals(day("2026-11-30"), info.dueDay)
        assertEquals(49_000L, info.dueKm)
        assertEquals(DueStatus.UPCOMING, info.status)
    }

    @Test
    fun `pengingat dijeda`() {
        val info = ReminderEngine.evaluate(oil.copy(baseDay = day("2020-01-01"), active = false), emptyList(), null, today)
        assertEquals(DueStatus.PAUSED, info.status)
    }

    @Test
    fun `interval bulan menghormati akhir bulan`() {
        assertEquals(day("2027-02-28"), ReminderEngine.addMonths(day("2026-08-31"), 6))
    }

    @Test
    fun `estimasi tanggal dari rata-rata pemakaian`() {
        val info = ReminderEngine.evaluate(
            DueRule(recurring = true, intervalKm = 5_000, baseDay = day("2026-08-01"), baseKm = 43_000),
            emptyList(), currentKm = 47_000, today = today, kmPerDay = 50.0,
        )
        assertEquals(20L, info.kmDaysLeft)
        assertEquals(today + 20, info.estimatedKmDay)
    }

    @Test
    fun `kunci notifikasi berubah saat tahap berubah`() {
        val a = ReminderEngine.evaluate(oil.copy(baseDay = day("2026-08-01"), baseKm = 43_000), emptyList(), 47_600, today)
        val b = ReminderEngine.evaluate(oil.copy(baseDay = day("2026-08-01"), baseKm = 43_000), emptyList(), 48_100, today)
        assert(ReminderEngine.notifyKey(a) != ReminderEngine.notifyKey(b))
    }
}
