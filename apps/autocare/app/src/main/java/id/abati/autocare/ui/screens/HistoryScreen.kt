package id.abati.autocare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.data.VisitType
import id.abati.autocare.domain.HistoryEntry
import id.abati.autocare.domain.HistoryFilter
import id.abati.autocare.domain.HistoryKind
import id.abati.autocare.domain.Period
import id.abati.autocare.domain.history
import id.abati.autocare.domain.workshopNames
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.ChoiceChips
import id.abati.autocare.ui.components.DateInput
import id.abati.autocare.ui.components.Dropdown
import id.abati.autocare.ui.components.EmptyState
import id.abati.autocare.ui.components.Pill
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.VehicleChips
import id.abati.autocare.ui.theme.Success
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.Fmt
import java.time.LocalDate

/** Pilihan periode cepat untuk filter riwayat & biaya. */
enum class PeriodPreset(val label: String) { ALL("Semua"), MONTH("Bulan ini"), DAYS90("90 hari"), YEAR("Tahun ini"), LAST_YEAR("Tahun lalu"), CUSTOM("Pilih tanggal") }

fun PeriodPreset.toPeriod(today: Long, from: Long?, to: Long?): Period {
    val year = LocalDate.ofEpochDay(today).year
    return when (this) {
        PeriodPreset.ALL -> Period.ALL
        PeriodPreset.MONTH -> Period.month(today)
        PeriodPreset.DAYS90 -> Period.lastDays(today, 90)
        PeriodPreset.YEAR -> Period.year(year)
        PeriodPreset.LAST_YEAR -> Period.year(year - 1)
        PeriodPreset.CUSTOM -> Period(from, to)
    }
}

@Composable
fun HistoryScreen(data: AppData, settings: AppSettings, nav: Nav, bottomBar: @Composable () -> Unit) {
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(HistoryKind.ALL) }
    var preset by rememberSaveable { mutableStateOf(PeriodPreset.ALL) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var visitType by rememberSaveable { mutableStateOf<VisitType?>(null) }
    var workshop by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val today = remember { Fmt.today() }

    val filter = HistoryFilter(vehicleId, query, preset.toPeriod(today, from, to), kind, visitType, workshop)
    val entries = data.history(filter)
    val activeFilters = listOfNotNull(visitType, workshop, preset.takeIf { it != PeriodPreset.ALL }).size

    ScreenScaffold(
        title = "Riwayat",
        subtitle = "${entries.size} catatan",
        bottomBar = bottomBar,
        actions = {
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Default.FilterList, "Filter", tint = if (activeFilters > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        floatingActionButton = {
            if (data.activeVehicles.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { nav.editVisit(vehicleId = vehicleId) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Servis") })
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Hapus") } },
                    placeholder = { Text("Cari pekerjaan, komponen, bengkel, km…") },
                )
            }
            item { VehicleChips(data, vehicleId, { vehicleId = it }, allLabel = "Semua kendaraan") }
            item { ChoiceChips(HistoryKind.entries, kind, { it.label }, { kind = it }) }
            if (showFilters) {
                item {
                    SectionCard(title = "Filter") {
                        ChoiceChips(PeriodPreset.entries, preset, { it.label }, { preset = it })
                        if (preset == PeriodPreset.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateInput(from, { from = it }, "Dari", Modifier.weight(1f), clearable = true)
                                DateInput(to, { to = it }, "Sampai", Modifier.weight(1f), clearable = true)
                            }
                        }
                        Dropdown("Jenis servis", listOf<VisitType?>(null) + VisitType.entries, visitType, { it?.label ?: "Semua jenis" }, { visitType = it })
                        Dropdown("Bengkel", listOf<String?>(null) + data.workshopNames(), workshop, { it ?: "Semua bengkel" }, { workshop = it })
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.History,
                        if (data.visits.isEmpty() && data.expenses.isEmpty()) "Riwayat masih kosong" else "Tidak ada yang cocok",
                        if (data.visits.isEmpty()) "Setiap servis, perbaikan, penggantian, modifikasi, dan inspeksi akan tampil di sini." else "Ubah kata kunci atau filter.",
                    )
                }
            }
            var lastMonth: String? = null
            entries.forEach { e ->
                val month = Fmt.month(e.day)
                if (month != lastMonth) {
                    lastMonth = month
                    item(key = "m-$month-${e.id}") {
                        Text(month, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item(key = e.id) { HistoryCard(data, e, showVehicle = vehicleId == null, nav = nav) }
            }
        }
    }
}

@Composable
private fun HistoryCard(data: AppData, e: HistoryEntry, showVehicle: Boolean, nav: Nav) {
    val vehicle = data.vehicleById[e.vehicleId]
    val vehicleLabel = if (showVehicle && vehicle != null) vehicle.plate.ifBlank { vehicle.displayName } else null
    when (e) {
        is HistoryEntry.Visit -> EntryCard(
            icon = Icons.Default.Build,
            title = e.visit.type.label + if (e.visit.workshopName.isNotBlank()) " · ${e.visit.workshopName}" else "",
            subtitle = e.tasks.joinToString(", ") { it.name }.ifBlank { "Tanpa rincian pekerjaan" },
            meta = listOfNotNull(Fmt.date(e.day), e.km?.let { Fmt.km(it) }, vehicleLabel).joinToString(" · "),
            amount = Fmt.rupiah(e.total),
            badge = if (e.visit.status == VisitStatus.DRAFT) "Draft" else null,
            onClick = { nav.visit(e.visit.id) },
        )
        is HistoryEntry.Spend -> EntryCard(
            icon = Icons.Default.Payments,
            title = e.expense.category.label,
            subtitle = e.expense.description.ifBlank { "Pengeluaran" },
            meta = listOfNotNull(Fmt.date(e.day), e.km?.let { Fmt.km(it) }, vehicleLabel).joinToString(" · "),
            amount = Fmt.rupiah(e.expense.amount),
            badge = null,
            onClick = { nav.editExpense(e.expense.id) },
        )
        is HistoryEntry.Inspection -> EntryCard(
            icon = Icons.Default.Checklist,
            title = "Inspeksi mandiri",
            subtitle = when {
                e.problems > 0 -> "${e.problems} bermasalah, ${e.attention} perlu perhatian"
                e.attention > 0 -> "${e.attention} perlu perhatian"
                else -> "Semua item baik"
            },
            meta = listOfNotNull(Fmt.date(e.day), e.km?.let { Fmt.km(it) }, vehicleLabel).joinToString(" · "),
            amount = null,
            badge = if (e.problems + e.attention > 0) "Cek" else "Baik",
            onClick = { nav.inspection(e.record.id) },
        )
    }
}

@Composable
private fun EntryCard(icon: ImageVector, title: String, subtitle: String, meta: String, amount: String?, badge: String?, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (amount != null) Text(amount, fontWeight = FontWeight.SemiBold)
                if (badge != null) Pill(badge, if (badge == "Baik") Success else Warning)
            }
        }
    }
}
