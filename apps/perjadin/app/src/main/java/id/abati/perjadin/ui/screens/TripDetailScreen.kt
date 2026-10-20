package id.abati.perjadin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.find
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Repository
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.ui.components.BudgetBar
import id.abati.perjadin.ui.components.CategoryBadge
import id.abati.perjadin.ui.components.ConfirmDialog
import id.abati.perjadin.ui.components.EmptyState
import id.abati.perjadin.ui.components.InfoRow
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.ui.components.TipCard
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.ui.theme.Success
import id.abati.perjadin.util.Fmt

private val tabs = listOf("Ringkasan", "Anggaran", "Pengeluaran")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: Trip,
    categories: List<Category>,
    repo: Repository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onReport: () -> Unit,
    onDeleted: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // null = dialog tertutup; BudgetItem tanpa id lama = tambah baru
    var budgetDialog by remember { mutableStateOf<BudgetItem?>(null) }
    var budgetDialogNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(trip.odrNumber, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(trip.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = {
                    IconButton(onClick = onReport) { Icon(Icons.Filled.PictureAsPdf, "Laporan PDF") }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Ubah") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu lain") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Hapus perjalanan", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            when (tab) {
                1 -> ExtendedFloatingActionButton(
                    onClick = { budgetDialogNew = true; budgetDialog = BudgetItem() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Tambah Anggaran") },
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Catat Pengeluaran") },
                )
                else -> ExtendedFloatingActionButton(
                    onClick = onReport,
                    icon = { Icon(Icons.Filled.PictureAsPdf, null) },
                    text = { Text("Buat Laporan PDF") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SummaryHeader(trip)
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, title ->
                    val count = when (i) {
                        1 -> " (${trip.budgets.size})"
                        2 -> " (${trip.expenses.size})"
                        else -> ""
                    }
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title + count, maxLines = 1) })
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> overviewTab(trip, categories, onStatus = { repo.setStatus(trip.id, it) })
                    1 -> budgetTab(trip, categories, onClick = { budgetDialogNew = false; budgetDialog = it }, onAdd = { budgetDialogNew = true; budgetDialog = BudgetItem() })
                    else -> expenseTab(trip, categories, onClick = onEditExpense, onAdd = onAddExpense)
                }
            }
        }
    }

    budgetDialog?.let { item ->
        BudgetDialog(
            item = if (budgetDialogNew) null else item,
            categories = categories,
            durationDays = trip.durationDays,
            onCreateCategory = repo::upsertCategory,
            onDismiss = { budgetDialog = null },
            onSave = { repo.upsertBudget(trip.id, it) },
            onDelete = { repo.deleteBudget(trip.id, it) },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus perjalanan?",
            message = "Perjalanan ${trip.odrNumber} beserta seluruh anggaran & pengeluarannya akan dihapus permanen dari perangkat ini.",
            confirmText = "Hapus",
            onConfirm = {
                onDeleted()
                repo.deleteTrip(trip.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SummaryHeader(trip: Trip) {
    val over = trip.remaining < 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                HeaderStat("Anggaran", Fmt.rupiah(trip.totalBudget), Modifier.weight(1f))
                HeaderStat("Realisasi", Fmt.rupiah(trip.totalActual), Modifier.weight(1f))
                HeaderStat(
                    if (over) "Melebihi" else "Sisa",
                    Fmt.rupiah(kotlin.math.abs(trip.remaining)),
                    Modifier.weight(1f),
                    color = if (over) Danger else Success,
                )
            }
            Spacer(Modifier.height(12.dp))
            BudgetBar(trip.totalActual, trip.totalBudget, height = 10)
            Spacer(Modifier.height(6.dp))
            Text(
                if (trip.totalBudget > 0) "Terpakai ${Fmt.percent(trip.totalActual, trip.totalBudget)} dari anggaran" else "Belum ada anggaran — isi di tab Anggaran",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WhiteCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

// ---------------- Tab Ringkasan ----------------

private fun LazyListScope.overviewTab(trip: Trip, categories: List<Category>, onStatus: (TripStatus) -> Unit) {
    item {
        WhiteCard {
            SectionTitle("Status")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStatus.entries.forEach { s ->
                    FilterChip(selected = trip.status == s, onClick = { onStatus(s) }, label = { Text(s.label) })
                }
            }
        }
    }
    item {
        WhiteCard {
            SectionTitle("Anggaran vs Realisasi per Kategori")
            if (trip.activeCategories(categories).isEmpty()) {
                Text("Belum ada data. Tambahkan anggaran & pengeluaran terlebih dulu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trip.activeCategories(categories).forEach { c ->
                val b = trip.budgetFor(c.id)
                val a = trip.actualFor(c.id)
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, size = 32)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${Fmt.rupiah(a)} / ${Fmt.rupiah(b)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a > b) {
                            Icon(Icons.Filled.Warning, "Melebihi anggaran", tint = Danger, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+${Fmt.rupiah(a - b)}", style = MaterialTheme.typography.labelMedium, color = Danger, fontWeight = FontWeight.Bold)
                        } else {
                            Text(Fmt.percent(a, b), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    BudgetBar(a, b, height = 6)
                }
            }
        }
    }
    if (trip.advance > 0) {
        item {
            WhiteCard {
                SectionTitle("Penyelesaian Uang Muka", subtitle = "Perhitungan untuk settlement dengan bagian keuangan")
                InfoRow("Uang muka diterima", Fmt.rupiah(trip.advance))
                InfoRow("Total realisasi", Fmt.rupiah(trip.totalActual))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val s = trip.settlement
                when {
                    s > 0 -> InfoRow("Dikembalikan ke kantor", Fmt.rupiah(s), valueColor = MaterialTheme.colorScheme.primary)
                    s < 0 -> InfoRow("Kekurangan (reimburse)", Fmt.rupiah(-s), valueColor = Danger)
                    else -> InfoRow("Selisih", "Lunas, tidak ada selisih", valueColor = Success)
                }
            }
        }
    }
    item {
        WhiteCard {
            SectionTitle("Detail Perjalanan")
            InfoRow("Nomor ODR", trip.odrNumber)
            InfoRow("Keperluan", trip.purpose)
            InfoRow("Rute", trip.route)
            InfoRow("Tanggal", Fmt.range(trip.startDay, trip.endDay))
            InfoRow("Durasi", "${trip.durationDays} hari")
            InfoRow("Pelaksana", trip.travelerName)
            InfoRow("NIK", trip.employeeId)
            InfoRow("Departemen", trip.department)
            if (trip.notes.isNotBlank()) InfoRow("Catatan", trip.notes)
        }
    }
}

// ---------------- Tab Anggaran ----------------

private fun LazyListScope.budgetTab(trip: Trip, categories: List<Category>, onClick: (BudgetItem) -> Unit, onAdd: () -> Unit) {
    item {
        TipCard(
            "Masukkan plafon anggaran yang disetujui kantor sesuai dokumen ODR. Ketuk item untuk mengubah atau menghapus. Gunakan \"Hitung otomatis\" untuk tarif per hari/malam.",
            title = "Apa itu anggaran?",
            icon = Icons.Filled.Info,
        )
    }
    if (trip.budgets.isEmpty()) {
        item {
            EmptyState(
                Icons.Filled.AccountBalanceWallet,
                "Belum ada anggaran",
                "Contoh: Tiket pesawat PP Rp2.000.000, Hotel 3 malam Rp1.500.000, Uang harian 4 hari Rp600.000.",
            ) {
                androidx.compose.material3.FilledTonalButton(onClick = onAdd) { Text("Tambah Anggaran Pertama") }
            }
        }
        return
    }
    val order = categories.map { it.id }
    items(trip.budgets.sortedBy { b -> order.indexOf(b.categoryId).let { if (it < 0) Int.MAX_VALUE else it } }, key = { it.id }) { b ->
        val actual = trip.actualFor(b.categoryId)
        val cat = categories.find(b.categoryId)
        LineItem(
            badge = { CategoryBadge(cat) },
            title = b.description,
            subtitle = "${cat.label} · realisasi kategori ${Fmt.rupiah(actual)}",
            amount = Fmt.rupiah(b.amount),
            onClick = { onClick(b) },
        )
    }
    item { TotalRow("Total Anggaran", Fmt.rupiah(trip.totalBudget)) }
}

// ---------------- Tab Pengeluaran ----------------

private fun LazyListScope.expenseTab(trip: Trip, categories: List<Category>, onClick: (String) -> Unit, onAdd: () -> Unit) {
    if (trip.expenses.isEmpty()) {
        item {
            EmptyState(
                Icons.Filled.ReceiptLong,
                "Belum ada pengeluaran",
                "Catat setiap biaya yang Anda bayar, misalnya: Taksi bandara → hotel Rp185.000. Simpan juga nomor struk/invoice sebagai bukti.",
            ) {
                androidx.compose.material3.FilledTonalButton(onClick = onAdd) { Text("Catat Pengeluaran Pertama") }
            }
        }
        return
    }
    trip.expenses.groupBy { it.day }.toSortedMap().forEach { (day, list) ->
        item(key = "h$day") {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dateLong(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Fmt.rupiah(list.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list, key = { it.id }) { e: Expense ->
            val cat = categories.find(e.categoryId)
            LineItem(
                badge = { CategoryBadge(cat) },
                title = e.description,
                subtitle = listOf(cat.label, e.paymentMethod, e.receiptNo).filter { it.isNotBlank() }.joinToString(" · "),
                amount = Fmt.rupiah(e.amount),
                onClick = { onClick(e.id) },
            )
        }
    }
    item { TotalRow("Total Realisasi", Fmt.rupiah(trip.totalActual)) }
}

@Composable
private fun LineItem(badge: @Composable () -> Unit, title: String, subtitle: String, amount: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, tonalElevation = 0.dp, shadowElevation = 0.5.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            badge()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
