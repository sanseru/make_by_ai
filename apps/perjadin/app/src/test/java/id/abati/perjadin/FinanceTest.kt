package id.abati.perjadin

import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aturan perhitungan keuangan yang dipakai di semua layar & PDF. */
class FinanceTest {

    // 2–5 November = 4 hari kalender, 3 malam
    private val start = 20_000L
    private fun trip(days: Long = 4, advance: Long = 0, budgets: List<BudgetItem> = emptyList(), expenses: List<Expense> = emptyList()) =
        Trip(odrNumber = "ODR-1", startDay = start, endDay = start + days - 1, advance = advance, budgets = budgets, expenses = expenses)

    private val perDay = BudgetItem(categoryId = "PER_DIEM", basis = BudgetBasis.PER_DAY, rate = 150_000)
    private val perNight = BudgetItem(categoryId = "HOTEL", basis = BudgetBasis.PER_NIGHT, rate = 500_000)
    private val fixed = BudgetItem(categoryId = "TRANSPORT", amount = 2_000_000)

    @Test fun perDayUsesCalendarDaysAndPerNightUsesNights() {
        val t = trip(days = 4, budgets = listOf(perDay, perNight, fixed)).withRecalculatedBudgets()
        assertEquals(600_000, t.budgetFor("PER_DIEM"))   // 4 hari × 150.000
        assertEquals(1_500_000, t.budgetFor("HOTEL"))    // 3 malam × 500.000
        assertEquals(2_000_000, t.budgetFor("TRANSPORT"))
        assertEquals(4_100_000, t.totalBudget)
    }

    @Test fun oneDayTripHasZeroNights() {
        val t = trip(days = 1, budgets = listOf(perNight, perDay)).withRecalculatedBudgets()
        assertEquals(0, t.budgetFor("HOTEL"))
        assertEquals(150_000, t.budgetFor("PER_DIEM"))
    }

    @Test fun changingDatesRecalculatesOnlyDurationBasedItems() {
        val t = trip(days = 4, budgets = listOf(perDay, perNight, fixed)).withRecalculatedBudgets()
        val longer = t.copy(endDay = t.endDay + 2).withRecalculatedBudgets() // 6 hari, 5 malam
        assertEquals(900_000, longer.budgetFor("PER_DIEM"))
        assertEquals(2_500_000, longer.budgetFor("HOTEL"))
        assertEquals(2_000_000, longer.budgetFor("TRANSPORT"))
    }

    @Test fun perQtyIgnoresDuration() {
        val taxi = BudgetItem(categoryId = "LOCAL_TRANSPORT", basis = BudgetBasis.PER_QTY, rate = 100_000, qty = 2)
        assertEquals(200_000, trip(days = 10, budgets = listOf(taxi)).withRecalculatedBudgets().totalBudget)
    }

    @Test fun duplicateCopiesBudgetsWithNewIdsButNotExpensesOrMoney() {
        val e = Expense(day = start, categoryId = "MEAL", amount = 50_000)
        val src = trip(days = 4, advance = 5_000_000, budgets = listOf(perDay, fixed), expenses = listOf(e))
            .withRecalculatedBudgets().copy(status = TripStatus.REPORTED, submittedDay = start, settledDay = start)
        val copy = src.duplicate("ODR-2", start + 30, start + 34) // 5 hari
        assertEquals("ODR-2", copy.odrNumber)
        assertTrue(copy.expenses.isEmpty())
        assertEquals(0, copy.advance)
        assertEquals(TripStatus.PLANNED, copy.status)
        assertFalse(copy.isSubmitted || copy.isSettled)
        assertEquals(750_000, copy.budgetFor("PER_DIEM"))
        assertTrue(copy.budgets.map { it.id }.none { id -> src.budgets.any { it.id == id } })
        assertTrue(copy.id != src.id)
    }

    @Test fun remainingAndOverBudget() {
        val t = trip(budgets = listOf(fixed), expenses = listOf(Expense(day = start, categoryId = "TRANSPORT", amount = 2_300_000)))
        assertEquals(-300_000, t.remaining)
        assertEquals(2_300_000, t.actualFor("TRANSPORT"))
    }

    @Test fun advanceRefundAndShortfall() {
        val spent = listOf(Expense(day = start, amount = 3_000_000))
        val refund = trip(advance = 5_000_000, expenses = spent)
        assertEquals(-2_000_000, refund.amountDueToEmployee) // pegawai mengembalikan 2 jt
        assertEquals(2_000_000, refund.settlement)

        val shortfall = trip(advance = 1_000_000, expenses = spent)
        assertEquals(2_000_000, shortfall.amountDueToEmployee) // kantor membayar kekurangan 2 jt

        val noAdvance = trip(expenses = spent)
        assertEquals(3_000_000, noAdvance.amountDueToEmployee) // seluruhnya reimburse
    }

    @Test fun reimbursementStatusDoesNotChangeTotals() {
        val t = trip(advance = 1_000_000, expenses = listOf(Expense(day = start, amount = 1_500_000)))
        assertTrue(t.needsSettlement)
        val settled = t.copy(submittedDay = start + 5, settledDay = start + 10, settledAmount = 500_000)
        assertFalse(settled.needsSettlement)
        // Status klaim tidak boleh mengubah realisasi (tidak dihitung dua kali)
        assertEquals(t.totalActual, settled.totalActual)
        assertEquals(t.amountDueToEmployee, settled.amountDueToEmployee)
    }

    @Test fun deletingExpenseUpdatesTotals() {
        val a = Expense(day = start, amount = 100_000)
        val b = Expense(day = start, amount = 250_000)
        val t = trip(expenses = listOf(a, b))
        assertEquals(350_000, t.totalActual)
        assertEquals(100_000, t.copy(expenses = t.expenses.filterNot { it.id == b.id }).totalActual)
    }

    @Test fun evenAmountsNeedNoSettlement() {
        val t = trip(advance = 1_000_000, expenses = listOf(Expense(day = start, amount = 1_000_000)))
        assertFalse(t.needsSettlement)
    }

    @Test fun focusTripSkipsCancelledAndReported() {
        val today = start + 1
        val cancelled = trip().copy(status = TripStatus.CANCELLED)
        val reported = trip().copy(status = TripStatus.REPORTED)
        assertNull(AppData(trips = listOf(cancelled, reported)).focusTrip(today))
        val active = trip()
        assertEquals(active.id, AppData(trips = listOf(cancelled, active)).focusTrip(today)?.id)
    }

    @Test fun orphanCategoryIsReportedNotLost() {
        val t = trip(expenses = listOf(Expense(day = start, categoryId = "DELETED", amount = 10)))
        val cats = t.activeCategories(Category.BUILT_INS)
        assertEquals(1, cats.size)
        assertEquals("DELETED", cats.first().id)
        assertEquals(10, t.actualFor("DELETED"))
    }
}
