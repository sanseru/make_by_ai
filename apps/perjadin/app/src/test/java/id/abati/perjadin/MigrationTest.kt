package id.abati.perjadin

import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.DATA_VERSION
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.UnsupportedDataVersionException
import id.abati.perjadin.data.parseAppData
import id.abati.perjadin.data.toJson
import id.abati.perjadin.data.validateAppData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Data dari versi lama harus tetap terbaca tanpa kehilangan informasi. */
class MigrationTest {

    // Format v1.0.1: tanpa "version", kategori berupa nama enum, tanpa field baru
    private val v1 = """
        {"profile":{"name":"Budi","department":"Ops"},"onboardingDone":true,
         "trips":[{"id":"t1","odrNumber":"ODR/1","purpose":"Audit","destination":"Medan",
           "startDay":20000,"endDay":20003,"advance":5000000,"status":"DONE",
           "budgets":[{"id":"b1","category":"HOTEL","description":"Hotel","amount":1500000}],
           "expenses":[{"id":"e1","day":20000,"category":"MEAL","description":"Makan","amount":45000,
              "paymentMethod":"Tunai","receiptNo":"S-1","note":""}]}]}
    """.trimIndent()

    @Test fun v1DataIsReadCompletely() {
        val d = parseAppData(v1)
        assertEquals("Budi", d.profile.name)
        val t = d.trips.single()
        assertEquals("ODR/1", t.odrNumber)
        assertEquals("HOTEL", t.budgets.single().categoryId)
        assertEquals(BudgetBasis.FIXED, t.budgets.single().basis) // anggaran lama = nominal tetap
        assertEquals(1_500_000, t.budgets.single().amount)
        assertEquals(45_000, t.expenses.single().amount)
        assertEquals("", t.expenses.single().photo)
        assertEquals(-1, t.submittedDay)
        assertEquals(Category.BUILT_INS, d.categories) // tanpa kategori tersimpan = bawaan
        assertTrue(validateAppData(d).isEmpty())
    }

    @Test fun v2CustomCategoriesAreMergedWithBuiltIns() {
        val v2 = """{"version":2,"trips":[],"categories":[{"id":"c1","label":"Tol","color":4280391411,"icon":"car"}]}"""
        val d = parseAppData(v2)
        assertEquals(Category.BUILT_INS.size + 1, d.categories.size)
        assertEquals("Tol", d.categories.last().label)
    }

    @Test fun currentFormatRoundTripsWithoutLoss() {
        val trip = Trip(
            odrNumber = "ODR/9", startDay = 1, endDay = 3, advance = 10, submittedDay = 4, settledDay = 5,
            settledAmount = 7, settlementNote = "payroll",
            expenses = listOf(Expense(day = 1, amount = 3, photo = "a.jpg")),
        )
        val d = AppData(trips = listOf(trip), reminderEnabled = true, reminderHour = 7, lastBackupAt = 99)
        val back = parseAppData(d.toJson().toString())
        assertEquals(DATA_VERSION, d.toJson().getInt("version"))
        assertEquals(d.trips.single().copy(updatedAt = 0, createdAt = 0), back.trips.single().copy(updatedAt = 0, createdAt = 0))
        assertEquals(d.reminderHour, back.reminderHour)
        assertEquals(d.lastBackupAt, back.lastBackupAt)
    }

    @Test fun newerVersionIsRejected() {
        try {
            parseAppData("""{"version":${DATA_VERSION + 1},"trips":[]}""")
            fail("harus ditolak")
        } catch (e: UnsupportedDataVersionException) {
            assertEquals(DATA_VERSION + 1, e.found)
        }
    }

    @Test fun validationCatchesBadData() {
        val bad = AppData(
            trips = listOf(
                Trip(id = "x", odrNumber = "A", startDay = 5, endDay = 1, expenses = listOf(Expense(day = 1, amount = -5, photo = "../../etc/passwd"))),
                Trip(id = "x", odrNumber = "B", startDay = 1, endDay = 1),
            ),
        )
        val issues = validateAppData(bad)
        assertTrue(issues.any { "ganda" in it })
        assertTrue(issues.any { "tanggal pulang" in it })
        assertTrue(issues.any { "negatif" in it })
        assertTrue(issues.any { "tidak valid" in it })
    }
}
