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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.MaintenanceItem
import id.abati.autocare.data.Reminder
import id.abati.autocare.domain.DueStatus
import id.abati.autocare.domain.ReminderEngine
import id.abati.autocare.domain.ReminderState
import id.abati.autocare.domain.completionsOf
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.kmPerDay
import id.abati.autocare.domain.reminderStates
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.ChoiceChips
import id.abati.autocare.ui.components.ConfirmDialog
import id.abati.autocare.ui.components.DateInput
import id.abati.autocare.ui.components.Dropdown
import id.abati.autocare.ui.components.EmptyState
import id.abati.autocare.ui.components.InfoRow
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.NumberInput
import id.abati.autocare.ui.components.ReminderRow
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.StatusBadge
import id.abati.autocare.ui.components.TextInput
import id.abati.autocare.ui.components.VehicleChips
import id.abati.autocare.ui.components.selectedVehicle
import id.abati.autocare.ui.components.statusColor
import id.abati.autocare.util.Fmt

private enum class ReminderGroup(val label: String) { ATTENTION("Perlu tindakan"), UPCOMING("Akan datang"), INCOMPLETE("Data belum lengkap"), DONE("Selesai & dijeda") }

private fun ReminderState.group(): ReminderGroup = when (info.status) {
    DueStatus.OVERDUE, DueStatus.DUE, DueStatus.DUE_SOON -> ReminderGroup.ATTENTION
    DueStatus.UPCOMING -> ReminderGroup.UPCOMING
    DueStatus.UNKNOWN -> ReminderGroup.INCOMPLETE
    DueStatus.DONE, DueStatus.PAUSED -> ReminderGroup.DONE
}

@Composable
fun RemindersScreen(data: AppData, settings: AppSettings, nav: Nav, bottomBar: @Composable () -> Unit) {
    var vehicleId by rememberSaveable { mutableStateOf(data.selectedVehicle(settings)?.id) }
    val today = remember { Fmt.today() }
    val states = data.reminderStates(vehicleId, today)
    val grouped = states.groupBy { it.group() }

    ScreenScaffold(
        title = "Pengingat",
        bottomBar = bottomBar,
        floatingActionButton = {
            if (data.activeVehicles.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { nav.editReminder(vehicleId = vehicleId) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Pengingat") })
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { VehicleChips(data, vehicleId, { vehicleId = it }, allLabel = "Semua") }
            if (states.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.Notifications, "Belum ada pengingat",
                        "Buat pengingat berdasarkan tanggal, kilometer, atau keduanya — misalnya ganti oli setiap 5.000 km atau 6 bulan.",
                    )
                }
            }
            ReminderGroup.entries.forEach { g ->
                val list = grouped[g] ?: return@forEach
                item(key = "h-${g.name}") {
                    Text("${g.label} (${list.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
                items(list, key = { it.reminder.id }) { s ->
                    ReminderRow(s, data, showVehicle = vehicleId == null, onClick = { nav.editReminder(s.reminder.id) }, today = today)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- form

@Composable
fun ReminderEditScreen(data: AppData, settings: AppSettings, id: String?, vehicleIdArg: String?, nav: Nav) {
    val actions = LocalActions.current
    val original = id?.let { rid -> data.reminders.firstOrNull { it.id == rid } }
    val isNew = original == null
    val defaultVehicle = vehicleIdArg?.let { data.vehicleById[it] }?.id ?: data.selectedVehicle(settings)?.id
    val base = remember(id) { original ?: Reminder(vehicleId = defaultVehicle ?: "", title = "") }

    var vehicleId by rememberSaveable { mutableStateOf(base.vehicleId) }
    var itemId by rememberSaveable { mutableStateOf(base.itemId) }
    var title by rememberSaveable { mutableStateOf(base.title) }
    var recurring by rememberSaveable { mutableStateOf(base.recurring) }
    var intervalKm by rememberSaveable { mutableStateOf(base.intervalKm?.toString() ?: "") }
    var intervalMonths by rememberSaveable { mutableStateOf(base.intervalMonths?.toString() ?: "") }
    var baseDay by rememberSaveable { mutableStateOf(base.baseDay) }
    var baseKm by rememberSaveable { mutableStateOf(base.baseKm?.toString() ?: "") }
    var dueDay by rememberSaveable { mutableStateOf(base.dueDay) }
    var dueKm by rememberSaveable { mutableStateOf(base.dueKm?.toString() ?: "") }
    var beforeKm by rememberSaveable { mutableStateOf(base.notifyBeforeKm.toString()) }
    var beforeDays by rememberSaveable { mutableStateOf(base.notifyBeforeDays.toString()) }
    var active by rememberSaveable { mutableStateOf(base.active) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var categoryId by rememberSaveable { mutableStateOf(itemId?.let { data.itemById[it]?.categoryId } ?: data.categories.firstOrNull()?.id) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var snoozeOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val today = remember { Fmt.today() }

    fun applyTemplate(item: MaintenanceItem) {
        itemId = item.id
        title = item.name
        intervalKm = item.intervalKm?.toString() ?: ""
        intervalMonths = item.intervalMonths?.toString() ?: ""
        recurring = item.intervalKm != null || item.intervalMonths != null
    }

    // Draft reminder dari isian form → pratinjau jatuh tempo memakai engine yang sama
    val draft = base.copy(
        vehicleId = vehicleId, itemId = itemId, title = title.trim(), recurring = recurring,
        intervalKm = if (recurring) Fmt.parseDigits(intervalKm)?.takeIf { it > 0 } else null,
        intervalMonths = if (recurring) intervalMonths.toIntOrNull()?.takeIf { it > 0 } else null,
        baseDay = if (recurring) baseDay else null,
        baseKm = if (recurring) Fmt.parseDigits(baseKm) else null,
        dueDay = dueDay, dueKm = Fmt.parseDigits(dueKm),
        notifyBeforeKm = Fmt.parseDigits(beforeKm) ?: 500, notifyBeforeDays = beforeDays.toIntOrNull() ?: 14,
        active = active, notes = notes.trim(),
    )
    val preview = if (vehicleId.isNotBlank()) {
        ReminderEngine.evaluate(
            ReminderEngine.ruleOf(draft), data.completionsOf(draft.id), data.currentOdometer(vehicleId)?.km, today, data.kmPerDay(vehicleId, today),
        )
    } else {
        null
    }
    val invalid = title.isBlank() || vehicleId.isBlank() ||
        (recurring && draft.intervalKm == null && draft.intervalMonths == null) ||
        (!recurring && draft.dueDay == null && draft.dueKm == null)

    ScreenScaffold(
        title = if (isNew) "Pengingat baru" else "Pengingat",
        onBack = nav::back,
        actions = {
            if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (original != null && preview != null) {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(original.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        StatusBadge(preview.status)
                    }
                    Text(Fmt.dueSummary(preview))
                    Text(Fmt.remainingSummary(preview), color = statusColor(preview.status), style = MaterialTheme.typography.bodySmall)
                    preview.last?.let { Text("Terakhir: ${Fmt.date(it.day)} · ${Fmt.km(it.km)}", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.editVisit(vehicleId = original.vehicleId, reminderId = original.id) }) {
                            Icon(Icons.Default.Build, null)
                            Text("  Catat servis")
                        }
                        if (preview.status != DueStatus.DONE) {
                            OutlinedButton(onClick = { snoozeOpen = true }) {
                                Icon(Icons.Default.Snooze, null)
                                Text("  Tunda")
                            }
                        }
                    }
                    if (!original.recurring) {
                        if (original.completedDay == null && preview.status != DueStatus.DONE) {
                            TextButton(onClick = { actions.run("Ditandai selesai", onDone = nav::back) { markReminderDone(original, today) } }) {
                                Icon(Icons.Default.Done, null)
                                Text("  Tandai selesai tanpa catatan servis")
                            }
                        } else if (original.completedDay != null) {
                            TextButton(onClick = { actions.run("Pengingat dibuka kembali") { markReminderDone(original, null) } }) { Text("Batalkan status selesai") }
                        }
                    }
                    if (original.snoozeDay != null || original.snoozeKm != null) {
                        TextButton(onClick = { actions.run("Penundaan dibatalkan") { snooze(original, null, null) } }) { Text("Batalkan penundaan") }
                    }
                }
                val done = data.tasksByReminder[original.id].orEmpty().mapNotNull { t -> data.visitById[t.visitId]?.let { t to it } }.sortedByDescending { it.second.day }
                if (done.isNotEmpty()) {
                    SectionCard(title = "Riwayat pengerjaan") {
                        done.forEach { (t, v) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("${Fmt.date(v.day)} · ${Fmt.km(v.odometer)}", Modifier.weight(1f))
                                Text(t.action.label + if (v.status.name == "DRAFT") " (draft)" else "", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Perawatan") {
                if (isNew) {
                    Dropdown("Kendaraan", data.activeVehicles.map { it.id }, vehicleId.takeIf { it.isNotBlank() }, { vid -> data.vehicleById[vid]?.let { "${it.displayName} ${it.plate}".trim() } ?: "" }, { vehicleId = it })
                    Text("Pilih dari template", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(data.categories.map { it.id }, categoryId, { cid -> data.categoryById[cid]?.name ?: "" }, { categoryId = it })
                    val items = data.items.filter { it.categoryId == categoryId }
                    Dropdown("Item perawatan", items, items.firstOrNull { it.id == itemId }, { item -> item.name + intervalLabel(item) }, ::applyTemplate, placeholder = "Pilih item atau isi sendiri")
                }
                TextInput(title, { title = it }, "Nama pengingat *", isError = tried && title.isBlank())
                ChoiceChips(listOf(true, false), recurring, { if (it) "Berulang" else "Sekali" }, { recurring = it })
            }

            if (recurring) {
                SectionCard(title = "Interval (mana yang tercapai lebih dulu)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberInput(intervalKm, { intervalKm = it }, "Setiap", Modifier.weight(1f), suffix = "km")
                        NumberInput(intervalMonths, { intervalMonths = it.take(3) }, "Setiap", Modifier.weight(1f), suffix = "bulan")
                    }
                    if (tried && draft.intervalKm == null && draft.intervalMonths == null) {
                        Text("Isi interval km dan/atau bulan", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Interval template hanyalah titik awal. Sesuaikan dengan buku manual pabrikan dan kondisi kendaraan.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionCard(title = "Terakhir dikerjakan (jika diketahui)") {
                    Text(
                        "Kosongkan bila belum tahu — aplikasi tidak akan mengarang data. Setelah servis dicatat, acuan otomatis diperbarui.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DateInput(baseDay, { baseDay = it }, "Tanggal terakhir", clearable = true)
                    NumberInput(baseKm, { baseKm = it }, "Kilometer terakhir", suffix = "km")
                    val cur = data.currentOdometer(vehicleId)
                    if (cur != null && baseDay == null && baseKm.isBlank()) {
                        TextButton(onClick = { baseDay = today; baseKm = cur.km.toString() }) { Text("Mulai dari hari ini (${Fmt.km(cur.km)})") }
                    }
                }
            }
            SectionCard(title = if (recurring) "Jadwal pertama (opsional)" else "Jatuh tempo *") {
                if (recurring) {
                    Text("Isi jika jadwal berikutnya sudah ditentukan bengkel, mis. stiker \"servis berikut 48.000 km\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DateInput(dueDay, { dueDay = it }, "Tanggal", clearable = true)
                NumberInput(dueKm, { dueKm = it }, "Kilometer", suffix = "km")
                if (!recurring && tried && draft.dueDay == null && draft.dueKm == null) {
                    Text("Isi tanggal dan/atau kilometer jatuh tempo", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            SectionCard(title = "Notifikasi") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(beforeKm, { beforeKm = it }, "Ingatkan sebelum", Modifier.weight(1f), suffix = "km")
                    NumberInput(beforeDays, { beforeDays = it.take(3) }, "Ingatkan sebelum", Modifier.weight(1f), suffix = "hari")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengingat aktif", Modifier.weight(1f))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                TextInput(notes, { notes = it }, "Catatan", singleLine = false, minLines = 2)
            }
            if (preview != null && isNew) {
                SectionCard(title = "Pratinjau") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dueSummary(preview), Modifier.weight(1f))
                        StatusBadge(preview.status)
                    }
                    val rem = Fmt.remainingSummary(preview)
                    if (rem.isNotBlank()) Text(rem, style = MaterialTheme.typography.bodySmall, color = statusColor(preview.status))
                }
            }
            Button(
                onClick = {
                    tried = true
                    if (invalid) {
                        actions.message("Lengkapi data pengingat")
                    } else {
                        actions.run(if (isNew) "Pengingat dibuat" else "Pengingat disimpan", onDone = nav::back) { saveReminder(draft) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simpan") }
        }
    }

    if (snoozeOpen && original != null) {
        SnoozeDialog(original, preview?.dueDay, preview?.dueKm, today, onDismiss = { snoozeOpen = false }) { d, k ->
            actions.run("Pengingat ditunda") { snooze(original, d, k) }
        }
    }
    if (confirmDelete && original != null) {
        ConfirmDialog(
            "Hapus pengingat?", "\"${original.title}\" akan dihapus. Riwayat servis yang terkait tetap tersimpan.", "Hapus",
            onConfirm = { actions.run("Pengingat dihapus", onDone = nav::back) { deleteReminder(original) } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

fun intervalLabel(item: MaintenanceItem): String {
    val parts = listOfNotNull(item.intervalKm?.let { Fmt.km(it) }, item.intervalMonths?.let { "$it bln" })
    return if (parts.isEmpty()) "" else " (${parts.joinToString(" / ")})"
}

@Composable
private fun SnoozeDialog(r: Reminder, dueDay: Long?, dueKm: Long?, today: Long, onDismiss: () -> Unit, onSnooze: (Long?, Long?) -> Unit) {
    var day by remember { mutableStateOf(dueDay?.let { maxOf(it, today) + 7 } ?: (today + 7)) }
    var km by remember { mutableStateOf(dueKm?.let { (it + 500).toString() } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tunda ${r.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Jatuh tempo tidak akan lebih awal dari nilai berikut. Penundaan otomatis batal setelah servis dicatat.", style = MaterialTheme.typography.bodySmall)
                if (dueDay != null) {
                    ChoiceChips(listOf(7L, 14L, 30L), null, { "+$it hari" }, { day = maxOf(dueDay, today) + it })
                    DateInput(day, { if (it != null) day = it }, "Tunda sampai tanggal")
                }
                if (dueKm != null) {
                    ChoiceChips(listOf(500L, 1000L, 2000L), null, { "+${Fmt.number(it)} km" }, { km = (dueKm + it).toString() })
                    NumberInput(km, { km = it }, "Tunda sampai", suffix = "km")
                }
                if (dueDay == null && dueKm == null) Text("Jatuh tempo belum bisa dihitung, lengkapi data pengingat dulu.")
            }
        },
        confirmButton = {
            TextButton(enabled = dueDay != null || dueKm != null, onClick = {
                onDismiss()
                onSnooze(if (dueDay != null) day else null, if (dueKm != null) Fmt.parseDigits(km) else null)
            }) { Text("Tunda") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
