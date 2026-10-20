package id.abati.perjadin.data

import java.time.LocalDate

/** Data contoh agar pengguna baru bisa langsung mencoba semua fitur. */
object DemoData {
    fun trips(profile: Profile): List<Trip> {
        val start = LocalDate.now().minusDays(5).toEpochDay()
        val name = profile.name.ifBlank { "Budi Santoso" }
        return listOf(
            Trip(
                odrNumber = "ODR/2026/10/0123 (CONTOH)",
                purpose = "Audit stok & serah terima gudang cabang Medan",
                origin = "Jakarta",
                destination = "Medan, Sumatera Utara",
                startDay = start,
                endDay = start + 3,
                travelerName = name,
                employeeId = profile.employeeId.ifBlank { "EMP-00421" },
                department = profile.department.ifBlank { "Operasional" },
                advance = 5_000_000,
                status = TripStatus.DONE,
                notes = "Data contoh — silakan hapus setelah mencoba.",
                budgets = listOf(
                    BudgetItem(category = Category.TRANSPORT, description = "Tiket pesawat CGK–KNO PP", amount = 2_000_000),
                    BudgetItem(category = Category.HOTEL, description = "Hotel 3 malam × Rp500.000", amount = 1_500_000),
                    BudgetItem(category = Category.PER_DIEM, description = "Uang harian 4 hari × Rp150.000", amount = 600_000),
                    BudgetItem(category = Category.LOCAL_TRANSPORT, description = "Transport lokal", amount = 500_000),
                    BudgetItem(category = Category.MEAL, description = "Konsumsi rapat", amount = 400_000),
                ),
                expenses = listOf(
                    Expense(day = start, category = Category.TRANSPORT, description = "Tiket Citilink CGK–KNO PP", amount = 1_650_000, paymentMethod = "Kartu Debit", receiptNo = "QG-8812AB"),
                    Expense(day = start, category = Category.LOCAL_TRANSPORT, description = "Taksi bandara Kualanamu → hotel", amount = 185_000, paymentMethod = "E-Wallet (GoPay/OVO/DANA)", receiptNo = "GRB-552301"),
                    Expense(day = start, category = Category.HOTEL, description = "Hotel Santika Medan 3 malam", amount = 1_260_000, paymentMethod = "Kartu Kredit Perusahaan", receiptNo = "INV-SNT-0931"),
                    Expense(day = start + 1, category = Category.MEAL, description = "Makan siang dengan kepala gudang", amount = 245_000, paymentMethod = "Tunai", receiptNo = "Struk 0045"),
                    Expense(day = start, category = Category.PER_DIEM, description = "Uang harian 4 hari × Rp150.000", amount = 600_000, paymentMethod = "Transfer Bank", note = "Sesuai SK uang harian golongan III"),
                    Expense(day = start + 3, category = Category.LOCAL_TRANSPORT, description = "Taksi hotel → bandara", amount = 160_000, paymentMethod = "Tunai", receiptNo = "BB-77120"),
                ),
            ),
        )
    }
}
