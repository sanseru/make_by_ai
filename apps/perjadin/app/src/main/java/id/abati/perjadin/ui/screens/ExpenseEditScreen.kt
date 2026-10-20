package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.PAYMENT_METHODS
import id.abati.perjadin.data.Trip
import id.abati.perjadin.ui.components.CategoryBadge
import id.abati.perjadin.ui.components.ConfirmDialog
import id.abati.perjadin.ui.components.DateField
import id.abati.perjadin.ui.components.DropdownField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.MoneyField
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    trip: Trip,
    expense: Expense?,
    onBack: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (String) -> Unit,
) {
    val isNew = expense == null
    // Default tanggal: hari ini jika dalam rentang perjalanan, selain itu tanggal berangkat
    val defaultDay = Fmt.today().let { if (it in trip.startDay..trip.endDay) it else trip.startDay }
    val defaultCategory = trip.budgets.firstOrNull()?.category ?: Category.MEAL

    var day by rememberSaveable { mutableLongStateOf(expense?.day ?: defaultDay) }
    var category by rememberSaveable { mutableStateOf(expense?.category ?: defaultCategory) }
    var description by rememberSaveable { mutableStateOf(expense?.description ?: "") }
    var amount by rememberSaveable { mutableLongStateOf(expense?.amount ?: 0L) }
    var payment by rememberSaveable { mutableStateOf(expense?.paymentMethod ?: PAYMENT_METHODS.first()) }
    var receipt by rememberSaveable { mutableStateOf(expense?.receiptNo ?: "") }
    var note by rememberSaveable { mutableStateOf(expense?.note ?: "") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val descError = if (description.isBlank()) "Uraian wajib diisi" else null
    val amountError = if (amount <= 0) "Nominal harus lebih dari 0" else null

    // Sisa anggaran kategori setelah pengeluaran ini (tanpa menghitung nilai lama jika sedang diedit)
    val catBudget = trip.budgetFor(category)
    val catActualOthers = trip.expenses.filter { it.category == category && it.id != expense?.id }.sumOf { it.amount }
    val catRemaining = catBudget - catActualOthers - amount
    val outOfRange = day < trip.startDay || day > trip.endDay

    fun save() {
        submitted = true
        if (descError != null || amountError != null) return
        onSave(
            (expense ?: Expense(day = day)).copy(
                day = day,
                category = category,
                description = description.trim(),
                amount = amount,
                paymentMethod = payment,
                receiptNo = receipt.trim(),
                note = note.trim(),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Catat Pengeluaran" else "Ubah Pengeluaran", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = ::save) { Text("Simpan", fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Pengeluaran", subtitle = "${trip.odrNumber} · ${trip.destination}")
            DropdownField(
                label = "Kategori",
                options = Category.entries,
                selected = category,
                optionLabel = { it.label },
                onSelect = { category = it },
                optionIcon = { CategoryBadge(it, size = 28) },
                supporting = if (catBudget > 0) "Anggaran kategori ini: ${Fmt.rupiah(catBudget)}" else "Kategori ini belum punya anggaran",
            )
            FormField(
                value = description, onValueChange = { description = it },
                label = "Uraian *",
                placeholder = category.example,
                supporting = if (submitted && descError != null) descError else "Jelaskan untuk apa biaya ini dikeluarkan",
                isError = submitted && descError != null,
                singleLine = false,
            )
            MoneyField(
                value = amount, onValueChange = { amount = it },
                label = "Nominal Dibayar *",
                placeholder = "Contoh: 185.000",
                supporting = if (submitted && amountError != null) amountError else "Isi sesuai jumlah di struk / invoice",
                isError = submitted && amountError != null,
            )

            if (catBudget > 0 && amount > 0) {
                val over = catRemaining < 0
                Surface(
                    color = if (over) Danger.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (over) Icons.Filled.Warning else Icons.Filled.Info, null, tint = if (over) Danger else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (over) "Melebihi anggaran ${category.label} sebesar ${Fmt.rupiah(-catRemaining)}. Siapkan alasan/persetujuan atasan."
                            else "Sisa anggaran ${category.label} setelah ini: ${Fmt.rupiah(catRemaining)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) Danger else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            DateField(
                day = day, onChange = { day = it }, label = "Tanggal Transaksi",
                supporting = if (outOfRange) "Di luar tanggal perjalanan (${Fmt.range(trip.startDay, trip.endDay)})" else null,
                isError = outOfRange,
            )

            SectionTitle("Pembayaran & Bukti")
            DropdownField(
                label = "Metode Pembayaran",
                options = PAYMENT_METHODS,
                selected = payment,
                optionLabel = { it },
                onSelect = { payment = it },
                leading = Icons.Filled.CreditCard,
            )
            FormField(
                value = receipt, onValueChange = { receipt = it },
                label = "No. Struk / Invoice / Kode Booking",
                placeholder = "Contoh: INV-SNT-0931 atau Struk 0045",
                supporting = "Memudahkan bagian keuangan mencocokkan bukti fisik",
                leading = Icons.Filled.Receipt,
                capitalization = KeyboardCapitalization.Characters,
            )
            FormField(
                value = note, onValueChange = { note = it },
                label = "Keterangan Tambahan (opsional)",
                placeholder = "Contoh: Dibayar bersama 2 rekan, bagian saya saja",
                singleLine = false,
                minLines = 2,
            )

            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (isNew) "Simpan Pengeluaran" else "Simpan Perubahan")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && expense != null) {
        ConfirmDialog(
            title = "Hapus pengeluaran?",
            message = "\"${expense.description}\" senilai ${Fmt.rupiah(expense.amount)} akan dihapus.",
            confirmText = "Hapus",
            onConfirm = { onDelete(expense.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
