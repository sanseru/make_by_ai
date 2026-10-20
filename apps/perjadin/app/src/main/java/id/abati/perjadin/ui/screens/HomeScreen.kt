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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import id.abati.perjadin.data.AppData
import id.abati.perjadin.ui.QuickRequest
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.ui.components.BudgetBar
import id.abati.perjadin.ui.components.EmptyState
import id.abati.perjadin.ui.components.StatusBadge
import id.abati.perjadin.ui.components.usageColor
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.util.Fmt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    data: AppData,
    onOpenTrip: (String) -> Unit,
    onNewTrip: () -> Unit,
    onFaq: () -> Unit,
    onSettings: () -> Unit,
    onTutorial: () -> Unit,
    onLoadDemo: () -> Unit,
    onSaveExpense: (tripId: String, Expense) -> Unit,
    onCreateCategory: (Category) -> Unit,
    quickRequest: QuickRequest? = null,
    onQuickHandled: () -> Unit = {},
) {
    // Alur cepat catat pengeluaran: pilih perjalanan (jika perlu) → sheet pengeluaran
    var quickTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickTrip by rememberSaveable { mutableStateOf(false) }
    val today = Fmt.today()
    val openTrips = data.trips.filter { it.status.isOpen }
    val currentTrips = openTrips.filter { it.status == TripStatus.ONGOING || today in it.startDay..it.endDay }
    val pickerTrips = (currentTrips + openTrips.sortedByDescending { it.startDay } + data.trips.sortedByDescending { it.startDay }).distinctBy { it.id }
    val startQuickExpense: () -> Unit = {
        if (data.trips.isEmpty()) {
            onNewTrip()
        } else if (currentTrips.size == 1) {
            quickTripId = currentTrips.first().id
        } else {
            pickTrip = true
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    val trips = data.trips
        .filter { filter == null || it.status.name == filter }
        .filter { t ->
            query.isBlank() || listOf(t.odrNumber, t.purpose, t.destination, t.origin).any { it.contains(query.trim(), ignoreCase = true) }
        }
        .sortedByDescending { it.startDay }

    // Permintaan dari widget / shortcut
    LaunchedEffect(quickRequest) {
        val req = quickRequest ?: return@LaunchedEffect
        val target = req.tripId?.let { data.trip(it) }
        if (target != null) quickTripId = target.id else startQuickExpense()
        onQuickHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Perjadin", fontWeight = FontWeight.Bold)
                        Text("Catatan Perjalanan Dinas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onFaq) { Icon(Icons.Outlined.HelpOutline, "Bantuan & FAQ") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Pengaturan") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (data.trips.isEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = onNewTrip,
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Perjalanan Baru") },
                    )
                } else {
                    ExtendedFloatingActionButton(
                        onClick = onNewTrip,
                        icon = { Icon(Icons.Filled.Luggage, null) },
                        text = { Text("Perjalanan Baru") },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    ExtendedFloatingActionButton(
                        onClick = startQuickExpense,
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Catat Pengeluaran") },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(data.trips) }

            // Pengingat backup berkala (tidak memblokir pemakaian)
            val backupAge = System.currentTimeMillis() - data.lastBackupAt
            if (data.trips.any { it.expenses.isNotEmpty() } && (data.lastBackupAt == 0L || backupAge > 14L * Fmt.DAY_MS)) {
                item {
                    SettlementBanner(
                        text = if (data.lastBackupAt == 0L) "Data belum pernah di-backup" else "Backup terakhir ${backupAge / Fmt.DAY_MS} hari lalu",
                        detail = "Ketuk untuk ekspor backup lengkap agar data aman saat ganti HP",
                        onClick = onSettings,
                    )
                }
            }

            // Pengingat uang yang belum diselesaikan dengan kantor
            val owed = data.trips.filter { it.needsSettlement && it.amountDueToEmployee > 0 }
            val toReturn = data.trips.filter { it.needsSettlement && it.amountDueToEmployee < 0 }
            if (owed.isNotEmpty()) {
                item {
                    SettlementBanner(
                        text = "Menunggu reimburse dari kantor: ${Fmt.rupiah(owed.sumOf { it.amountDueToEmployee })}",
                        detail = owed.joinToString(", ") { it.odrNumber },
                        onClick = { onOpenTrip(owed.first().id) },
                    )
                }
            }
            if (toReturn.isNotEmpty()) {
                item {
                    SettlementBanner(
                        text = "Sisa uang muka perlu dikembalikan: ${Fmt.rupiah(-toReturn.sumOf { it.amountDueToEmployee })}",
                        detail = toReturn.joinToString(", ") { it.odrNumber },
                        onClick = { onOpenTrip(toReturn.first().id) },
                    )
                }
            }

            if (!data.profile.isComplete) {
                item {
                    Surface(onClick = onSettings, color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonOutline, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Lengkapi profil Anda", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(
                                    "Nama, jabatan & atasan akan otomatis terisi di form dan laporan PDF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (data.trips.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Hapus pencarian") }
                        },
                        placeholder = { Text("Cari No. ODR, kota tujuan, atau keperluan…") },
                        shape = RoundedCornerShape(50),
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Semua (${data.trips.size})") })
                        TripStatus.entries.forEach { s ->
                            val count = data.trips.count { it.status == s }
                            FilterChip(selected = filter == s.name, onClick = { filter = if (filter == s.name) null else s.name }, label = { Text("${s.label} ($count)") })
                        }
                    }
                }
            }

            if (data.trips.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Luggage,
                        title = "Belum ada perjalanan dinas",
                        message = "Mulai dengan menekan tombol \"Perjalanan Baru\" lalu masukkan nomor ODR Anda. Bingung? Coba data contoh atau lihat tutorial singkat.",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onLoadDemo) { Text("Coba dengan Data Contoh") }
                            TextButton(onClick = onTutorial) {
                                Icon(Icons.Filled.PlayCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Lihat Tutorial")
                            }
                        }
                    }
                }
            } else if (trips.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.Search, "Tidak ditemukan", "Tidak ada perjalanan yang cocok dengan pencarian / filter Anda.")
                }
            }

            items(trips, key = { it.id }) { trip -> TripCard(trip, onClick = { onOpenTrip(trip.id) }) }
        }
    }

    if (pickTrip) {
        TripPickerSheet(
            trips = pickerTrips,
            onPick = { t ->
                pickTrip = false
                quickTripId = t.id
            },
            onDismiss = { pickTrip = false },
        )
    }
    quickTripId?.let { id -> data.trip(id) }?.let { trip ->
        ExpenseSheet(
            trip = trip,
            categories = data.categories,
            history = data.expenseHistory,
            expense = null,
            onSave = { onSaveExpense(trip.id, it) },
            onDelete = {},
            onCreateCategory = onCreateCategory,
            onDismiss = { quickTripId = null },
        )
    }
}

@Composable
private fun SettlementBanner(text: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Payments, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun OverviewCard(trips: List<Trip>) {
    // Ringkasan hanya untuk perjalanan yang belum dilaporkan (masih berjalan)
    val active = trips.filter { it.status.isOpen }
    val budget = active.sumOf { it.totalBudget }
    val actual = active.sumOf { it.totalActual }
    val cs = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(cs.primary, cs.secondary)))
                .padding(20.dp),
        ) {
            Text("Anggaran perjalanan aktif", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Text(Fmt.rupiah(budget), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row {
                OverviewStat("Realisasi", Fmt.rupiah(actual), Modifier.weight(1f))
                OverviewStat(if (budget - actual >= 0) "Sisa" else "Melebihi", Fmt.rupiah(kotlin.math.abs(budget - actual)), Modifier.weight(1f))
                OverviewStat("Perjalanan", "${active.size} aktif", Modifier.weight(0.8f))
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            trip.odrNumber,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(trip.status)
            }
            Spacer(Modifier.height(10.dp))
            Text(trip.purpose, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            MetaLine(Icons.Filled.Place, trip.route.ifBlank { trip.destination })
            MetaLine(Icons.Filled.CalendarMonth, "${Fmt.range(trip.startDay, trip.endDay)} · ${trip.durationDays} hari")
            Spacer(Modifier.height(12.dp))
            BudgetBar(trip.totalActual, trip.totalBudget)
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    "${Fmt.rupiah(trip.totalActual)} dari ${Fmt.rupiah(trip.totalBudget)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val over = trip.remaining < 0
                Text(
                    if (over) "Lebih ${Fmt.rupiah(-trip.remaining)}" else "Sisa ${Fmt.rupiah(trip.remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (over) Danger else usageColor(trip.usage),
                )
            }
        }
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
