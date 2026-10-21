package id.abati.autocare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.AttachmentKind
import id.abati.autocare.data.CheckStatus
import id.abati.autocare.data.InspectionRecord
import id.abati.autocare.data.InspectionResult
import id.abati.autocare.data.OwnerType
import id.abati.autocare.data.Templates
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.odometerWarning
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.AttachmentPicker
import id.abati.autocare.ui.components.AttachmentStrip
import id.abati.autocare.ui.components.ChoiceChips
import id.abati.autocare.ui.components.ConfirmDialog
import id.abati.autocare.ui.components.DateInput
import id.abati.autocare.ui.components.Dropdown
import id.abati.autocare.ui.components.EmptyState
import id.abati.autocare.ui.components.InfoRow
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.NumberInput
import id.abati.autocare.ui.components.Pill
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.TextInput
import id.abati.autocare.ui.components.guessKind
import id.abati.autocare.ui.components.selectedVehicle
import id.abati.autocare.ui.components.view
import id.abati.autocare.ui.theme.Danger
import id.abati.autocare.ui.theme.Success
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.AttachmentStore
import id.abati.autocare.util.Fmt

fun CheckStatus.color() = when (this) {
    CheckStatus.OK -> Success
    CheckStatus.ATTENTION -> Warning
    CheckStatus.PROBLEM -> Danger
    CheckStatus.NOT_CHECKED -> androidx.compose.ui.graphics.Color.Gray
}

@Composable
fun InspectionEditScreen(data: AppData, settings: AppSettings, id: String?, vehicleIdArg: String?, nav: Nav) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val original = id?.let { iid -> data.inspections.firstOrNull { it.id == iid } }
    val isNew = original == null
    val defaultVehicle = vehicleIdArg?.takeIf { data.vehicleById[it] != null } ?: data.selectedVehicle(settings)?.id ?: ""
    val base = remember(id) {
        original ?: InspectionRecord(vehicleId = defaultVehicle, day = Fmt.today(), odometer = data.currentOdometer(defaultVehicle)?.km)
    }
    var vehicleId by rememberSaveable { mutableStateOf(base.vehicleId) }
    var day by rememberSaveable { mutableStateOf(base.day) }
    var odometer by rememberSaveable { mutableStateOf(base.odometer?.toString() ?: "") }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var newItem by rememberSaveable { mutableStateOf("") }
    val results = remember(id) {
        mutableStateListOf<InspectionResult>().apply {
            val existing = original?.let { data.resultsByInspection[it.id] } ?: emptyList()
            if (existing.isNotEmpty()) addAll(existing) else addAll(Templates.inspectionChecklist.map { InspectionResult(inspectionId = base.id, name = it) })
        }
    }
    val pending = remember(id) { mutableStateListOf<AttachmentStore.Imported>() }
    val existingFiles = original?.let { data.attachmentsOf(OwnerType.INSPECTION, it.id) } ?: emptyList()
    val odoWarning = Fmt.parseDigits(odometer)?.let { data.odometerWarning(vehicleId, day, it, ignoreRefId = original?.id) }

    fun discard() {
        AttachmentStore.cleaner(context).delete(pending.map { it.fileName })
        nav.back()
    }
    BackHandler { discard() }

    ScreenScaffold(title = if (isNew) "Inspeksi mandiri" else "Ubah inspeksi", onBack = ::discard) { pad ->
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(day, { if (it != null) day = it }, "Tanggal", Modifier.weight(1f))
                    NumberInput(odometer, { odometer = it }, "Odometer", Modifier.weight(1f), suffix = "km", isError = odoWarning != null)
                }
                if (odoWarning != null) Text(odoWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    for (i in results.indices) if (results[i].status == CheckStatus.NOT_CHECKED) results[i] = results[i].copy(status = CheckStatus.OK)
                }) { Text("Tandai semua yang belum diperiksa sebagai baik") }
            }
            SectionCard(title = "Checklist (${results.count { it.status != CheckStatus.NOT_CHECKED }}/${results.size})") {
                results.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { results.removeAt(i) }) { Icon(Icons.Default.Delete, "Hapus item") }
                        }
                        ChoiceChips(
                            listOf(CheckStatus.OK, CheckStatus.ATTENTION, CheckStatus.PROBLEM, CheckStatus.NOT_CHECKED), r.status, { it.label },
                            { s -> results[i] = results[i].copy(status = s) },
                        )
                        if (r.status == CheckStatus.ATTENTION || r.status == CheckStatus.PROBLEM || r.note.isNotBlank()) {
                            TextInput(r.note, { n -> results[i] = results[i].copy(note = n) }, "Catatan")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput(newItem, { newItem = it }, "Item tambahan", Modifier.weight(1f))
                    IconButton(onClick = {
                        if (newItem.isNotBlank()) {
                            results.add(InspectionResult(inspectionId = base.id, name = newItem.trim()))
                            newItem = ""
                        }
                    }) { Icon(Icons.Default.Add, "Tambah item") }
                }
            }
            SectionCard(title = "Catatan & foto") {
                TextInput(notes, { notes = it }, "Catatan umum", singleLine = false, minLines = 2)
                AttachmentStrip(existingFiles.map { it.view() }, onDelete = { v ->
                    existingFiles.firstOrNull { it.id == v.key }?.let { a -> actions.run("Lampiran dihapus") { deleteAttachment(a) } }
                })
                AttachmentStrip(pending.map { it.view() }, onDelete = { v ->
                    pending.removeAll { it.fileName == v.key }
                    AttachmentStore.cleaner(context).delete(listOf(v.fileName))
                })
                AttachmentPicker(onImported = { pending.add(it) })
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (vehicleId.isBlank() || results.isEmpty()) {
                        actions.message("Pilih kendaraan dan isi checklist")
                    } else {
                        val record = base.copy(vehicleId = vehicleId, day = day, odometer = Fmt.parseDigits(odometer), notes = notes.trim())
                        val list = results.toList()
                        val files = pending.toList()
                        actions.run("Inspeksi disimpan", onDone = {
                            pending.clear()
                            Reminders.checkNow(context)
                            nav.back()
                        }) {
                            saveInspection(record, list)
                            files.forEach { f ->
                                saveAttachment(
                                    Attachment(
                                        vehicleId = record.vehicleId, ownerType = OwnerType.INSPECTION, ownerId = record.id, fileName = f.fileName,
                                        displayName = f.displayName, mimeType = f.mimeType, sizeBytes = f.size, kind = guessKind(f, AttachmentKind.PHOTO),
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { Text("Simpan inspeksi") }
        }
    }
}

@Composable
fun InspectionDetailScreen(data: AppData, id: String, nav: Nav) {
    val actions = LocalActions.current
    val record = data.inspections.firstOrNull { it.id == id }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (record == null) {
        ScreenScaffold("Inspeksi", onBack = nav::back) { pad -> EmptyState(Icons.Default.Checklist, "Data tidak ditemukan", "Inspeksi mungkin sudah dihapus.", Modifier.padding(pad)) }
        return
    }
    val results = data.resultsByInspection[record.id] ?: emptyList()
    val files = data.attachmentsOf(OwnerType.INSPECTION, record.id)
    val issues = results.filter { it.status == CheckStatus.ATTENTION || it.status == CheckStatus.PROBLEM }
    val vehicle = data.vehicleById[record.vehicleId]

    ScreenScaffold(
        title = "Inspeksi ${Fmt.date(record.day)}",
        subtitle = vehicle?.let { it.plate.ifBlank { it.displayName } },
        onBack = nav::back,
        actions = {
            IconButton(onClick = { nav.editInspection(record.id) }) { Icon(Icons.Default.Edit, "Ubah") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                InfoRow("Tanggal", Fmt.dateLong(record.day))
                InfoRow("Odometer", Fmt.km(record.odometer))
                InfoRow("Hasil", "${results.count { it.status == CheckStatus.OK }} baik · ${issues.size} perlu tindakan")
                if (record.notes.isNotBlank()) InfoRow("Catatan", record.notes)
                if (issues.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.editVisit(vehicleId = record.vehicleId) }) { Text("Catat perbaikan") }
                        TextButton(onClick = { nav.editReminder(vehicleId = record.vehicleId) }) { Text("Buat pengingat") }
                    }
                }
            }
            SectionCard(title = "Checklist") {
                results.sortedByDescending { it.status.ordinal.let { o -> if (it.status == CheckStatus.NOT_CHECKED) -1 else o } }.forEach { r ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name)
                            if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill(r.status.label, r.status.color())
                    }
                }
            }
            if (files.isNotEmpty()) {
                SectionCard(title = "Foto") { AttachmentStrip(files.map { it.view() }, onDelete = null) }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Hapus inspeksi?", "Hasil inspeksi ${Fmt.date(record.day)} dan fotonya akan dihapus.", "Hapus",
            onConfirm = { actions.run("Inspeksi dihapus", onDone = nav::back) { deleteInspection(record) } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
