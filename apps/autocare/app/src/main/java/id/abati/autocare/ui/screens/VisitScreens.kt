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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.DialogProperties
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.AttachmentKind
import id.abati.autocare.data.MaintenanceItem
import id.abati.autocare.data.OwnerType
import id.abati.autocare.data.Repository
import id.abati.autocare.data.ServiceTask
import id.abati.autocare.data.ServiceVisit
import id.abati.autocare.data.TaskAction
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.data.VisitType
import id.abati.autocare.data.Workshop
import id.abati.autocare.domain.Money
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.odometerWarning
import id.abati.autocare.domain.visitCost
import id.abati.autocare.domain.workshopNames
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
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.AttachmentStore
import id.abati.autocare.util.Fmt

/** Pekerjaan yang sedang diedit beserta rencana komponennya (bila memasang/mengganti komponen baru). */
private data class TaskDraft(val task: ServiceTask, val plan: Repository.ComponentPlan?)

// ---------------------------------------------------------------- form kunjungan

@Composable
fun VisitEditScreen(data: AppData, settings: AppSettings, id: String?, vehicleIdArg: String?, reminderId: String?, nav: Nav) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val original = id?.let { data.visitById[it] }
    val isNew = original == null
    val reminder = reminderId?.let { rid -> data.reminders.firstOrNull { it.id == rid } }
    val defaultVehicle = original?.vehicleId ?: reminder?.vehicleId ?: vehicleIdArg?.takeIf { data.vehicleById[it] != null } ?: data.selectedVehicle(settings)?.id ?: ""
    val base = remember(id) {
        original ?: ServiceVisit(
            vehicleId = defaultVehicle,
            day = Fmt.today(),
            odometer = data.currentOdometer(defaultVehicle)?.km,
            type = VisitType.ROUTINE,
        )
    }

    var vehicleId by rememberSaveable { mutableStateOf(base.vehicleId) }
    var day by rememberSaveable { mutableStateOf(base.day) }
    var odometer by rememberSaveable { mutableStateOf(base.odometer?.toString() ?: "") }
    var type by rememberSaveable { mutableStateOf(base.type) }
    var done by rememberSaveable { mutableStateOf(base.status == VisitStatus.DONE) }
    var workshop by rememberSaveable { mutableStateOf(base.workshopName) }
    var mechanic by rememberSaveable { mutableStateOf(base.mechanic) }
    var invoice by rememberSaveable { mutableStateOf(base.invoiceNumber) }
    var payment by rememberSaveable { mutableStateOf(base.paymentMethod) }
    var complaint by rememberSaveable { mutableStateOf(base.complaint) }
    var diagnosis by rememberSaveable { mutableStateOf(base.diagnosis) }
    var recommendation by rememberSaveable { mutableStateOf(base.recommendation) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var extraFee by rememberSaveable { mutableStateOf(if (base.extraFee > 0) base.extraFee.toString() else "") }
    var extraFeeNote by rememberSaveable { mutableStateOf(base.extraFeeNote) }
    var discount by rememberSaveable { mutableStateOf(if (base.discount > 0) base.discount.toString() else "") }
    var warranty by rememberSaveable { mutableStateOf(base.warrantyUntilDay) }
    var warrantyNotes by rememberSaveable { mutableStateOf(base.warrantyNotes) }
    var acknowledgedOdo by rememberSaveable { mutableStateOf(false) }

    val tasks = remember(id) {
        mutableStateListOf<TaskDraft>().apply {
            if (original != null) {
                addAll((data.tasksByVisit[original.id] ?: emptyList()).map { t -> TaskDraft(t, existingPlan(data, t)) })
            } else if (reminder != null) {
                val item = reminder.itemId?.let { data.itemById[it] }
                add(newTaskFromItem(base, item, reminder.title).let { it.copy(task = it.task.copy(reminderId = reminder.id)) })
            }
        }
    }
    val pending = remember(id) { mutableStateListOf<AttachmentStore.Imported>() }
    var editingTask by remember { mutableStateOf<TaskDraft?>(null) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var showMore by rememberSaveable { mutableStateOf(original != null) }

    val odoValue = Fmt.parseDigits(odometer)
    val odoWarning = odoValue?.let { data.odometerWarning(vehicleId, day, it, ignoreRefId = original?.id) }
    val draftVisit = base.copy(extraFee = Fmt.parseDigits(extraFee) ?: 0, discount = Fmt.parseDigits(discount) ?: 0)
    val cost = Money.visitCost(draftVisit, tasks.map { it.task })

    fun discard() {
        AttachmentStore.cleaner(context).delete(pending.map { it.fileName })
        nav.back()
    }
    BackHandler { discard() }

    ScreenScaffold(title = if (isNew) "Catat servis" else "Ubah kunjungan", onBack = ::discard) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Kunjungan") {
                if (isNew) {
                    Dropdown(
                        "Kendaraan", data.activeVehicles.map { it.id }, vehicleId.takeIf { it.isNotBlank() },
                        { vid -> data.vehicleById[vid]?.let { "${it.displayName} ${it.plate}".trim() } ?: "" },
                        { vid ->
                            if (vid != vehicleId) {
                                vehicleId = vid
                                odometer = data.currentOdometer(vid)?.km?.toString() ?: ""
                                // Tautan reminder/komponen milik kendaraan lain tidak boleh terbawa
                                val copy = tasks.map { it.copy(task = it.task.copy(reminderId = null, componentId = null)) }
                                tasks.clear()
                                tasks.addAll(copy)
                            }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(day, { if (it != null) day = it }, "Tanggal", Modifier.weight(1f))
                    NumberInput(odometer, { odometer = it; acknowledgedOdo = false }, "Odometer", Modifier.weight(1f), suffix = "km", isError = odoWarning != null && !acknowledgedOdo)
                }
                if (odoWarning != null) Text(odoWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ChoiceChips(VisitType.entries, type, { it.label }, { type = it })
                ChoiceChips(listOf(true, false), done, { if (it) "Selesai dikerjakan" else "Draft / rencana" }, { done = it })
                if (!done) {
                    Text(
                        "Draft tidak menghitung biaya, tidak memperbarui pengingat, dan tidak mengubah riwayat komponen.",
                        style = MaterialTheme.typography.bodySmall, color = Warning,
                    )
                }
                WorkshopField(data, workshop) { workshop = it }
            }

            SectionCard(
                title = "Pekerjaan (${tasks.size})",
                action = { TextButton(onClick = { addingTask = true }) { Icon(Icons.Default.Add, null); Text(" Tambah") } },
            ) {
                if (tasks.isEmpty()) {
                    Text("Tambahkan satu atau lebih pekerjaan: ganti oli, filter, servis rem, jasa, dll.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                tasks.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider()
                    TaskRow(data, d, onEdit = { editingTask = d }, onDelete = { tasks.remove(d) })
                }
            }

            SectionCard(title = "Biaya") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(extraFee, { extraFee = it }, "Biaya tambahan", Modifier.weight(1f), prefix = "Rp")
                    NumberInput(discount, { discount = it }, "Diskon", Modifier.weight(1f), prefix = "Rp", isError = cost.total < 0)
                }
                if ((Fmt.parseDigits(extraFee) ?: 0) > 0) TextInput(extraFeeNote, { extraFeeNote = it }, "Keterangan biaya tambahan")
                InfoRow("Suku cadang", Fmt.rupiah(cost.parts))
                InfoRow("Jasa", Fmt.rupiah(cost.labor))
                if (cost.extraFee > 0) InfoRow("Biaya tambahan", Fmt.rupiah(cost.extraFee))
                if (cost.discount > 0) InfoRow("Diskon", "-" + Fmt.rupiah(cost.discount))
                HorizontalDivider()
                InfoRow("Total", Fmt.rupiah(cost.total), valueColor = MaterialTheme.colorScheme.primary)
                if (cost.total < 0) Text("Diskon melebihi subtotal", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = "Nota & foto") {
                val existing = original?.let { data.attachmentsOf(OwnerType.VISIT, it.id) } ?: emptyList()
                AttachmentStrip(existing.map { it.view() }, onDelete = { v ->
                    existing.firstOrNull { it.id == v.key }?.let { a -> actions.run("Lampiran dihapus") { deleteAttachment(a) } }
                })
                AttachmentStrip(pending.map { it.view() }, onDelete = { v ->
                    pending.removeAll { it.fileName == v.key }
                    AttachmentStore.cleaner(context).delete(listOf(v.fileName))
                })
                AttachmentPicker(onImported = { pending.add(it) })
            }

            if (!showMore) {
                OutlinedButton(onClick = { showMore = true }, modifier = Modifier.fillMaxWidth()) { Text("Detail lain: mekanik, invoice, diagnosis, garansi") }
            } else {
                SectionCard(title = "Detail lain") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput(mechanic, { mechanic = it }, "Teknisi/mekanik", Modifier.weight(1f))
                        TextInput(invoice, { invoice = it }, "No. invoice", Modifier.weight(1f))
                    }
                    TextInput(payment, { payment = it }, "Metode pembayaran")
                    TextInput(complaint, { complaint = it }, "Keluhan", singleLine = false)
                    TextInput(diagnosis, { diagnosis = it }, "Diagnosis", singleLine = false)
                    TextInput(recommendation, { recommendation = it }, "Rekomendasi mekanik", singleLine = false)
                    TextInput(notes, { notes = it }, "Catatan", singleLine = false, minLines = 2)
                    DateInput(warranty, { warranty = it }, "Garansi pekerjaan s.d.", clearable = true)
                    if (warranty != null) TextInput(warrantyNotes, { warrantyNotes = it }, "Ketentuan garansi")
                }
            }

            Button(
                onClick = {
                    when {
                        vehicleId.isBlank() -> actions.message("Pilih kendaraan")
                        tasks.isEmpty() -> actions.message("Tambahkan minimal satu pekerjaan")
                        cost.total < 0 -> actions.message("Diskon melebihi subtotal")
                        odoWarning != null && !acknowledgedOdo -> {
                            acknowledgedOdo = true
                            actions.message("Periksa odometer. Tekan Simpan sekali lagi untuk tetap menyimpan.")
                        }
                        else -> {
                            val known = data.workshops.firstOrNull { it.name.equals(workshop.trim(), ignoreCase = true) }
                            val newWorkshop = if (known == null && workshop.isNotBlank()) Workshop(name = workshop.trim()) else null
                            val visit = base.copy(
                                vehicleId = vehicleId, day = day, odometer = odoValue, type = type,
                                status = if (done) VisitStatus.DONE else VisitStatus.DRAFT,
                                workshopId = known?.id ?: newWorkshop?.id, workshopName = workshop.trim(),
                                mechanic = mechanic.trim(), invoiceNumber = invoice.trim(), paymentMethod = payment.trim(),
                                complaint = complaint.trim(), diagnosis = diagnosis.trim(), recommendation = recommendation.trim(), notes = notes.trim(),
                                extraFee = Fmt.parseDigits(extraFee) ?: 0, extraFeeNote = extraFeeNote.trim(), discount = Fmt.parseDigits(discount) ?: 0,
                                warrantyUntilDay = warranty, warrantyNotes = warrantyNotes.trim(),
                            )
                            val taskList = tasks.map { it.task }
                            val plans = tasks.mapNotNull { d -> d.plan?.let { d.task.id to it } }.toMap()
                            val files = pending.toList()
                            actions.run(if (isNew) "Servis dicatat" else "Perubahan disimpan", onDone = {
                                pending.clear()
                                Reminders.checkNow(context)
                                nav.back()
                            }) {
                                if (newWorkshop != null) saveWorkshop(newWorkshop)
                                saveVisit(visit, taskList, plans)
                                files.forEach { f ->
                                    saveAttachment(
                                        Attachment(
                                            vehicleId = visit.vehicleId, ownerType = OwnerType.VISIT, ownerId = visit.id,
                                            fileName = f.fileName, displayName = f.displayName, mimeType = f.mimeType, sizeBytes = f.size,
                                            kind = guessKind(f, AttachmentKind.RECEIPT),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simpan") }
        }
    }

    if (addingTask) {
        TaskDialog(data, vehicleId, null, onDismiss = { addingTask = false }) { tasks.add(it) }
    }
    editingTask?.let { current ->
        TaskDialog(data, vehicleId, current, onDismiss = { editingTask = null }) { updated ->
            val i = tasks.indexOf(current)
            if (i >= 0) tasks[i] = updated
        }
    }
}

/** Rencana komponen dari episode pemasangan yang sudah ada (agar umur pakai bisa diedit ulang). */
private fun existingPlan(data: AppData, t: ServiceTask): Repository.ComponentPlan? {
    if (!t.action.changesComponent || t.componentId == null) return null
    val inst = data.installations.firstOrNull { it.taskId == t.id } ?: return null
    val c = data.componentById[t.componentId] ?: return null
    return Repository.ComponentPlan(c.name, c.categoryName, inst.expectedLifeKm, inst.expectedLifeMonths, inst.serialNumber)
}

private fun newTaskFromItem(visit: ServiceVisit, item: MaintenanceItem?, name: String): TaskDraft = TaskDraft(
    ServiceTask(
        visitId = visit.id, vehicleId = visit.vehicleId, itemId = item?.id, name = name,
        action = if (item?.isComponent == true) TaskAction.REPLACED else TaskAction.SERVICED,
    ),
    null,
)

@Composable
private fun WorkshopField(data: AppData, value: String, onChange: (String) -> Unit) {
    val names = data.workshopNames()
    TextInput(value, onChange, "Bengkel")
    val suggestions = names.filter { value.isBlank() || (it.contains(value, true) && !it.equals(value, true)) }.take(6)
    if (suggestions.isNotEmpty()) ChoiceChips(suggestions, null, { it }, onChange)
}

@Composable
private fun TaskRow(data: AppData, d: TaskDraft, onEdit: () -> Unit, onDelete: () -> Unit) {
    val t = d.task
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.name, fontWeight = FontWeight.Medium)
            val detail = listOf(t.brand, t.partNumber, if (t.quantityMilli != 1000L || t.unit.isNotBlank()) "${Money.formatQuantity(t.quantityMilli)} ${t.unit}".trim() else "")
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(t.action.label, if (t.action.isWorkDone) MaterialTheme.colorScheme.primary else Warning)
                t.reminderId?.let { rid -> data.reminders.firstOrNull { it.id == rid } }?.let { Pill("↻ ${it.title}", MaterialTheme.colorScheme.secondary) }
                val compName = t.componentId?.let { data.componentById[it]?.name } ?: d.plan?.componentName
                if (compName != null) Pill("⚙ $compName", MaterialTheme.colorScheme.tertiary)
            }
            Text(Fmt.rupiah(Money.taskTotal(t)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Ubah") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Hapus") }
    }
}

/** Dialog tambah/ubah satu pekerjaan dalam kunjungan. */
@Composable
private fun TaskDialog(data: AppData, vehicleId: String, existing: TaskDraft?, onDismiss: () -> Unit, onSave: (TaskDraft) -> Unit) {
    val t0 = existing?.task
    var categoryId by remember { mutableStateOf(t0?.itemId?.let { data.itemById[it]?.categoryId } ?: data.categories.firstOrNull()?.id) }
    var itemId by remember { mutableStateOf(t0?.itemId) }
    var name by remember { mutableStateOf(t0?.name ?: "") }
    var action by remember { mutableStateOf(t0?.action ?: TaskAction.REPLACED) }
    var brand by remember { mutableStateOf(t0?.brand ?: "") }
    var partNumber by remember { mutableStateOf(t0?.partNumber ?: "") }
    var qty by remember { mutableStateOf(t0?.let { Money.formatQuantity(it.quantityMilli) } ?: "1") }
    var unit by remember { mutableStateOf(t0?.unit ?: "") }
    var unitPrice by remember { mutableStateOf(t0?.unitPrice?.takeIf { it > 0 }?.toString() ?: "") }
    var labor by remember { mutableStateOf(t0?.laborCost?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(t0?.notes ?: "") }
    var warranty by remember { mutableStateOf(t0?.warrantyUntilDay) }
    var reminderId by remember { mutableStateOf(t0?.reminderId) }
    var componentId by remember { mutableStateOf(t0?.componentId) }
    var newComponent by remember { mutableStateOf(existing?.plan != null && t0?.componentId == null) }
    var compName by remember { mutableStateOf(existing?.plan?.componentName ?: "") }
    var lifeKm by remember { mutableStateOf(existing?.plan?.expectedLifeKm?.toString() ?: "") }
    var lifeMonths by remember { mutableStateOf(existing?.plan?.expectedLifeMonths?.toString() ?: "") }
    var serial by remember { mutableStateOf(existing?.plan?.serialNumber ?: "") }

    val reminders = data.reminders.filter { it.vehicleId == vehicleId }
    val components = data.components.filter { it.vehicleId == vehicleId }
    val quantity = Money.parseQuantity(qty)

    fun pickItem(item: MaintenanceItem) {
        itemId = item.id
        name = item.name
        action = if (item.isComponent) TaskAction.REPLACED else TaskAction.SERVICED
        // Sarankan reminder & komponen yang cocok dengan item ini
        reminders.firstOrNull { it.itemId == item.id }?.let { reminderId = it.id }
        if (item.isComponent) {
            val match = components.firstOrNull { it.itemId == item.id }
            componentId = match?.id
            newComponent = match == null
            compName = item.name
            lifeKm = item.intervalKm?.toString() ?: ""
            lifeMonths = item.intervalMonths?.toString() ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(if (existing == null) "Tambah pekerjaan" else "Ubah pekerjaan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChips(data.categories.map { it.id }, categoryId, { cid -> data.categoryById[cid]?.name ?: "" }, { categoryId = it })
                val items = data.items.filter { it.categoryId == categoryId }
                Dropdown("Dari katalog", items, items.firstOrNull { it.id == itemId }, { it.name }, ::pickItem, placeholder = "Pilih atau isi nama sendiri")
                TextInput(name, { name = it }, "Nama pekerjaan *")
                Text("Tindakan", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(TaskAction.entries, action, { it.label }, { action = it })
                if (action == TaskAction.INSPECTED) {
                    Text("Pemeriksaan tidak dicatat sebagai penggantian komponen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(brand, { brand = it }, "Merek", Modifier.weight(1f))
                    TextInput(partNumber, { partNumber = it }, "Tipe/no. part", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(qty, { qty = it }, "Jumlah", Modifier.weight(1f), isError = quantity == null, keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    TextInput(unit, { unit = it }, "Satuan", Modifier.weight(1f))
                }
                NumberInput(unitPrice, { unitPrice = it }, "Harga suku cadang per satuan", prefix = "Rp")
                NumberInput(labor, { labor = it }, "Biaya jasa", prefix = "Rp")
                if (quantity != null) {
                    Text(
                        "Subtotal: ${Fmt.rupiah(Money.lineTotal(Fmt.parseDigits(unitPrice) ?: 0, quantity) + (Fmt.parseDigits(labor) ?: 0))}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                HorizontalDivider()
                Text("Pengingat terkait", style = MaterialTheme.typography.labelLarge)
                Dropdown(
                    "Pekerjaan ini menyelesaikan pengingat", listOf<String?>(null) + reminders.map { it.id }, reminderId,
                    { rid -> rid?.let { r -> reminders.firstOrNull { it.id == r }?.title } ?: "Tidak ada" }, { reminderId = it },
                )
                if (reminderId != null && !action.isWorkDone && action != TaskAction.INSPECTED) {
                    Text("Pekerjaan yang baru direncanakan tidak menyelesaikan pengingat.", style = MaterialTheme.typography.bodySmall, color = Warning)
                }

                HorizontalDivider()
                Text("Komponen", style = MaterialTheme.typography.labelLarge)
                if (action.changesComponent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newComponent, onCheckedChange = { newComponent = it; if (it) componentId = null })
                        Text("Komponen baru (belum ada di daftar)")
                    }
                }
                if (newComponent && action.changesComponent) {
                    TextInput(compName, { compName = it }, "Nama komponen, mis. Aki / Ban depan kiri")
                    TextInput(serial, { serial = it }, "Nomor seri (opsional)")
                } else {
                    Dropdown(
                        if (action.changesComponent) "Komponen yang diganti/dipasang" else "Komponen yang diperiksa/diservis (opsional)",
                        listOf<String?>(null) + components.map { it.id }, componentId,
                        { cid -> cid?.let { c -> components.firstOrNull { it.id == c }?.name } ?: "Tidak ada" }, { componentId = it },
                    )
                }
                if (action.changesComponent && (newComponent || componentId != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput(lifeKm, { lifeKm = it }, "Umur pakai", Modifier.weight(1f), suffix = "km")
                        NumberInput(lifeMonths, { lifeMonths = it.take(3) }, "Umur pakai", Modifier.weight(1f), suffix = "bln")
                    }
                    Text("Riwayat komponen lama tetap tersimpan; pemasangan ini menjadi episode baru.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DateInput(warranty, { warranty = it }, "Garansi komponen s.d.", clearable = true)
                TextInput(notes, { notes = it }, "Catatan kondisi sebelum/sesudah", singleLine = false)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && quantity != null && !(newComponent && action.changesComponent && compName.isBlank()),
                onClick = {
                    val itemName = itemId?.let { data.itemById[it] }
                    val catName = itemName?.categoryId?.let { data.categoryById[it]?.name } ?: ""
                    val task = (t0 ?: ServiceTask(visitId = "", vehicleId = vehicleId, name = name)).copy(
                        itemId = itemId, name = name.trim(), action = action, brand = brand.trim(), partNumber = partNumber.trim(),
                        quantityMilli = quantity ?: 1000, unit = unit.trim(),
                        unitPrice = Fmt.parseDigits(unitPrice) ?: 0, laborCost = Fmt.parseDigits(labor) ?: 0,
                        notes = notes.trim(), warrantyUntilDay = warranty, reminderId = reminderId,
                        componentId = if (newComponent && action.changesComponent) null else componentId,
                    )
                    val plan = if (action.changesComponent && (newComponent || componentId != null)) {
                        Repository.ComponentPlan(
                            componentName = if (newComponent) compName.trim() else components.firstOrNull { it.id == componentId }?.name ?: name.trim(),
                            categoryName = catName,
                            expectedLifeKm = Fmt.parseDigits(lifeKm)?.takeIf { it > 0 },
                            expectedLifeMonths = lifeMonths.toIntOrNull()?.takeIf { it > 0 },
                            serialNumber = serial.trim(),
                        )
                    } else {
                        null
                    }
                    onSave(TaskDraft(task, plan))
                    onDismiss()
                },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- detail kunjungan

@Composable
fun VisitDetailScreen(data: AppData, id: String, nav: Nav) {
    val actions = LocalActions.current
    val visit = data.visitById[id]
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (visit == null) {
        ScreenScaffold("Kunjungan", onBack = nav::back) { pad -> EmptyState(Icons.Default.Build, "Data tidak ditemukan", "Catatan mungkin sudah dihapus.", Modifier.padding(pad)) }
        return
    }
    val vehicle = data.vehicleById[visit.vehicleId]
    val tasks = data.tasksByVisit[visit.id] ?: emptyList()
    val cost = data.visitCost(visit)
    val files = data.attachmentsOf(OwnerType.VISIT, visit.id)

    ScreenScaffold(
        title = visit.type.label,
        subtitle = listOfNotNull(vehicle?.let { it.plate.ifBlank { it.displayName } }, Fmt.date(visit.day)).joinToString(" · "),
        onBack = nav::back,
        actions = {
            IconButton(onClick = { nav.editVisit(visit.id) }) { Icon(Icons.Default.Edit, "Ubah") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.rupiah(cost.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Pill(visit.status.label, if (visit.status == VisitStatus.DONE) MaterialTheme.colorScheme.primary else Warning)
                }
                InfoRow("Tanggal", Fmt.dateLong(visit.day))
                InfoRow("Odometer", Fmt.km(visit.odometer))
                InfoRow("Bengkel", visit.workshopName.ifBlank { "–" })
                if (visit.mechanic.isNotBlank()) InfoRow("Mekanik", visit.mechanic)
                if (visit.invoiceNumber.isNotBlank()) InfoRow("No. invoice", visit.invoiceNumber)
                if (visit.paymentMethod.isNotBlank()) InfoRow("Pembayaran", visit.paymentMethod)
                visit.warrantyUntilDay?.let { InfoRow("Garansi s.d.", Fmt.date(it) + if (visit.warrantyNotes.isNotBlank()) " · ${visit.warrantyNotes}" else "") }
            }
            SectionCard(title = "Pekerjaan (${tasks.size})") {
                tasks.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(t.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(Fmt.rupiah(Money.taskTotal(t)))
                        }
                        val detail = listOf(
                            t.action.label, t.brand, t.partNumber,
                            if (t.quantityMilli != 1000L || t.unit.isNotBlank()) "${Money.formatQuantity(t.quantityMilli)} ${t.unit} × ${Fmt.rupiah(t.unitPrice)}".trim() else "",
                            if (t.laborCost > 0) "jasa ${Fmt.rupiah(t.laborCost)}" else "",
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        t.componentId?.let { cid -> data.componentById[cid] }?.let { c ->
                            TextButton(onClick = { nav.component(c.id) }) { Text("Riwayat komponen: ${c.name}") }
                        }
                        t.reminderId?.let { rid -> data.reminders.firstOrNull { it.id == rid } }?.let { r ->
                            Text("Menyelesaikan pengingat: ${r.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        t.warrantyUntilDay?.let { Text("Garansi s.d. ${Fmt.date(it)}", style = MaterialTheme.typography.bodySmall) }
                        if (t.notes.isNotBlank()) Text(t.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                InfoRow("Suku cadang", Fmt.rupiah(cost.parts))
                InfoRow("Jasa", Fmt.rupiah(cost.labor))
                if (cost.extraFee > 0) InfoRow("Biaya tambahan", Fmt.rupiah(cost.extraFee) + if (visit.extraFeeNote.isNotBlank()) " (${visit.extraFeeNote})" else "")
                if (cost.discount > 0) InfoRow("Diskon", "-" + Fmt.rupiah(cost.discount))
            }
            val texts = listOf("Keluhan" to visit.complaint, "Diagnosis" to visit.diagnosis, "Rekomendasi" to visit.recommendation, "Catatan" to visit.notes).filter { it.second.isNotBlank() }
            if (texts.isNotEmpty()) {
                SectionCard(title = "Catatan mekanik") { texts.forEach { (k, v) -> InfoRow(k, v) } }
            }
            SectionCard(title = "Nota & foto (${files.size})") {
                AttachmentStrip(files.map { it.view() }, onDelete = { v ->
                    files.firstOrNull { it.id == v.key }?.let { a -> actions.run("Lampiran dihapus") { deleteAttachment(a) } }
                })
                AttachmentPicker(onImported = { f ->
                    actions.run("Lampiran ditambahkan") {
                        saveAttachment(
                            Attachment(
                                vehicleId = visit.vehicleId, ownerType = OwnerType.VISIT, ownerId = visit.id,
                                fileName = f.fileName, displayName = f.displayName, mimeType = f.mimeType, sizeBytes = f.size,
                                kind = guessKind(f, AttachmentKind.RECEIPT),
                            ),
                        )
                    }
                })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Hapus kunjungan?",
            "Kunjungan ${Fmt.date(visit.day)} beserta ${tasks.size} pekerjaan dan ${files.size} lampiran akan dihapus. Jadwal pengingat dan riwayat komponen dihitung ulang otomatis.",
            "Hapus",
            onConfirm = { actions.run("Kunjungan dihapus", onDone = nav::back) { deleteVisit(visit) } },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
