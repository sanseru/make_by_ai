package id.abati.autocare

import id.abati.autocare.data.AppData
import id.abati.autocare.data.Component
import id.abati.autocare.data.ComponentInstallation
import id.abati.autocare.data.Expense
import id.abati.autocare.data.ExpenseCategory
import id.abati.autocare.data.OdometerLog
import id.abati.autocare.data.OdometerSource
import id.abati.autocare.data.Reminder
import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import id.abati.autocare.data.TaskAction
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.domain.CostGroup
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.HistoryFilter
import id.abati.autocare.domain.Money
import id.abati.autocare.domain.Period
import id.abati.autocare.domain.componentState
import id.abati.autocare.domain.costEntries
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.history
import id.abati.autocare.domain.monthlyTotals
import id.abati.autocare.domain.odometerWarning
import id.abati.autocare.domain.reminderInfo
import id.abati.autocare.domain.summarize
import id.abati.autocare.domain.visitCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    private fun day(s: String) = LocalDate.parse(s).toEpochDay()
    private val today = day("2026-10-21")

    private val car = Vehicle(id = "car", brand = "Toyota", model = "Avanza", initialOdometer = 30_000)
    private val other = Vehicle(id = "other", brand = "Honda", model = "Jazz")

    /** Contoh PRD §6: 21 Okt 2026, 48.000 km, total Rp875.000. */
    private val visit = ServiceVisit(id = "v1", vehicleId = "car", day = day("2026-10-21"), odometer = 48_000, workshopName = "Bengkel Maju")
    private val oilReminder = Reminder(id = "r-oil", vehicleId = "car", title = "Ganti oli", intervalKm = 5_000, intervalMonths = 6, baseDay = day("2026-04-01"), baseKm = 43_000)
    private val brakeReminder = Reminder(id = "r-brake", vehicleId = "car", title = "Servis rem", intervalKm = 10_000, baseDay = day("2026-01-01"), baseKm = 40_000)
    private val tasks = listOf(
        ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Ganti oli mesin", unitPrice = 450_000, reminderId = "r-oil"),
        ServiceTask(id = "t2", visitId = "v1", vehicleId = "car", name = "Ganti filter oli", unitPrice = 75_000),
        // Pemeriksaan rem: TIDAK dihubungkan ke reminder rem → reminder rem tidak diperbarui
        ServiceTask(id = "t3", visitId = "v1", vehicleId = "car", name = "Servis rem", action = TaskAction.INSPECTED, laborCost = 250_000),
        ServiceTask(id = "t4", visitId = "v1", vehicleId = "car", name = "Jasa pengerjaan", action = TaskAction.SERVICED, laborCost = 100_000),
    )

    private val data = AppData(
        vehicles = listOf(car, other),
        odometerLogs = listOf(
            OdometerLog(vehicleId = "car", day = day("2026-01-01"), km = 30_000, source = OdometerSource.INITIAL),
            OdometerLog(vehicleId = "car", day = day("2026-09-01"), km = 46_000),
            OdometerLog(vehicleId = "other", day = day("2026-09-01"), km = 99_000),
        ),
        reminders = listOf(oilReminder, brakeReminder),
        visits = listOf(visit),
        tasks = tasks,
        expenses = listOf(
            Expense(id = "e1", vehicleId = "car", day = day("2026-03-10"), category = ExpenseCategory.TAX, amount = 2_500_000, description = "Pajak tahunan"),
            Expense(id = "e2", vehicleId = "other", day = day("2026-10-01"), category = ExpenseCategory.WASH, amount = 50_000),
        ),
    )

    @Test
    fun `total satu kunjungan multi-item akurat`() {
        val c = data.visitCost(visit)
        assertEquals(525_000L, c.parts)
        assertEquals(350_000L, c.labor)
        assertEquals(875_000L, c.total)
    }

    @Test
    fun `pembulatan baris biaya konsisten`() {
        // 4,5 liter × Rp86.667 = Rp390.001,5 → dibulatkan HALF_UP ke Rp390.002
        assertEquals(390_002L, Money.lineTotal(86_667, 4_500))
        assertEquals(1L, Money.lineTotal(1, 500))
        assertEquals(4_500L, Money.parseQuantity("4,5"))
        assertEquals(2_000L, Money.parseQuantity("2"))
        assertNull(Money.parseQuantity("0"))
        assertNull(Money.parseQuantity("abc"))
        assertEquals("4,5", Money.formatQuantity(4_500))
        assertEquals("2", Money.formatQuantity(2_000))
    }

    @Test
    fun `odometer terkini dari servis terbaru`() {
        assertEquals(48_000L, data.currentOdometer("car")?.km)
        assertEquals(99_000L, data.currentOdometer("other")?.km) // tidak tercampur antar kendaraan
    }

    @Test
    fun `peringatan odometer lebih rendah`() {
        assertNotNull(data.odometerWarning("car", today, 47_000))
        assertNull(data.odometerWarning("car", today, 48_500))
        assertNotNull(data.odometerWarning("car", day("2026-02-01"), 47_000)) // lebih tinggi dari catatan setelahnya
    }

    @Test
    fun `servis selesai memperbarui reminder terkait saja`() {
        val oil = data.reminderInfo(oilReminder, today)
        assertEquals(53_000L, oil.dueKm)
        assertEquals(DueStatus.UPCOMING, oil.status)
        // Reminder rem tetap memakai acuan awal 40.000 + 10.000 = 50.000
        assertEquals(50_000L, data.reminderInfo(brakeReminder, today).dueKm)
    }

    @Test
    fun `menghapus kunjungan mengembalikan jadwal reminder`() {
        val without = data.copy(visits = emptyList(), tasks = emptyList())
        val oil = without.reminderInfo(oilReminder, today)
        assertEquals(48_000L, oil.dueKm)
        assertEquals(DueStatus.OVERDUE, oil.status) // 2026-10-01 lewat 20 hari
    }

    @Test
    fun `kunjungan draft tidak menyelesaikan reminder dan tidak dihitung biaya`() {
        val draft = data.copy(visits = listOf(visit.copy(status = VisitStatus.DRAFT)))
        assertEquals(48_000L, draft.reminderInfo(oilReminder, today).dueKm)
        assertEquals(2_500_000L, summarize(draft.costEntries("car", Period.ALL)).total)
    }

    @Test
    fun `pekerjaan yang hanya direncanakan tidak menyelesaikan reminder`() {
        val planned = data.copy(tasks = tasks.map { if (it.id == "t1") it.copy(action = TaskAction.PLANNED) else it })
        assertEquals(48_000L, planned.reminderInfo(oilReminder, today).dueKm)
    }

    @Test
    fun `rekap biaya per kendaraan dan periode`() {
        val year = summarize(data.costEntries("car", Period.year(2026)))
        assertEquals(875_000L + 2_500_000L, year.total)
        assertEquals(525_000L, year.byGroup[CostGroup.PARTS])
        assertEquals(1, year.visitCount)
        val month = summarize(data.costEntries("car", Period.month(today)))
        assertEquals(875_000L, month.total)
        val all = summarize(data.costEntries(null, Period.ALL))
        assertEquals(875_000L + 2_500_000L + 50_000L, all.total)
        val m = monthlyTotals(data.costEntries("car", Period.ALL), 2026)
        assertEquals(2_500_000L, m[2])
        assertEquals(875_000L, m[9])
    }

    @Test
    fun `diskon mengurangi total`() {
        val d = data.copy(visits = listOf(visit.copy(discount = 75_000, extraFee = 10_000)))
        assertEquals(810_000L, summarize(d.costEntries("car", Period.ALL)).total - 2_500_000L)
    }

    @Test
    fun `pencarian riwayat berdasarkan pekerjaan, bengkel, km, dan tanggal`() {
        assertEquals(1, data.history(HistoryFilter(query = "filter oli")).size)
        assertEquals(1, data.history(HistoryFilter(query = "maju")).size)
        assertEquals(1, data.history(HistoryFilter(query = "48.000")).size)
        assertEquals(1, data.history(HistoryFilter(query = "2026-10-21")).size)
        assertEquals(1, data.history(HistoryFilter(query = "pajak")).size)
        assertEquals(0, data.history(HistoryFilter(query = "rem", vehicleId = "other")).size)
        assertEquals(2, data.history(HistoryFilter(vehicleId = "car")).size)
        assertEquals(1, data.history(HistoryFilter(vehicleId = "car", period = Period(day("2026-10-01"), day("2026-10-31")))).size)
    }

    @Test
    fun `status komponen berdasarkan umur pakai`() {
        val battery = Component(id = "c1", vehicleId = "car", name = "Aki")
        val d = data.copy(
            components = listOf(battery),
            installations = listOf(
                ComponentInstallation(id = "i-old", componentId = "c1", vehicleId = "car", installedDay = day("2022-05-10"), removed = true, removedDay = day("2024-11-01")),
                ComponentInstallation(id = "i-new", componentId = "c1", vehicleId = "car", installedDay = day("2024-11-01"), installedKm = 35_000, expectedLifeMonths = 24),
            ),
        )
        val s = d.componentState(battery, today)
        assertEquals("i-new", s.current?.id)
        assertEquals(2, s.history.size) // riwayat lama tetap ada
        assertEquals(DueStatus.DUE_SOON, s.info?.status) // jatuh tempo 2026-11-01
        assertTrue(s.conditionLabel.contains("Segera"))
    }
}
