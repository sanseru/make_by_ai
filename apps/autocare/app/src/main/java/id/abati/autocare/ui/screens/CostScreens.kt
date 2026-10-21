package id.abati.autocare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.AttachmentKind
import id.abati.autocare.data.Expense
import id.abati.autocare.data.ExpenseCategory
import id.abati.autocare.data.OwnerType
import id.abati.autocare.domain.CostGroup
import id.abati.autocare.domain.Period
import id.abati.autocare.domain.costEntries
import id.abati.autocare.domain.monthlyTotals
import id.abati.autocare.domain.odometerWarning
import id.abati.autocare.domain.summarize
import id.abati.autocare.report.PdfReport
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.AttachmentPicker
import id.abati.autocare.ui.components.AttachmentStrip
import id.abati.autocare.ui.components.ChoiceChips
import id.abati.autocare.ui.components.ConfirmDialog
import id.abati.autocare.ui.components.DateInput
import id.abati.autocare.ui.components.Dropdown
import id.abati.autocare.ui.components.InfoRow
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.NumberInput
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.TextInput
import id.abati.autocare.ui.components.VehicleChips
import id.abati.autocare.ui.components.guessKind
import id.abati.autocare.ui.components.selectedVehicle
import id.abati.autocare.ui.components.view
import id.abati.autocare.util.AttachmentStore
import id.abati.autocare.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// ---------------------------------------------------------------- rekap biaya

@Composable
fun CostsScreen(data: AppData, settings: AppSettings, nav: Nav, bottomBar: @Composable () -> Unit) {
    var vehicleId by rememberSaveable { mutableStateOf(data.selectedVehicle(settings)?.id) }
    var preset by rememberSaveable { mutableStateOf(PeriodPreset.YEAR) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = remember { Fmt.today() }
    val thisYear = LocalDate.ofEpochDay(today).year
    var chartYear by rememberSaveable { mutableStateOf(thisYear) }

    val period = preset.toPeriod(today, from, to)
    val entries = data.costEntries(vehicleId, period)
    val summary = summarize(entries)
    val allEntries = data.costEntries(vehicleId, Period.ALL)
    val years = (allEntries.map { LocalDate.ofEpochDay(it.day).year } + thisYear).distinct().sortedDescending()
    val monthly = monthlyTotals(allEntries, chartYear)
    val expenses = data.expenses.filter { (vehicleId == null || it.vehicleId == vehicleId) && it.day in period }.sortedByDescending { it.day }

    ScreenScaffold(
        title = "Biaya & laporan",
        bottomBar = bottomBar,
        actions = { IconButton(onClick = { nav.report(vehicleId) }) { Icon(Icons.Default.PictureAsPdf, "Laporan PDF") } },
        floatingActionButton = {
            if (data.activeVehicles.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { nav.editExpense(vehicleId = vehicleId) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Pengeluaran") })
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { VehicleChips(data, vehicleId, { vehicleId = it }, allLabel = "Semua kendaraan") }
            item { ChoiceChips(PeriodPreset.entries, preset, { it.label }, { preset = it }) }
            if (preset == PeriodPreset.CUSTOM) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateInput(from, { from = it }, "Dari", Modifier.weight(1f), clearable = true)
                        DateInput(to, { to = it }, "Sampai", Modifier.weight(1f), clearable = true)
                    }
                }
            }
            item {
                SectionCard {
                    Text("Total pengeluaran · ${preset.label.lowercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.rupiah(summary.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("${summary.visitCount} kunjungan bengkel · ${expenses.size} pengeluaran lain", style = MaterialTheme.typography.bodySmall)
                    if (summary.byGroup.isNotEmpty()) HorizontalDivider()
                    CostGroup.entries.forEach { g -> summary.byGroup[g]?.let { InfoRow(g.label, Fmt.rupiah(it)) } }
                }
            }
            if (summary.byLabel.isNotEmpty()) {
                item {
                    SectionCard(title = "Per kategori") {
                        val max = summary.byLabel.maxOf { it.second }.coerceAtLeast(1)
                        summary.byLabel.forEach { (label, amount) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Fmt.rupiah(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                            LinearProgressIndicator(
                                progress = { (amount.toFloat() / max).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Tren bulanan $chartYear") {
                    if (years.size > 1) ChoiceChips(years, chartYear, { it.toString() }, { chartYear = it })
                    MonthlyBars(monthly)
                    Text("Total $chartYear: ${Fmt.rupiah(monthly.sum())}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (expenses.isNotEmpty()) {
                item { Text("Pengeluaran lain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                items(expenses, key = { it.id }) { e ->
                    SectionCard(onClick = { nav.editExpense(e.id) }) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(e.category.label, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(Fmt.date(e.day), e.description, if (vehicleId == null) data.vehicleById[e.vehicleId]?.plate ?: "" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Fmt.rupiah(e.amount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.report(vehicleId) }, modifier = Modifier.weight(1f)) { Text("Laporan PDF") }
                    OutlinedButton(onClick = nav::backup, modifier = Modifier.weight(1f)) { Text("Backup & restore") }
                }
            }
        }
    }
}

/** Grafik batang sederhana 12 bulan (tanpa library chart). */
@Composable
private fun MonthlyBars(values: LongArray) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                val fraction = (v.toFloat() / max).coerceIn(0f, 1f)
                if (v > 0) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
                } else {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..12).forEach { m ->
            Text(Fmt.monthShort(m).take(3), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- pengeluaran

@Composable
fun ExpenseEditScreen(data: AppData, settings: AppSettings, id: String?, vehicleIdArg: String?, nav: Nav) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val original = id?.let { eid -> data.expenses.firstOrNull { it.id == eid } }
    val isNew = original == null
    val defaultVehicle = vehicleIdArg?.takeIf { data.vehicleById[it] != null } ?: data.selectedVehicle(settings)?.id ?: ""
    val base = remember(id) { original ?: Expense(vehicleId = defaultVehicle, day = Fmt.today(), amount = 0) }

    var vehicleId by rememberSaveable { mutableStateOf(base.vehicleId) }
    var day by rememberSaveable { mutableStateOf(base.day) }
    var category by rememberSaveable { mutableStateOf(base.category) }
    var amount by rememberSaveable { mutableStateOf(if (base.amount > 0) base.amount.toString() else "") }
    var description by rememberSaveable { mutableStateOf(base.description) }
    var odometer by rememberSaveable { mutableStateOf(base.odometer?.toString() ?: "") }
    var payment by rememberSaveable { mutableStateOf(base.paymentMethod) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val pending = remember(id) { mutableStateListOf<AttachmentStore.Imported>() }
    val existing = original?.let { data.attachmentsOf(OwnerType.EXPENSE, it.id) } ?: emptyList()
    val odoWarning = Fmt.parseDigits(odometer)?.let { data.odometerWarning(vehicleId, day, it, ignoreRefId = original?.id) }

    fun discard() {
        AttachmentStore.cleaner(context).delete(pending.map { it.fileName })
        nav.back()
    }
    BackHandler { discard() }

    ScreenScaffold(
        title = if (isNew) "Catat pengeluaran" else "Pengeluaran",
        onBack = ::discard,
        actions = { if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                if (isNew) {
                    Dropdown(
                        "Kendaraan", data.activeVehicles.map { it.id }, vehicleId.takeIf { it.isNotBlank() },
                        { vid -> data.vehicleById[vid]?.let { "${it.displayName} ${it.plate}".trim() } ?: "" }, { vehicleId = it },
                    )
                }
                Dropdown("Kategori", ExpenseCategory.entries, category, { it.label }, { category = it })
                NumberInput(amount, { amount = it }, "Jumlah *", prefix = "Rp")
                DateInput(day, { if (it != null) day = it }, "Tanggal")
                TextInput(description, { description = it }, "Keterangan")
                NumberInput(odometer, { odometer = it }, "Odometer (opsional)", suffix = "km", supporting = odoWarning, isError = odoWarning != null)
                TextInput(payment, { payment = it }, "Metode pembayaran (opsional)")
            }
            SectionCard(title = "Bukti") {
                AttachmentStrip(existing.map { it.view() }, onDelete = { v ->
                    existing.firstOrNull { it.id == v.key }?.let { a -> actions.run("Lampiran dihapus") { deleteAttachment(a) } }
                })
                AttachmentStrip(pending.map { it.view() }, onDelete = { v ->
                    pending.removeAll { it.fileName == v.key }
                    AttachmentStore.cleaner(context).delete(listOf(v.fileName))
                })
                AttachmentPicker(onImported = { pending.add(it) })
            }
            Button(
                onClick = {
                    val value = Fmt.parseDigits(amount) ?: 0
                    if (vehicleId.isBlank() || value <= 0) {
                        actions.message("Isi kendaraan dan jumlah pengeluaran")
                    } else {
                        val e = base.copy(
                            vehicleId = vehicleId, day = day, category = category, amount = value, description = description.trim(),
                            odometer = Fmt.parseDigits(odometer), paymentMethod = payment.trim(),
                        )
                        val files = pending.toList()
                        actions.run(if (isNew) "Pengeluaran dicatat" else "Perubahan disimpan", onDone = { pending.clear(); nav.back() }) {
                            saveExpense(e)
                            files.forEach { f ->
                                saveAttachment(
                                    Attachment(
                                        vehicleId = e.vehicleId, ownerType = OwnerType.EXPENSE, ownerId = e.id, fileName = f.fileName,
                                        displayName = f.displayName, mimeType = f.mimeType, sizeBytes = f.size, kind = guessKind(f, AttachmentKind.RECEIPT),
                                    ),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simpan") }
        }
    }
    if (confirmDelete && original != null) {
        ConfirmDialog(
            "Hapus pengeluaran?", "${original.category.label} ${Fmt.rupiah(original.amount)} pada ${Fmt.date(original.day)} akan dihapus.", "Hapus",
            onConfirm = { actions.run("Pengeluaran dihapus", onDone = nav::back) { deleteExpense(original) } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

// ---------------------------------------------------------------- laporan PDF

@Composable
fun ReportScreen(data: AppData, settings: AppSettings, vehicleIdArg: String?, nav: Nav) {
    val context = LocalContext.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    var vehicleId by rememberSaveable { mutableStateOf(vehicleIdArg?.takeIf { data.vehicleById[it] != null } ?: data.selectedVehicle(settings)?.id) }
    var preset by rememberSaveable { mutableStateOf(PeriodPreset.ALL) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    var tasks by rememberSaveable { mutableStateOf(true) }
    var expenses by rememberSaveable { mutableStateOf(true) }
    var components by rememberSaveable { mutableStateOf(true) }
    var inspections by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    val today = remember { Fmt.today() }

    ScreenScaffold(title = "Laporan PDF", onBack = nav::back) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Isi laporan") {
                Dropdown(
                    "Kendaraan", data.vehicles.map { it.id }, vehicleId,
                    { vid -> data.vehicleById[vid]?.let { "${it.displayName} ${it.plate}".trim() } ?: "" }, { vehicleId = it; file = null },
                )
                Text("Periode", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(PeriodPreset.entries, preset, { it.label }, { preset = it; file = null })
                if (preset == PeriodPreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateInput(from, { from = it; file = null }, "Dari", Modifier.weight(1f), clearable = true)
                        DateInput(to, { to = it; file = null }, "Sampai", Modifier.weight(1f), clearable = true)
                    }
                }
                Toggle("Status jadwal perawatan", reminders) { reminders = it; file = null }
                Toggle("Rincian pekerjaan per kunjungan", tasks) { tasks = it; file = null }
                Toggle("Pengeluaran lain", expenses) { expenses = it; file = null }
                Toggle("Riwayat komponen", components) { components = it; file = null }
                Toggle("Hasil inspeksi", inspections) { inspections = it; file = null }
                Text(
                    "Laporan PDF berguna sebagai dokumentasi (mis. saat menjual mobil), tetapi bukan pengganti backup lengkap.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                enabled = vehicleId != null && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val vehicle = vehicleId?.let { data.vehicleById[it] }
                    if (vehicle != null) {
                        val periodLabel = if (preset == PeriodPreset.CUSTOM) "${Fmt.date(from)} – ${Fmt.date(to)}" else preset.label
                        val options = PdfReport.Options(preset.toPeriod(today, from, to), periodLabel, reminders, tasks, expenses, components, inspections)
                        busy = true
                        scope.launch {
                            try {
                                file = withContext(Dispatchers.IO) { PdfReport.generate(context, data, vehicle, options) }
                            } catch (e: Exception) {
                                actions.message("Gagal membuat PDF: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
            ) { Text(if (busy) "Membuat laporan…" else "Buat laporan PDF") }
            file?.let { f ->
                SectionCard(title = "Laporan siap") {
                    Text(f.name, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { PdfReport.view(context, f) }) {
                            Icon(Icons.Default.Visibility, null)
                            Text("  Buka")
                        }
                        OutlinedButton(onClick = { PdfReport.share(context, f) }) {
                            Icon(Icons.Default.Share, null)
                            Text("  Bagikan/simpan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
