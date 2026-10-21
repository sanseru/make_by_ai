package id.abati.autocare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.AppData
import id.abati.autocare.data.CheckStatus
import id.abati.autocare.data.FuelType
import id.abati.autocare.data.OdometerLog
import id.abati.autocare.data.OdometerSource
import id.abati.autocare.data.Transmission
import id.abati.autocare.data.Vehicle
import id.abati.autocare.data.VisitStatus
import id.abati.autocare.domain.componentStates
import id.abati.autocare.domain.currentOdometer
import id.abati.autocare.domain.odometerWarning
import id.abati.autocare.domain.readings
import id.abati.autocare.domain.visitCost
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.AttachmentPicker
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
import id.abati.autocare.ui.components.Thumbnail
import id.abati.autocare.ui.components.VehicleSummaryRow
import id.abati.autocare.ui.components.statusColor
import id.abati.autocare.ui.theme.Success
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.AttachmentStore
import id.abati.autocare.util.Fmt
import java.time.LocalDate

// ---------------------------------------------------------------- daftar

@Composable
fun VehiclesScreen(data: AppData, nav: Nav, bottomBar: @Composable () -> Unit) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val list = data.vehicles.filter { showArchived || !it.archived }
    ScreenScaffold(
        title = "Kendaraan",
        bottomBar = bottomBar,
        actions = {
            if (data.vehicles.any { it.archived }) {
                TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Sembunyikan arsip" else "Tampilkan arsip") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.editVehicle() }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kendaraan") })
        },
    ) { pad ->
        if (list.isEmpty()) {
            EmptyState(
                Icons.Default.DirectionsCar, "Belum ada kendaraan", "Tambahkan mobil pertama Anda. Satu akun bisa menyimpan beberapa kendaraan.",
                Modifier.padding(pad),
            )
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { v ->
                    SectionCard(onClick = { nav.vehicle(v.id) }) {
                        VehicleSummaryRow(data, v)
                        val visits = data.visits.count { it.vehicleId == v.id && it.status == VisitStatus.DONE }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("$visits servis", MaterialTheme.colorScheme.primary)
                            if (v.archived) Pill("Diarsipkan", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- form

@Composable
fun VehicleEditScreen(data: AppData, id: String?, nav: Nav) {
    val actions = LocalActions.current
    val context = LocalContext.current
    val original = id?.let { data.vehicleById[it] }
    val isNew = original == null
    val base = remember(id) { original ?: Vehicle(brand = "", model = "") }

    var brand by rememberSaveable { mutableStateOf(base.brand) }
    var model by rememberSaveable { mutableStateOf(base.model) }
    var variant by rememberSaveable { mutableStateOf(base.variant) }
    var year by rememberSaveable { mutableStateOf(base.year?.toString() ?: "") }
    var plate by rememberSaveable { mutableStateOf(base.plate) }
    var color by rememberSaveable { mutableStateOf(base.color) }
    var vin by rememberSaveable { mutableStateOf(base.vin) }
    var engine by rememberSaveable { mutableStateOf(base.engineNumber) }
    var purchaseDay by rememberSaveable { mutableStateOf(base.purchaseDay) }
    var initialKm by rememberSaveable { mutableStateOf(base.initialOdometer?.toString() ?: "") }
    var fuel by rememberSaveable { mutableStateOf(base.fuel) }
    var transmission by rememberSaveable { mutableStateOf(base.transmission) }
    var photo by rememberSaveable { mutableStateOf(base.photo) }
    var notes by rememberSaveable { mutableStateOf(base.notes) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val currentYear = LocalDate.now().year
    val yearValue = year.toIntOrNull()
    val yearError = year.isNotBlank() && (yearValue == null || yearValue < 1900 || yearValue > currentYear + 1)

    // Foto baru yang belum disimpan dihapus jika form dibatalkan
    fun discard() {
        if (photo.isNotBlank() && photo != base.photo) AttachmentStore.cleaner(context).delete(listOf(photo))
        nav.back()
    }
    BackHandler { discard() }

    ScreenScaffold(title = if (isNew) "Tambah kendaraan" else "Ubah kendaraan", onBack = ::discard) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Identitas") {
                TextInput(brand, { brand = it }, "Merek *", isError = tried && brand.isBlank())
                TextInput(model, { model = it }, "Model *", isError = tried && model.isBlank())
                TextInput(variant, { variant = it }, "Tipe/varian")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInputPlain(year, { year = it.take(4) }, "Tahun", Modifier.weight(1f), isError = yearError)
                    TextInput(plate, { plate = it.uppercase() }, "Nomor polisi", Modifier.weight(1.4f))
                }
                TextInput(color, { color = it }, "Warna")
                Dropdown("Bahan bakar", FuelType.entries, fuel, { it.label }, { fuel = it })
                Dropdown("Transmisi", Transmission.entries, transmission, { it.label }, { transmission = it })
            }
            SectionCard(title = "Data tambahan (opsional)") {
                TextInput(vin, { vin = it.uppercase() }, "Nomor rangka (VIN)")
                TextInput(engine, { engine = it.uppercase() }, "Nomor mesin")
                DateInput(purchaseDay, { purchaseDay = it }, "Tanggal pembelian", clearable = true)
                if (isNew) {
                    NumberInput(initialKm, { initialKm = it }, "Odometer saat ini", suffix = "km", supporting = "Dipakai sebagai acuan awal pengingat berbasis km")
                } else {
                    Text("Perbarui kilometer lewat menu Odometer agar riwayatnya tercatat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextInput(notes, { notes = it }, "Catatan kondisi awal, modifikasi, info penting", singleLine = false, minLines = 3)
            }
            SectionCard(title = "Foto kendaraan") {
                if (photo.isNotBlank()) {
                    Thumbnail(photo, "image/jpeg", Modifier.fillMaxWidth().size(180.dp).clip(RoundedCornerShape(12.dp)))
                    TextButton(onClick = {
                        if (photo != base.photo) AttachmentStore.cleaner(context).delete(listOf(photo))
                        photo = ""
                    }) { Text("Hapus foto") }
                }
                AttachmentPicker(onImported = { imported ->
                    if (photo.isNotBlank() && photo != base.photo) AttachmentStore.cleaner(context).delete(listOf(photo))
                    photo = imported.fileName
                }, imagesOnly = true)
            }
            Button(
                onClick = onClick@{
                    tried = true
                    if (brand.isBlank() || model.isBlank() || yearError) {
                        actions.message("Lengkapi merek & model, dan periksa tahun")
                        return@onClick
                    }
                    val v = base.copy(
                        brand = brand.trim(), model = model.trim(), variant = variant.trim(), year = yearValue,
                        plate = plate.trim(), color = color.trim(), vin = vin.trim(), engineNumber = engine.trim(),
                        purchaseDay = purchaseDay, initialOdometer = if (isNew) Fmt.parseDigits(initialKm) else base.initialOdometer,
                        fuel = fuel, transmission = transmission, photo = photo, notes = notes.trim(),
                    )
                    val oldPhoto = base.photo
                    actions.run(if (isNew) "Kendaraan ditambahkan" else "Perubahan disimpan", onDone = {
                        if (oldPhoto.isNotBlank() && oldPhoto != v.photo) AttachmentStore.cleaner(context).delete(listOf(oldPhoto))
                        nav.back()
                    }) {
                        saveVehicle(v, isNew)
                        if (isNew) actions.settings.update { it.copy(selectedVehicleId = v.id) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simpan") }
        }
    }
}

/** Input angka tanpa pemisah ribuan (tahun). */
@Composable
private fun NumberInputPlain(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    TextInput(value, { onChange(it.filter(Char::isDigit)) }, label, modifier, isError = isError, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
}

// ---------------------------------------------------------------- detail

@Composable
fun VehicleDetailScreen(data: AppData, id: String, nav: Nav) {
    val vehicle = data.vehicleById[id]
    val actions = LocalActions.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var odometer by rememberSaveable { mutableStateOf(false) }
    val today = remember { Fmt.today() }

    if (vehicle == null) {
        ScreenScaffold("Kendaraan", onBack = nav::back) { pad -> EmptyState(Icons.Default.DirectionsCar, "Data tidak ditemukan", "Kendaraan mungkin sudah dihapus.", Modifier.padding(pad)) }
        return
    }

    ScreenScaffold(
        title = vehicle.displayName,
        subtitle = vehicle.plate.ifBlank { null },
        onBack = nav::back,
        actions = {
            IconButton(onClick = { nav.editVehicle(vehicle.id) }) { Icon(Icons.Default.Edit, "Ubah") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Laporan PDF") },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                    onClick = { menu = false; nav.report(vehicle.id) },
                )
                DropdownMenuItem(
                    text = { Text(if (vehicle.archived) "Aktifkan kembali" else "Arsipkan (dijual/tidak dipakai)") },
                    leadingIcon = { Icon(if (vehicle.archived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                    onClick = {
                        menu = false
                        actions.run(if (vehicle.archived) "Kendaraan diaktifkan" else "Kendaraan diarsipkan; riwayat tetap tersimpan") { setArchived(vehicle, !vehicle.archived) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Hapus kendaraan", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; confirmDelete = true },
                )
            }
        },
    ) { pad ->
        val odo = data.currentOdometer(vehicle.id)
        val components = data.componentStates(vehicle.id, today)
        val visits = data.visits.filter { it.vehicleId == vehicle.id }.sortedByDescending { it.day }
        val inspections = data.inspections.filter { it.vehicleId == vehicle.id }.sortedByDescending { it.day }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    VehicleSummaryRow(data, vehicle)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { odometer = true }) {
                            Icon(Icons.Default.Speed, null)
                            Text("  Update km")
                        }
                        OutlinedButton(onClick = { nav.odometer(vehicle.id) }) { Text("Riwayat km") }
                    }
                }
            }
            item {
                SectionCard(title = "Profil") {
                    InfoRow("Merek / model", "${vehicle.brand} ${vehicle.model} ${vehicle.variant}".trim())
                    InfoRow("Tahun", vehicle.year?.toString() ?: "–")
                    InfoRow("Nomor polisi", vehicle.plate.ifBlank { "–" })
                    InfoRow("Warna", vehicle.color.ifBlank { "–" })
                    InfoRow("Bahan bakar", vehicle.fuel.label)
                    InfoRow("Transmisi", vehicle.transmission.label)
                    InfoRow("Nomor rangka", vehicle.vin.ifBlank { "–" })
                    InfoRow("Nomor mesin", vehicle.engineNumber.ifBlank { "–" })
                    InfoRow("Tanggal beli", Fmt.date(vehicle.purchaseDay))
                    InfoRow("Odometer awal", vehicle.initialOdometer?.let { Fmt.km(it) } ?: "–")
                    InfoRow("Odometer terkini", odo?.let { "${Fmt.km(it.km)} (${Fmt.date(it.day)})" } ?: "–")
                    if (vehicle.notes.isNotBlank()) InfoRow("Catatan", vehicle.notes)
                }
            }
            item {
                SectionCard(
                    title = "Komponen (${components.size})",
                    action = { TextButton(onClick = { nav.editComponent(vehicleId = vehicle.id) }) { Text("Tambah") } },
                ) {
                    if (components.isEmpty()) {
                        Text(
                            "Belum ada komponen. Komponen otomatis tercatat saat Anda mencatat penggantian (mis. aki, ban) di servis, atau tambahkan manual.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    components.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickableRow { nav.component(c.component.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.component.name, fontWeight = FontWeight.Medium)
                                Text(
                                    c.current?.let { cur -> "Dipasang ${Fmt.date(cur.installedDay)} · ${Fmt.km(cur.installedKm)}" } ?: "Tidak terpasang",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val st = c.info?.status
                            Pill(c.conditionLabel, if (st != null && st.needsAttention) statusColor(st) else if (c.current != null) Success else Warning)
                        }
                    }
                }
            }
            item {
                SectionCard(
                    title = "Servis & perbaikan (${visits.size})",
                    action = { TextButton(onClick = { nav.editVisit(vehicleId = vehicle.id) }) { Text("Catat") } },
                ) {
                    if (visits.isEmpty()) Text("Belum ada catatan servis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visits.take(5).forEachIndexed { i, v ->
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickableRow { nav.visit(v.id) }) {
                            Column(Modifier.weight(1f)) {
                                Text("${Fmt.date(v.day)} · ${v.type.label}", fontWeight = FontWeight.Medium)
                                Text(
                                    (data.tasksByVisit[v.id] ?: emptyList()).joinToString(", ") { it.name }.ifBlank { v.workshopName },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                            Text(Fmt.rupiah(data.visitCost(v).total), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (visits.size > 5) TextButton(onClick = { nav.tab("history") }) { Text("Lihat semua di Riwayat") }
                }
            }
            item {
                SectionCard(
                    title = "Inspeksi (${inspections.size})",
                    action = { TextButton(onClick = { nav.editInspection(vehicleId = vehicle.id) }) { Text("Mulai") } },
                ) {
                    if (inspections.isEmpty()) Text("Lakukan pemeriksaan mandiri: ban, lampu, rem, cairan, wiper, kebocoran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inspections.take(3).forEach { r ->
                        val res = data.resultsByInspection[r.id] ?: emptyList()
                        val issues = res.count { it.status == CheckStatus.PROBLEM || it.status == CheckStatus.ATTENTION }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickableRow { nav.inspection(r.id) }) {
                            Text("${Fmt.date(r.day)} · ${Fmt.km(r.odometer)}", Modifier.weight(1f))
                            Pill(if (issues == 0) "Semua baik" else "$issues perlu tindakan", if (issues == 0) Success else Warning)
                        }
                    }
                }
            }
        }
    }

    if (odometer) OdometerDialog(data, vehicle.id, onDismiss = { odometer = false })
    if (confirmDelete) DeleteVehicleDialog(data, vehicle, onDismiss = { confirmDelete = false }) {
        actions.run("Kendaraan dan seluruh catatannya dihapus", onDone = nav::back) { deleteVehicle(vehicle) }
    }
}

/** Penghapusan kendaraan wajib dikonfirmasi dengan mengetik nomor polisi/nama. */
@Composable
private fun DeleteVehicleDialog(data: AppData, v: Vehicle, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val keyword = v.plate.ifBlank { v.model }.trim()
    var typed by remember { mutableStateOf("") }
    val visits = data.visits.count { it.vehicleId == v.id }
    val reminders = data.reminders.count { it.vehicleId == v.id }
    val files = data.attachments.count { it.vehicleId == v.id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus ${v.displayName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Akan terhapus permanen: $visits kunjungan servis, $reminders pengingat, $files lampiran, serta riwayat odometer, komponen, pengeluaran, dan inspeksi kendaraan ini.")
                Text("Jika mobil sudah dijual, pertimbangkan Arsipkan atau buat laporan PDF/backup terlebih dahulu.", style = MaterialTheme.typography.bodySmall)
                TextInput(typed, { typed = it }, "Ketik \"$keyword\" untuk konfirmasi")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(); onConfirm() },
                enabled = typed.trim().equals(keyword, ignoreCase = true),
            ) { Text("Hapus permanen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

// ---------------------------------------------------------------- odometer

/** Dialog update kilometer dengan peringatan bila nilainya tidak masuk akal (US-02). */
@Composable
fun OdometerDialog(data: AppData, vehicleId: String, onDismiss: () -> Unit, existing: OdometerLog? = null) {
    val actions = LocalActions.current
    val context = LocalContext.current
    var km by rememberSaveable { mutableStateOf(existing?.km?.toString() ?: "") }
    var day by rememberSaveable { mutableStateOf(existing?.day ?: Fmt.today()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val value = Fmt.parseDigits(km)
    val warning = value?.let { data.odometerWarning(vehicleId, day, it, ignoreRefId = existing?.id) }
    val last = data.currentOdometer(vehicleId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Update kilometer" else "Ubah catatan km") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (last != null) Text("Terakhir: ${Fmt.km(last.km)} (${Fmt.date(last.day)})", style = MaterialTheme.typography.bodySmall)
                NumberInput(km, { km = it; acknowledged = false }, "Odometer", suffix = "km", isError = warning != null && !acknowledged)
                DateInput(day, { if (it != null) { day = it; acknowledged = false } }, "Tanggal")
                TextInput(note, { note = it }, "Catatan (opsional)")
                if (warning != null) Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null,
                onClick = onClick@{
                    if (warning != null && !acknowledged) {
                        acknowledged = true // klik kedua = simpan tetap (mis. koreksi data lama)
                        return@onClick
                    }
                    val log = (existing ?: OdometerLog(vehicleId = vehicleId, day = day, km = value!!)).copy(day = day, km = value!!, note = note.trim())
                    actions.run("Kilometer disimpan", onDone = {
                        Reminders.checkNow(context) // status reminder berbasis km ikut diperbarui
                        onDismiss()
                    }) { saveOdometer(log) }
                },
            ) { Text(if (warning != null && !acknowledged) "Periksa" else if (warning != null) "Simpan tetap" else "Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun OdometerScreen(data: AppData, vehicleId: String, nav: Nav) {
    val vehicle = data.vehicleById[vehicleId]
    val actions = LocalActions.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OdometerLog?>(null) }
    var deleting by remember { mutableStateOf<OdometerLog?>(null) }
    val readings = data.readings(vehicleId).reversed()

    ScreenScaffold(
        title = "Riwayat odometer",
        subtitle = vehicle?.let { it.plate.ifBlank { it.displayName } },
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Speed, null) }, text = { Text("Update km") })
        },
    ) { pad ->
        if (readings.isEmpty()) {
            EmptyState(Icons.Default.Speed, "Belum ada catatan km", "Catat odometer secara berkala agar pengingat berbasis kilometer akurat.", Modifier.padding(pad))
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(readings, key = { "${it.source}-${it.logId ?: it.refId}-${it.day}-${it.km}" }) { r ->
                    val log = r.logId?.let { lid -> data.odometerLogs.firstOrNull { it.id == lid } }
                    val ref = r.refId
                    val open: (() -> Unit)? = when {
                        log != null -> fun() { editing = log }
                        ref == null -> null
                        r.source == OdometerSource.SERVICE -> fun() { nav.visit(ref) }
                        r.source == OdometerSource.INSPECTION -> fun() { nav.inspection(ref) }
                        r.source == OdometerSource.EXPENSE -> fun() { nav.editExpense(ref) }
                        else -> null
                    }
                    SectionCard(onClick = open) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.km(r.km), fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(Fmt.date(r.day), r.source.label, r.note).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (log != null) {
                                IconButton(onClick = { deleting = log }) { Icon(Icons.Default.Delete, "Hapus") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) OdometerDialog(data, vehicleId, onDismiss = { adding = false })
    editing?.let { OdometerDialog(data, vehicleId, onDismiss = { editing = null }, existing = it) }
    deleting?.let { log ->
        ConfirmDialog(
            "Hapus catatan km?", "${Fmt.km(log.km)} pada ${Fmt.date(log.day)} akan dihapus. Status pengingat dihitung ulang.", "Hapus",
            onConfirm = { actions.run("Catatan dihapus") { deleteOdometer(log) } },
            onDismiss = { deleting = null }, destructive = true,
        )
    }
}
