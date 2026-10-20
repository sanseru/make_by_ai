package id.abati.perjadin

import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.parseAppData
import id.abati.perjadin.data.toJson
import id.abati.perjadin.data.AppData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uang harian/makan/saku lumpsum: dibayar penuh per hari aktual, tidak perlu bukti,
 * tidak dikembalikan kecuali hari aktual lebih sedikit; perpanjangan ikut dibayar.
 */
class LumpsumTest {
    private val start = 20_000L
    private val perDiem = BudgetItem(categoryId = "PER_DIEM", basis = BudgetBasis.PER_DAY, rate = 150_000, lumpsum = true)
    private val ticket = BudgetItem(categoryId = "TRANSPORT", amount = 2_000_000)

    /** Rencana 4 hari, uang muka 5 jt, tiket riil 1,8 jt. */
    private fun trip(actualDays: Long? = null, extra: List<Expense> = emptyList()): Trip {
        val t = Trip(
            odrNumber = "ODR", startDay = start, endDay = start + 3, advance = 5_000_000,
            budgets = listOf(perDiem, ticket),
            expenses = listOf(Expense(day = start, categoryId = "TRANSPORT", amount = 1_800_000)) + extra,
        ).withRecalculatedBudgets()
        return if (actualDays == null) t else t.copy(actualStartDay = start, actualEndDay = start + actualDays - 1)
    }

    @Test fun fullTripPaysFullLumpsumRegardlessOfSpending() {
        // Makan hanya habis 300 rb, tetap dapat hak penuh 600 rb (tidak dikembalikan)
        val t = trip(extra = listOf(Expense(day = start, categoryId = "PER_DIEM", amount = 300_000)))
        assertEquals(600_000, t.budgetFor("PER_DIEM"))
        assertEquals(600_000, t.actualFor("PER_DIEM"))
        assertEquals(1_800_000 + 600_000, t.totalActual)
        // Sisa uang muka hanya dari tiket: 5 jt − (1,8 jt + 0,6 jt)
        assertEquals(-2_600_000, t.amountDueToEmployee)
    }

    @Test fun shorterTripReturnsOnlyMissingDays() {
        val t = trip(actualDays = 3)
        assertEquals(600_000, t.budgetFor("PER_DIEM"))      // plafon tetap dari rencana ODR
        assertEquals(450_000, t.actualFor("PER_DIEM"))      // hak 3 hari
        assertEquals(150_000, t.budgetFor("PER_DIEM") - t.actualFor("PER_DIEM"))
    }

    @Test fun extendedTripPaysExtraDays() {
        val t = trip(actualDays = 5)
        assertEquals(750_000, t.actualFor("PER_DIEM"))
        assertEquals(600_000, t.budgetFor("PER_DIEM"))
    }

    @Test fun perNightLumpsumFollowsActualNights() {
        val lodging = BudgetItem(categoryId = "HOTEL", basis = BudgetBasis.PER_NIGHT, rate = 400_000, lumpsum = true)
        val t = Trip(odrNumber = "X", startDay = start, endDay = start + 3, budgets = listOf(lodging)).withRecalculatedBudgets()
        assertEquals(1_200_000, t.actualFor("HOTEL"))
        assertEquals(800_000, t.copy(actualStartDay = start, actualEndDay = start + 2).actualFor("HOTEL"))
    }

    @Test fun expensesInLumpsumCategoryAreNotDoubleCounted() {
        val t = trip(extra = listOf(Expense(day = start, categoryId = "PER_DIEM", amount = 900_000)))
        assertEquals(1, t.countedExpenses.size)
        assertEquals(1_800_000, t.totalCountedExpenses)
        assertEquals(600_000, t.totalLumpsum)
    }

    @Test fun nonLumpsumPerDayStillUsesReceipts() {
        val meal = BudgetItem(categoryId = "MEAL", basis = BudgetBasis.PER_DAY, rate = 100_000)
        val t = Trip(
            odrNumber = "X", startDay = start, endDay = start + 3, budgets = listOf(meal),
            expenses = listOf(Expense(day = start, categoryId = "MEAL", amount = 120_000)),
        ).withRecalculatedBudgets()
        assertFalse(t.isLumpsumCategory("MEAL"))
        assertEquals(120_000, t.actualFor("MEAL"))
        assertEquals(400_000 - 120_000, t.remaining)
    }

    @Test fun fixedLumpsumIsPaidAsIs() {
        val pocket = BudgetItem(categoryId = "OTHER", amount = 250_000, lumpsum = true)
        val t = Trip(odrNumber = "X", startDay = start, endDay = start + 1, budgets = listOf(pocket))
        assertEquals(250_000, t.copy(actualStartDay = start, actualEndDay = start).actualFor("OTHER"))
    }

    @Test fun lumpsumAndActualDatesSurviveJsonRoundTrip() {
        val t = trip(actualDays = 3)
        val back = parseAppData(AppData(trips = listOf(t)).toJson().toString()).trips.single()
        assertTrue(back.budgets.first { it.categoryId == "PER_DIEM" }.lumpsum)
        assertEquals(t.actualEndDay, back.actualEndDay)
        assertEquals(t.totalActual, back.totalActual)
    }

    @Test fun duplicateResetsActualDates() {
        val copy = trip(actualDays = 3).duplicate("ODR-2", start + 10, start + 14)
        assertFalse(copy.hasActualDates)
        assertEquals(750_000, copy.actualFor("PER_DIEM"))
    }
}
