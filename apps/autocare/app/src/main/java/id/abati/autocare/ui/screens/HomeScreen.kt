package id.abati.autocare.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NotificationAdd
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.Period
import id.abati.autocare.domain.componentStates
import id.abati.autocare.domain.costEntries
import id.abati.autocare.domain.reminderStates
import id.abati.autocare.domain.summarize
import id.abati.autocare.domain.visitCost
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.EmptyState
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.Pill
import id.abati.autocare.ui.components.ReminderRow
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.VehicleChips
import id.abati.autocare.ui.components.VehicleSummaryRow
import id.abati.autocare.ui.components.selectedVehicle
import id.abati.autocare.ui.components.statusColor
import id.abati.autocare.ui.theme.Danger
import id.abati.autocare.ui.theme.Success
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.Fmt
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(data: AppData, settings: AppSettings, nav: Nav, bottomBar: @Composable () -> Unit) {
    val actions = LocalActions.current
    val vehicle = data.selectedVehicle(settings)
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var odometerFor by rememberSaveable { mutableStateOf<String?>(null) }
    val today = remember { Fmt.today() }

    ScreenScaffold(
        title = "AutoCare",
        subtitle = "Catatan & pengingat kendaraan",
        actions = { IconButton(onClick = nav::settings) { Icon(Icons.Default.Settings, "Pengaturan") } },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (vehicle != null) {
                ExtendedFloatingActionButton(onClick = { quickAdd = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Catat") })
            }
        },
    ) { pad ->
        if (vehicle == null) {
            Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    Icons.Default.DirectionsCar,
                    "Belum ada kendaraan",
                    "Daftarkan mobil Anda untuk mulai mencatat servis, komponen, biaya, dan menerima pengingat perawatan. Tidak perlu akun atau internet.",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nav.editVehicle() }) { Text("Tambah kendaraan") }
                        TextButton(onClick = nav::backup) { Text("Pulihkan dari backup") }
                    }
                }
            }
        } else {
            HomeContent(data, settings, nav, vehicle, today, pad) { odometerFor = it }
        }
    }

    if (quickAdd && vehicle != null) {
        ModalBottomSheet(onDismissRequest = { quickAdd = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text("Catat untuk ${vehicle.plate.ifBlank { vehicle.displayName }}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                QuickItem(Icons.Default.Build, "Catat servis / perbaikan", "Beberapa pekerjaan dalam satu kunjungan") { quickAdd = false; nav.editVisit(vehicleId = vehicle.id) }
                QuickItem(Icons.Default.Speed, "Update kilometer", "Catat odometer terbaru") { quickAdd = false; odometerFor = vehicle.id }
                QuickItem(Icons.Default.NotificationAdd, "Tambah pengingat", "Dari template atau buat sendiri") { quickAdd = false; nav.editReminder(vehicleId = vehicle.id) }
                QuickItem(Icons.Default.Payments, "Catat pengeluaran", "Pajak, asuransi, aksesori, cuci, dll") { quickAdd = false; nav.editExpense(vehicleId = vehicle.id) }
                QuickItem(Icons.Default.Checklist, "Inspeksi mandiri", "Checklist ban, lampu, rem, cairan") { quickAdd = false; nav.editInspection(vehicleId = vehicle.id) }
                QuickItem(Icons.Default.Restore, "Pasang/ganti komponen", "Catat riwayat komponen") { quickAdd = false; nav.editComponent(vehicleId = vehicle.id) }
            }
        }
    }
    odometerFor?.let { id -> OdometerDialog(data, id, onDismiss = { odometerFor = null }) }
}

@Composable
private fun HomeContent(
    data: AppData,
    settings: AppSettings,
    nav: Nav,
    vehicle: Vehicle,
    today: Long,
    pad: PaddingValues,
    onUpdateKm: (String) -> Unit,
) {
    val actions = LocalActions.current
    val states = data.reminderStates(vehicle.id, today)
    val upcoming = states.filter { it.info.status != DueStatus.DONE && it.info.status != DueStatus.PAUSED }.take(4)
    val attention = states.count { it.info.status == DueStatus.OVERDUE || it.info.status == DueStatus.DUE }
    val month = summarize(data.costEntries(vehicle.id, Period.month(today))).total
    val year = summarize(data.costEntries(vehicle.id, Period.year(LocalDate.ofEpochDay(today).year))).total
    val lastVisit = data.visits.filter { it.vehicleId == vehicle.id && it.status == VisitStatus.DONE }.maxByOrNull { it.day }
    val components = data.componentStates(vehicle.id, today).filter { it.current != null }.take(6)
    val backupAge = if (settings.lastBackupAt > 0) (System.currentTimeMillis() - settings.lastBackupAt) / 86_400_000L else null
    val needBackup = settings.backupReminderDays > 0 && (backupAge == null || backupAge >= settings.backupReminderDays)

    LazyColumn(
        Modifier.padding(pad).fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            VehicleChips(data, vehicle.id, onSelect = { id ->
                if (id != null) actions.launch { actions.settings.update { it.copy(selectedVehicleId = id) } }
            })
        }
        item {
            SectionCard(onClick = { nav.vehicle(vehicle.id) }) {
                VehicleSummaryRow(data, vehicle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onUpdateKm(vehicle.id) }) {
                        Icon(Icons.Default.Speed, null)
                        Text("  Update km")
                    }
                    OutlinedButton(onClick = { nav.editVisit(vehicleId = vehicle.id) }) {
                        Icon(Icons.Default.Build, null)
                        Text("  Catat servis")
                    }
                }
            }
        }
        if (attention > 0) {
            item {
                Card(
                    onClick = { nav.tab("reminders") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Danger)
                        Spacer(Modifier.padding(6.dp))
                        Text(
                            "$attention perawatan sudah jatuh tempo atau terlambat",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Biaya bulan ini", Fmt.rupiah(month), Modifier.weight(1f)) { nav.tab("costs") }
                StatTile("Biaya tahun ini", Fmt.rupiah(year), Modifier.weight(1f)) { nav.tab("costs") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pengingat terdekat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.tab("reminders") }) { Text("Semua") }
            }
        }
        if (upcoming.isEmpty()) {
            item {
                SectionCard {
                    Text("Belum ada pengingat aktif.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { nav.editReminder(vehicleId = vehicle.id) }) { Text("Buat pengingat dari template") }
                }
            }
        }
        items(upcoming, key = { it.reminder.id }) { s ->
            ReminderRow(s, data, showVehicle = false, onClick = { nav.editReminder(s.reminder.id) }, today = today)
        }
        if (lastVisit != null) {
            item {
                SectionCard(title = "Servis terakhir", onClick = { nav.visit(lastVisit.id) }) {
                    val tasks = data.tasksByVisit[lastVisit.id] ?: emptyList()
                    Text("${Fmt.date(lastVisit.day)} · ${Fmt.km(lastVisit.odometer)}", fontWeight = FontWeight.Medium)
                    Text(
                        tasks.joinToString(", ") { it.name }.ifBlank { lastVisit.type.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Text(
                        listOf(lastVisit.workshopName, Fmt.rupiah(data.visitCost(lastVisit).total)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (components.isNotEmpty()) {
            item {
                SectionCard(title = "Kondisi komponen", action = { TextButton(onClick = { nav.vehicle(vehicle.id) }) { Text("Detail") } }) {
                    components.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.component.name, Modifier.weight(1f))
                            val st = c.info?.status
                            val color = if (st != null && st.needsAttention) statusColor(st) else Success
                            Pill(c.conditionLabel, color)
                        }
                    }
                }
            }
        }
        if (needBackup) {
            item {
                SectionCard(onClick = nav::backup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, null, tint = Warning)
                        Spacer(Modifier.padding(6.dp))
                        Column {
                            Text("Amankan riwayat dengan backup", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (backupAge == null) "Belum pernah backup." else "Backup terakhir $backupAge hari lalu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    SectionCard(modifier = modifier, onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(0.dp))
    }
}
