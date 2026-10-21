package id.abati.autocare

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.abati.autocare.data.AppDatabase
import id.abati.autocare.data.Component
import id.abati.autocare.data.ComponentInstallation
import id.abati.autocare.data.Reminder
import id.abati.autocare.data.Repository
import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import id.abati.autocare.data.TaskAction
import id.abati.autocare.data.Templates
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.reminderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Uji database Room & aturan repository di perangkat/emulator nyata. */
@RunWith(AndroidJUnit4::class)
class RepositoryDeviceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    // Scope untuk StateFlow repository; ditunggu selesai sebelum database ditutup
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.Default)
    private val deleted = mutableListOf<String>()

    private fun day(s: String) = LocalDate.parse(s).toEpochDay()

    private val car = Vehicle(id = "car", brand = "Toyota", model = "Avanza", initialOdometer = 30_000, purchaseDay = LocalDate.parse("2024-01-01").toEpochDay())

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true)
        repo = Repository(db, scope) { deleted += it }
        repo.seedIfEmpty()
        repo.saveVehicle(car, isNew = true)
    }

    @After
    fun tearDown() = runBlocking {
        scopeJob.cancelAndJoin() // hentikan observasi flow Room dulu agar tidak query ke DB yang sudah ditutup
        db.close()
    }

    private fun visit(id: String, date: String, km: Long) = ServiceVisit(id = id, vehicleId = "car", day = day(date), odometer = km)

    @Test
    fun katalogBawaanTerisiDanOdometerAwalTercatat() = runBlocking {
        val data = repo.snapshot()
        assertEquals(Templates.items.size, data.items.size)
        assertEquals(30_000L, data.currentOdometer("car")?.km)
    }

    @Test
    fun penggantianKomponenMenyimpanRiwayatLamaDanHapusServisMemulihkan() = runBlocking {
        val battery = Component(id = "aki", vehicleId = "car", name = "Aki")
        repo.saveComponent(battery)
        repo.installManually(ComponentInstallation(id = "lama", componentId = "aki", vehicleId = "car", installedDay = day("2025-05-10"), installedKm = 35_000, brand = "GS"))

        val v = visit("v1", "2026-10-21", 48_000)
        val t = ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Ganti aki", action = TaskAction.REPLACED, componentId = "aki", brand = "Amaron", unitPrice = 1_200_000)
        repo.saveVisit(v, listOf(t), mapOf("t1" to Repository.ComponentPlan("Aki", "Kelistrikan", null, 24)))

        var inst = repo.snapshot().installations.associateBy { it.id }
        val old = inst.getValue("lama")
        assertTrue("episode lama ditutup, bukan dihapus", old.removed)
        assertEquals(day("2025-05-10"), old.installedDay)
        assertEquals(48_000L, old.removedKm)
        val new = inst.values.single { !it.removed }
        assertEquals("Amaron", new.brand)
        assertEquals(24, new.expectedLifeMonths)

        // Edit kunjungan (ubah merek) tidak boleh menghilangkan umur pakai yang sudah diatur
        repo.saveVisit(v, listOf(t.copy(brand = "Bosch")), emptyMap())
        inst = repo.snapshot().installations.associateBy { it.id }
        assertEquals("Bosch", inst.values.single { !it.removed }.brand)
        assertEquals(24, inst.values.single { !it.removed }.expectedLifeMonths)

        repo.deleteVisit(v)
        inst = repo.snapshot().installations.associateBy { it.id }
        assertEquals(1, inst.size)
        assertFalse("episode lama aktif kembali", inst.getValue("lama").removed)
    }

    @Test
    fun pemeriksaanTidakDicatatSebagaiPenggantian() = runBlocking {
        repo.saveComponent(Component(id = "rem", vehicleId = "car", name = "Kampas rem"))
        val t = ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Cek rem", action = TaskAction.INSPECTED, componentId = "rem")
        repo.saveVisit(visit("v1", "2026-10-21", 48_000), listOf(t), emptyMap())
        assertTrue(repo.snapshot().installations.isEmpty())
    }

    @Test
    fun reminderDihitungUlangSetelahServisDanDraftDiabaikan() = runBlocking {
        val r = Reminder(id = "oli", vehicleId = "car", title = "Ganti oli", intervalKm = 5_000, baseDay = day("2026-01-01"), baseKm = 43_000)
        repo.saveReminder(r)
        repo.snooze(r, null, 49_000)
        val today = day("2026-10-21")
        assertEquals(49_000L, repo.snapshot().let { it.reminderInfo(it.reminders.single(), today).dueKm })

        val t = ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Oli", reminderId = "oli")
        repo.saveVisit(visit("v1", "2026-10-21", 48_000).copy(status = VisitStatus.DRAFT), listOf(t), emptyMap())
        assertEquals(49_000L, repo.snapshot().let { it.reminderInfo(it.reminders.single(), today).dueKm })

        repo.saveVisit(visit("v1", "2026-10-21", 48_000), listOf(t), emptyMap())
        val after = repo.snapshot()
        assertNull("penundaan batal setelah servis", after.reminders.single().snoozeKm)
        assertEquals(53_000L, after.reminderInfo(after.reminders.single(), today).dueKm)
    }

    @Test
    fun restoreGabungTidakMenggandakanDanGantiMenimpa() = runBlocking {
        repo.saveVisit(visit("v1", "2026-10-21", 48_000), listOf(ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Oli", unitPrice = 450_000)), emptyMap())
        val backup = repo.snapshot()

        repo.restore(backup, Repository.RestoreMode.MERGE)
        repo.restore(backup, Repository.RestoreMode.MERGE)
        var data = repo.snapshot()
        assertEquals(1, data.vehicles.size)
        assertEquals(1, data.visits.size)
        assertEquals(1, data.tasks.size)
        assertEquals(backup.items.size, data.items.size)

        repo.saveVehicle(Vehicle(id = "baru", brand = "Honda", model = "Jazz"), isNew = true)
        repo.restore(backup, Repository.RestoreMode.REPLACE)
        data = repo.snapshot()
        assertEquals(listOf("car"), data.vehicles.map { it.id })
        assertEquals(backup.odometerLogs.size, data.odometerLogs.size)
    }

    @Test
    fun hapusKendaraanMenghapusSemuaCatatanTerkait() = runBlocking {
        repo.saveVisit(visit("v1", "2026-10-21", 48_000), listOf(ServiceTask(id = "t1", visitId = "v1", vehicleId = "car", name = "Oli")), emptyMap())
        repo.saveReminder(Reminder(vehicleId = "car", title = "Oli", intervalKm = 5_000))
        repo.deleteVehicle(repo.snapshot().vehicles.single())
        val data = repo.snapshot()
        assertTrue(data.vehicles.isEmpty())
        assertTrue(data.visits.isEmpty() && data.tasks.isEmpty() && data.reminders.isEmpty() && data.odometerLogs.isEmpty())
        assertFalse("katalog tetap ada", data.items.isEmpty())
    }
}
