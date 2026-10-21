package id.abati.autocare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.AttachmentKind
import id.abati.autocare.data.Component
import id.abati.autocare.data.ComponentInstallation
import id.abati.autocare.data.OwnerType
import id.abati.autocare.domain.componentState
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.tasksOfComponent
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
import id.abati.autocare.ui.components.statusColor
import id.abati.autocare.ui.components.view
import id.abati.autocare.ui.theme.Success
import id.abati.autocare.util.Fmt

// ---------------------------------------------------------------- detail & timeline

private data class TimelineEvent(val day: Long?, val km: Long?, val title: String, val detail: String, val color: Color, val onClick: (() -> Unit)?)

@Composable
fun ComponentDetailScreen(data: AppData, id: String, nav: Nav) {
    val actions = LocalActions.current
    val component = data.componentById[id]
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val today = remember { Fmt.today() }
    if (component == null) {
        ScreenScaffold("Komponen", onBack = nav::back) { pad -> EmptyState(Icons.Default.Settings, "Data tidak ditemukan", "Komponen mungkin sudah dihapus.", Modifier.padding(pad)) }
        return
    }
    val state = data.componentState(component, today)
    val vehicle = data.vehicleById[component.vehicleId]
    val files = data.attachmentsOf(OwnerType.COMPONENT, component.id)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Timeline: pemasangan, pelepasan, dan pemeriksaan/servis dari kunjungan bengkel
    val events = buildList {
        state.history.forEach { i ->
            val label = listOf(i.brand, i.type).filter { it.isNotBlank() }.joinToString(" ")
            add(
                TimelineEvent(
                    i.installedDay, i.installedKm, "Dipasang" + if (label.isNotBlank()) " · $label" else "",
                    listOfNotNull(
                        i.price.takeIf { it > 0 }?.let { Fmt.rupiah(it) },
                        i.serialNumber.takeIf { it.isNotBlank() }?.let { "SN $it" },
                        i.warrantyUntilDay?.let { "garansi s.d. ${Fmt.date(it)}" },
                        i.notes.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    Success, { nav.editInstallation(i.id) },
                ),
            )
            if (i.removed) {
                add(TimelineEvent(i.removedDay, i.removedKm, "Dilepas" + if (i.removalReason.isNotBlank()) " · ${i.removalReason}" else "", label, muted, { nav.editInstallation(i.id) }))
            }
        }
        data.tasksOfComponent(component.id)
            .filter { (t, _) -> !t.action.changesComponent }
            .forEach { (t, v) -> add(TimelineEvent(v.day, v.odometer, t.action.label + " · ${t.name}", listOf(v.workshopName, t.notes).filter { it.isNotBlank() }.joinToString(" · "), primary, { nav.visit(v.id) })) }
    }.sortedWith(compareByDescending<TimelineEvent> { it.day ?: Long.MIN_VALUE }.thenByDescending { it.km ?: Long.MIN_VALUE })

    ScreenScaffold(
        title = component.name,
        subtitle = listOfNotNull(component.categoryName.takeIf { it.isNotBlank() }, vehicle?.let { it.plate.ifBlank { it.displayName } }).joinToString(" · "),
        onBack = nav::back,
        actions = {
            IconButton(onClick = { nav.editComponent(component.id) }) { Icon(Icons.Default.Edit, "Ubah") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.current != null) "Terpasang" else "Tidak terpasang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val st = state.info?.status
                    Pill(state.conditionLabel, if (st != null && st.needsAttention) statusColor(st) else Success)
                }
                state.current?.let { cur ->
                    InfoRow("Tanggal pasang", Fmt.date(cur.installedDay))
                    InfoRow("Odometer pasang", Fmt.km(cur.installedKm))
                    InfoRow("Merek / tipe", listOf(cur.brand, cur.type).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "–" })
                    if (cur.price > 0) InfoRow("Harga", Fmt.rupiah(cur.price))
                    InfoRow(
                        "Umur pakai",
                        listOfNotNull(cur.expectedLifeKm?.let { Fmt.km(it) }, cur.expectedLifeMonths?.let { "$it bulan" }).joinToString(" / ").ifBlank { "Belum ditentukan" },
                    )
                    state.info?.let { info ->
                        InfoRow("Penggantian berikutnya", Fmt.dueSummary(info))
                        val rem = Fmt.remainingSummary(info)
                        if (rem.isNotBlank()) InfoRow("Sisa", rem, valueColor = statusColor(info.status))
                    }
                    val used = cur.installedKm?.let { k -> data.currentOdometer(component.vehicleId)?.km?.minus(k) }
                    if (used != null && used >= 0) InfoRow("Sudah dipakai", Fmt.km(used))
                }
                if (state.current == null || state.info == null) {
                    Text(
                        "Penggantian berikutnya belum ditentukan. Atur umur pakai sesuai rekomendasi produsen dan hasil pemeriksaan.",
                        style = MaterialTheme.typography.bodySmall, color = muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.editVisit(vehicleId = component.vehicleId) }) { Text("Catat di servis") }
                    OutlinedButton(onClick = { nav.editInstallation(componentId = component.id) }) { Text("Pemasangan manual") }
                }
            }
            if (component.notes.isNotBlank()) SectionCard(title = "Catatan") { Text(component.notes) }

            SectionCard(title = "Timeline (${events.size})") {
                if (events.isEmpty()) Text("Belum ada riwayat.", color = muted, style = MaterialTheme.typography.bodySmall)
                events.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().then(if (e.onClick != null) Modifier.clickableRow(e.onClick) else Modifier).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Spacer(Modifier.padding(top = 5.dp).size(10.dp).background(e.color, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, fontWeight = FontWeight.Medium)
                            Text(listOf(Fmt.date(e.day), Fmt.km(e.km)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
                            if (e.detail.isNotBlank()) Text(e.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            SectionCard(title = "Foto & dokumen") {
                AttachmentStrip(files.map { it.view() }, onDelete = { v ->
                    files.firstOrNull { it.id == v.key }?.let { a -> actions.run("Lampiran dihapus") { deleteAttachment(a) } }
                })
                AttachmentPicker(onImported = { f ->
                    actions.run("Lampiran ditambahkan") {
                        saveAttachment(
                            Attachment(
                                vehicleId = component.vehicleId, ownerType = OwnerType.COMPONENT, ownerId = component.id, fileName = f.fileName,
                                displayName = f.displayName, mimeType = f.mimeType, sizeBytes = f.size, kind = guessKind(f, AttachmentKind.PHOTO),
                            ),
                        )
                    }
                })
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Hapus komponen?",
            "Komponen \"${component.name}\" beserta ${state.history.size} catatan pemasangannya akan dihapus. Catatan servis tetap ada.",
            "Hapus",
            onConfirm = { actions.run("Komponen dihapus", onDone = nav::back) { deleteComponent(component) } },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

// ---------------------------------------------------------------- form komponen

@Composable
fun ComponentEditScreen(data: AppData, settings: AppSettings, id: String?, vehicleIdArg: String?, nav: Nav) {
    val actions = LocalActions.current
    val original = id?.let { data.componentById[it] }
    val isNew = original == null
    val defaultVehicle = vehicleIdArg?.takeIf { data.vehicleById[it] != null } ?: data.selectedVehicle(settings)?.id ?: ""
    val base = remember(id) { original ?: Component(vehicleId = defaultVehicle, name = "") }
    val componentItems = data.items.filter { it.isComponent }

    var vehicleId by rememberSaveable { mutableStateOf(base.vehicleId) }
    var itemId by rememberSaveable { mutableStateOf(base.itemId) }
    var name by rememberSaveable { mutableStateOf(base.name) }
    var category by rememberSaveable { mutableStateOf(base.categoryName) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    // Pemasangan awal (khusus komponen baru)
    var installed by rememberSaveable { mutableStateOf(true) }
    var day by rememberSaveable { mutableStateOf<Long?>(Fmt.today()) }
    var km by rememberSaveable { mutableStateOf(data.currentOdometer(defaultVehicle)?.km?.toString() ?: "") }
    var brand by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var lifeKm by rememberSaveable { mutableStateOf("") }
    var lifeMonths by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(title = if (isNew) "Tambah komponen" else "Ubah komponen", onBack = nav::back) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Komponen") {
                if (isNew) {
                    Dropdown(
                        "Kendaraan", data.activeVehicles.map { it.id }, vehicleId.takeIf { it.isNotBlank() },
                        { vid -> data.vehicleById[vid]?.let { "${it.displayName} ${it.plate}".trim() } ?: "" }, { vehicleId = it },
                    )
                    Dropdown("Dari katalog", componentItems, componentItems.firstOrNull { it.id == itemId }, { it.name }, { item ->
                        itemId = item.id
                        name = item.name
                        category = data.categoryById[item.categoryId]?.name ?: ""
                        lifeKm = item.intervalKm?.toString() ?: ""
                        lifeMonths = item.intervalMonths?.toString() ?: ""
                    }, placeholder = "Pilih atau isi sendiri")
                }
                TextInput(name, { name = it }, "Nama komponen *", supporting = "Contoh: Aki, Ban depan kiri, Dashcam, Head unit")
                TextInput(category, { category = it }, "Kategori")
                TextInput(notes, { notes = it }, "Catatan", singleLine = false, minLines = 2)
            }
            if (isNew) {
                SectionCard(title = "Pemasangan saat ini") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sudah terpasang di kendaraan", Modifier.weight(1f))
                        Switch(checked = installed, onCheckedChange = { installed = it })
                    }
                    if (installed) {
                        Text("Kosongkan yang tidak diketahui.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateInput(day, { day = it }, "Tanggal pasang", Modifier.weight(1f), clearable = true)
                            NumberInput(km, { km = it }, "Odometer", Modifier.weight(1f), suffix = "km")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextInput(brand, { brand = it }, "Merek", Modifier.weight(1f))
                            TextInput(type, { type = it }, "Tipe", Modifier.weight(1f))
                        }
                        NumberInput(price, { price = it }, "Harga", prefix = "Rp")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberInput(lifeKm, { lifeKm = it }, "Umur pakai", Modifier.weight(1f), suffix = "km")
                            NumberInput(lifeMonths, { lifeMonths = it.take(3) }, "Umur pakai", Modifier.weight(1f), suffix = "bln")
                        }
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (name.isBlank() || vehicleId.isBlank()) {
                        actions.message("Isi kendaraan dan nama komponen")
                    } else {
                        val c = base.copy(vehicleId = vehicleId, itemId = itemId, name = name.trim(), categoryName = category.trim(), notes = notes.trim())
                        val inst = if (isNew && installed) {
                            ComponentInstallation(
                                componentId = c.id, vehicleId = vehicleId, brand = brand.trim(), type = type.trim(), price = Fmt.parseDigits(price) ?: 0,
                                installedDay = day, installedKm = Fmt.parseDigits(km),
                                expectedLifeKm = Fmt.parseDigits(lifeKm)?.takeIf { it > 0 }, expectedLifeMonths = lifeMonths.toIntOrNull()?.takeIf { it > 0 },
                            )
                        } else {
                            null
                        }
                        actions.run(if (isNew) "Komponen ditambahkan" else "Perubahan disimpan", onDone = {
                            nav.back()
                            if (isNew) nav.component(c.id)
                        }) {
                            saveComponent(c)
                            if (inst != null) installManually(inst)
                        }
                    }
                },
            ) { Text("Simpan") }
        }
    }
}

// ---------------------------------------------------------------- episode pemasangan

@Composable
fun InstallationEditScreen(data: AppData, id: String?, componentIdArg: String?, nav: Nav) {
    val actions = LocalActions.current
    val original = id?.let { iid -> data.installations.firstOrNull { it.id == iid } }
    val component = (original?.componentId ?: componentIdArg)?.let { data.componentById[it] }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (component == null) {
        ScreenScaffold("Pemasangan", onBack = nav::back) { pad -> EmptyState(Icons.Default.Settings, "Data tidak ditemukan", "Komponen mungkin sudah dihapus.", Modifier.padding(pad)) }
        return
    }
    val isNew = original == null
    val item = component.itemId?.let { data.itemById[it] }
    val base = remember(id) {
        original ?: ComponentInstallation(
            componentId = component.id, vehicleId = component.vehicleId, installedDay = Fmt.today(),
            installedKm = data.currentOdometer(component.vehicleId)?.km,
            expectedLifeKm = item?.intervalKm, expectedLifeMonths = item?.intervalMonths,
        )
    }
    val fromService = base.taskId != null

    var day by rememberSaveable { mutableStateOf(base.installedDay) }
    var km by rememberSaveable { mutableStateOf(base.installedKm?.toString() ?: "") }
    var brand by rememberSaveable { mutableStateOf(base.brand) }
    var type by rememberSaveable { mutableStateOf(base.type) }
    var serial by rememberSaveable { mutableStateOf(base.serialNumber) }
    var price by rememberSaveable { mutableStateOf(if (base.price > 0) base.price.toString() else "") }
    var lifeKm by rememberSaveable { mutableStateOf(base.expectedLifeKm?.toString() ?: "") }
    var lifeMonths by rememberSaveable { mutableStateOf(base.expectedLifeMonths?.toString() ?: "") }
    var nextDay by rememberSaveable { mutableStateOf(base.nextDay) }
    var nextKm by rememberSaveable { mutableStateOf(base.nextKm?.toString() ?: "") }
    var warranty by rememberSaveable { mutableStateOf(base.warrantyUntilDay) }
    var before by rememberSaveable { mutableStateOf(base.conditionBefore) }
    var after by rememberSaveable { mutableStateOf(base.conditionAfter) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var removed by rememberSaveable { mutableStateOf(base.removed) }
    var removedDay by rememberSaveable { mutableStateOf(base.removedDay) }
    var removedKm by rememberSaveable { mutableStateOf(base.removedKm?.toString() ?: "") }
    var reason by rememberSaveable { mutableStateOf(base.removalReason) }

    ScreenScaffold(
        title = if (isNew) "Pemasangan baru" else "Episode pemasangan",
        subtitle = component.name,
        onBack = nav::back,
        actions = { if (!isNew && !fromService) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Hapus") } },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (fromService) {
                SectionCard {
                    Text("Dicatat dari kunjungan servis. Tanggal, km, merek & harga mengikuti catatan servis.", style = MaterialTheme.typography.bodySmall)
                    base.taskId?.let { tid -> data.tasks.firstOrNull { it.id == tid } }?.let { t ->
                        TextButton(onClick = { nav.visit(t.visitId) }) { Text("Buka kunjungan servis") }
                    }
                }
            }
            if (isNew) {
                Text(
                    "Episode yang sedang terpasang akan ditutup otomatis (riwayatnya tetap tersimpan).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = "Pemasangan") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(day, { if (!fromService) day = it }, "Tanggal pasang", Modifier.weight(1f), clearable = !fromService)
                    NumberInput(km, { if (!fromService) km = it }, "Odometer", Modifier.weight(1f), suffix = "km")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(brand, { if (!fromService) brand = it }, "Merek", Modifier.weight(1f))
                    TextInput(type, { if (!fromService) type = it }, "Tipe", Modifier.weight(1f))
                }
                TextInput(serial, { serial = it }, "Nomor seri")
                NumberInput(price, { if (!fromService) price = it }, "Harga", prefix = "Rp")
                DateInput(warranty, { warranty = it }, "Garansi s.d.", clearable = true)
            }
            SectionCard(title = "Penggantian berikutnya") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(lifeKm, { lifeKm = it }, "Umur pakai", Modifier.weight(1f), suffix = "km")
                    NumberInput(lifeMonths, { lifeMonths = it.take(3) }, "Umur pakai", Modifier.weight(1f), suffix = "bln")
                }
                Text("Atau tentukan langsung (mis. hasil pemeriksaan):", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateInput(nextDay, { nextDay = it }, "Tanggal", Modifier.weight(1f), clearable = true)
                    NumberInput(nextKm, { nextKm = it }, "Kilometer", Modifier.weight(1f), suffix = "km")
                }
            }
            SectionCard(title = "Kondisi") {
                TextInput(before, { before = it }, "Kondisi sebelum", singleLine = false)
                TextInput(after, { after = it }, "Kondisi sesudah", singleLine = false)
                TextInput(notes, { notes = it }, "Catatan", singleLine = false)
            }
            if (!isNew) {
                SectionCard(title = "Pelepasan") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sudah dilepas/diganti", Modifier.weight(1f))
                        Switch(checked = removed, onCheckedChange = { removed = it; if (it && removedDay == null) removedDay = Fmt.today() })
                    }
                    if (removed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DateInput(removedDay, { removedDay = it }, "Tanggal lepas", Modifier.weight(1f), clearable = true)
                            NumberInput(removedKm, { removedKm = it }, "Odometer", Modifier.weight(1f), suffix = "km")
                        }
                        ChoiceChips(listOf("Diganti", "Rusak", "Dijual", "Dilepas"), reason, { it }, { reason = it })
                    }
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val inst = base.copy(
                        installedDay = day, installedKm = Fmt.parseDigits(km), brand = brand.trim(), type = type.trim(), serialNumber = serial.trim(),
                        price = Fmt.parseDigits(price) ?: 0, expectedLifeKm = Fmt.parseDigits(lifeKm)?.takeIf { it > 0 },
                        expectedLifeMonths = lifeMonths.toIntOrNull()?.takeIf { it > 0 }, nextDay = nextDay, nextKm = Fmt.parseDigits(nextKm),
                        warrantyUntilDay = warranty, conditionBefore = before.trim(), conditionAfter = after.trim(), notes = notes.trim(),
                        removed = removed, removedDay = if (removed) removedDay else null, removedKm = if (removed) Fmt.parseDigits(removedKm) else null,
                        removalReason = if (removed) reason else "",
                    )
                    actions.run("Riwayat komponen disimpan", onDone = nav::back) {
                        if (isNew) installManually(inst) else saveInstallation(inst)
                    }
                },
            ) { Text("Simpan") }
        }
    }
    if (confirmDelete && original != null) {
        ConfirmDialog(
            "Hapus episode ini?", "Catatan pemasangan ${Fmt.date(original.installedDay)} akan dihapus dari riwayat komponen.", "Hapus",
            onConfirm = { actions.run("Episode dihapus", onDone = nav::back) { deleteInstallation(original) } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
