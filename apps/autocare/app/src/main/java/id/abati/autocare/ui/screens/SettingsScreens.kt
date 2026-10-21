package id.abati.autocare.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.autocare.BuildConfig
import id.abati.autocare.backup.BackupArchive
import id.abati.autocare.backup.BackupService
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.MaintenanceCategory
import id.abati.autocare.data.MaintenanceItem
import id.abati.autocare.data.Repository
import id.abati.autocare.data.Workshop
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.ui.Nav
import id.abati.autocare.ui.components.ChoiceChips
import id.abati.autocare.ui.components.ConfirmDialog
import id.abati.autocare.ui.components.Dropdown
import id.abati.autocare.ui.components.EmptyState
import id.abati.autocare.ui.components.InfoRow
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.NumberInput
import id.abati.autocare.ui.components.Pill
import id.abati.autocare.ui.components.ScreenScaffold
import id.abati.autocare.ui.components.SectionCard
import id.abati.autocare.ui.components.TextInput
import id.abati.autocare.ui.theme.Warning
import id.abati.autocare.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

// ---------------------------------------------------------------- pengaturan

@Composable
fun SettingsScreen(data: AppData, settings: AppSettings, nav: Nav) {
    val actions = LocalActions.current
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(Reminders.hasPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (!granted) actions.message("Izin notifikasi ditolak. Aktifkan lewat Pengaturan Android bila ingin menerima pengingat.")
    }

    fun update(transform: (AppSettings) -> AppSettings, reschedule: Boolean = false) {
        actions.launch {
            actions.settings.update(transform)
            if (reschedule) {
                val s = actions.settings.current()
                Reminders.schedule(context, s.notificationsEnabled, s.notifyHour, replace = true)
            }
        }
    }

    ScreenScaffold(title = "Pengaturan", onBack = nav::back) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Notifikasi pengingat") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifikasi lokal")
                        Text("Berjalan tanpa internet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.notificationsEnabled, onCheckedChange = { on ->
                        update({ it.copy(notificationsEnabled = on) }, reschedule = true)
                        if (on && !permitted && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                }
                if (settings.notificationsEnabled && !permitted) {
                    Text("Izin notifikasi belum diberikan; pengingat tetap tampil di aplikasi.", color = Warning, style = MaterialTheme.typography.bodySmall)
                    if (Build.VERSION.SDK_INT >= 33) {
                        TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Izinkan notifikasi") }
                    }
                }
                Dropdown(
                    "Jam pengecekan harian", (5..22).toList(), settings.notifyHour, { "%02d.00".format(it) },
                    { h -> update({ it.copy(notifyHour = h) }, reschedule = true) },
                )
                Text("Ingatkan update odometer bila tidak dicatat selama", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(0, 14, 30, 60), settings.odometerReminderDays, { if (it == 0) "Nonaktif" else "$it hari" }, { d -> update({ it.copy(odometerReminderDays = d) }) })
                TextButton(onClick = { Reminders.checkNow(context); actions.message("Pengecekan pengingat dijalankan") }) { Text("Periksa pengingat sekarang") }
            }
            SectionCard(title = "Data") {
                NavRow(Icons.Default.Backup, "Backup & restore", backupLabel(settings)) { nav.backup() }
                HorizontalDivider()
                NavRow(Icons.Default.Category, "Katalog perawatan", "${data.categories.size} kategori · ${data.items.size} item") { nav.catalog() }
                HorizontalDivider()
                NavRow(Icons.Default.Store, "Bengkel", "${data.workshops.size} bengkel tersimpan") { nav.workshops() }
                Text("Ingatkan backup setiap", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(0, 7, 30, 90), settings.backupReminderDays, { if (it == 0) "Nonaktif" else "$it hari" }, { d -> update({ it.copy(backupReminderDays = d) }) })
            }
            SectionCard(title = "Tentang") {
                InfoRow("Aplikasi", "AutoCare ${BuildConfig.VERSION_NAME}")
                InfoRow("Penyimpanan", "Lokal di perangkat, tanpa akun & server")
                Text(
                    "Interval perawatan bawaan hanyalah titik awal. Selalu ikuti rekomendasi pabrikan dan kondisi kendaraan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun backupLabel(s: AppSettings): String =
    if (s.lastBackupAt <= 0) "Belum pernah backup" else "Terakhir ${Fmt.date(Instant.ofEpochMilli(s.lastBackupAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())}"

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------- backup & restore

@Composable
fun BackupScreen(data: AppData, settings: AppSettings, nav: Nav) {
    val context = LocalContext.current
    val actions = LocalActions.current
    val scope = rememberCoroutineScope()
    val service = remember { BackupService(context.applicationContext) }
    var busy by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupArchive.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = "Membuat backup…"
            scope.launch {
                try {
                    val r = service.export(uri)
                    actions.message("Backup tersimpan: ${r.attachmentCount} lampiran" + if (r.missingAttachments > 0) " (${r.missingAttachments} file hilang)" else "")
                } catch (e: Exception) {
                    error = "Backup gagal: ${e.message}"
                } finally {
                    busy = null
                }
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = "Memeriksa backup…"
            scope.launch {
                try {
                    preview = service.inspect(uri)
                } catch (e: BackupArchive.BackupException) {
                    error = e.message
                } catch (e: Exception) {
                    error = "File tidak bisa dibaca: ${e.message}"
                } finally {
                    busy = null
                }
            }
        }
    }

    ScreenScaffold(title = "Backup & restore", onBack = nav::back) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Backup lengkap") {
                Text(
                    "Satu file .zip berisi seluruh kendaraan, servis, pekerjaan, pengingat, odometer, komponen, biaya, bengkel, inspeksi, beserta foto nota & dokumen. Simpan di Google Drive, email, atau komputer.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                InfoRow("Backup terakhir", backupLabel(settings).removePrefix("Terakhir "))
                InfoRow("Isi saat ini", "${data.vehicles.size} kendaraan · ${data.visits.size} servis · ${data.attachments.size} lampiran")
                Button(
                    enabled = busy == null,
                    onClick = { create.launch(BackupArchive.fileName(System.currentTimeMillis())) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Backup, null)
                    Text("  Buat backup")
                }
            }
            SectionCard(title = "Pulihkan") {
                Text(
                    "File diperiksa terlebih dahulu (versi, checksum, kelengkapan, relasi data). Data aktif tidak diubah sebelum Anda mengonfirmasi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    enabled = busy == null,
                    onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Restore, null)
                    Text("  Pilih file backup")
                }
            }
            busy?.let { msg ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(msg)
                }
            }
            Text(
                "Laporan PDF bukan pengganti backup. Buat backup secara berkala, terutama sebelum ganti HP atau reset perangkat.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    preview?.let { p ->
        RestoreDialog(p, data, onDismiss = { preview = null }) { mode ->
            preview = null
            busy = "Memulihkan data…"
            scope.launch {
                try {
                    service.apply(p, mode)
                    actions.message("Data berhasil dipulihkan")
                } catch (e: Exception) {
                    error = "Restore gagal, data lama tidak diubah: ${e.message}"
                } finally {
                    busy = null
                }
            }
        }
    }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Tidak bisa diproses") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun RestoreDialog(p: BackupArchive.Preview, current: AppData, onDismiss: () -> Unit, onApply: (Repository.RestoreMode) -> Unit) {
    var mode by remember { mutableStateOf(if (current.isEmpty) Repository.RestoreMode.REPLACE else Repository.RestoreMode.MERGE) }
    var confirmed by remember { mutableStateOf(false) }
    val exported = p.manifest.exportedAt.takeIf { it > 0 }?.let {
        Fmt.date(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
    } ?: "–"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pulihkan backup?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Dibuat", "$exported · v${p.manifest.appVersion.ifBlank { "?" }}")
                InfoRow("Isi", "${p.vehicleCount} kendaraan · ${p.visitCount} servis · ${p.data.reminders.size} pengingat · ${p.attachmentCount} lampiran")
                p.warnings.forEach { Text("⚠ $it", color = Warning, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                ChoiceChips(
                    Repository.RestoreMode.entries, mode,
                    { if (it == Repository.RestoreMode.MERGE) "Gabungkan" else "Ganti semua" }, { mode = it; confirmed = false },
                )
                Text(
                    if (mode == Repository.RestoreMode.MERGE) {
                        "Menambahkan data dari backup yang belum ada di HP ini. Data yang sama tidak digandakan, data yang ada tidak diubah."
                    } else {
                        "SEMUA data di HP ini (${current.vehicles.size} kendaraan, ${current.visits.size} servis) akan diganti dengan isi backup."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (mode == Repository.RestoreMode.REPLACE && !current.isEmpty) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("Saya mengerti data saat ini akan diganti", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = mode == Repository.RestoreMode.MERGE || current.isEmpty || confirmed,
                onClick = { onApply(mode) },
            ) { Text("Pulihkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- katalog

@Composable
fun CatalogScreen(data: AppData, nav: Nav) {
    val actions = LocalActions.current
    var editing by remember { mutableStateOf<MaintenanceItem?>(null) }
    var creatingIn by remember { mutableStateOf<String?>(null) }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MaintenanceItem?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "Katalog perawatan",
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { newCategory = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Kategori") })
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Interval bawaan hanyalah saran awal dan dapat diubah. Tambahkan kategori/item sendiri untuk modifikasi, aksesori, atau pekerjaan khusus.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(data.categories, key = { it.id }) { c ->
                val items = data.items.filter { it.categoryId == c.id }
                SectionCard(
                    title = "${c.name} (${items.size})",
                    onClick = { expanded = if (expanded == c.id) null else c.id },
                    action = { TextButton(onClick = { creatingIn = c.id }) { Text("Tambah") } },
                ) {
                    if (expanded == c.id) {
                        items.forEach { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name)
                                    Text(
                                        listOfNotNull(intervalLabel(item).trim().removeSurrounding("(", ")").ifBlank { null }, if (item.isComponent) "komponen" else null)
                                            .joinToString(" · ").ifBlank { "Tanpa interval" },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editing = item }) { Icon(Icons.Default.Edit, "Ubah") }
                                if (!item.builtIn) IconButton(onClick = { deleting = item }) { Icon(Icons.Default.Delete, "Hapus") }
                            }
                        }
                        if (!c.builtIn && items.isEmpty()) {
                            TextButton(onClick = { actions.run("Kategori dihapus") { deleteCategory(c) } }) { Text("Hapus kategori kosong") }
                        }
                    } else {
                        Text(items.take(4).joinToString(", ") { it.name } + if (items.size > 4) ", …" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { ItemDialog(data, it, it.categoryId, onDismiss = { editing = null }) }
    creatingIn?.let { ItemDialog(data, null, it, onDismiss = { creatingIn = null }) }
    if (newCategory) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newCategory = false },
            title = { Text("Kategori baru") },
            text = { TextInput(name, { name = it }, "Nama kategori") },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newCategory = false
                    actions.run("Kategori ditambahkan") { saveCategory(MaintenanceCategory(name = name.trim(), sortOrder = 100 + data.categories.size)) }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { newCategory = false }) { Text("Batal") } },
        )
    }
    deleting?.let { item ->
        ConfirmDialog(
            "Hapus item?", "\"${item.name}\" dihapus dari katalog. Pengingat & riwayat yang memakai nama ini tetap tersimpan.", "Hapus",
            onConfirm = { actions.run("Item dihapus") { deleteItem(item) } }, onDismiss = { deleting = null }, destructive = true,
        )
    }
}

@Composable
private fun ItemDialog(data: AppData, item: MaintenanceItem?, categoryId: String, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    var name by remember { mutableStateOf(item?.name ?: "") }
    var cat by remember { mutableStateOf(categoryId) }
    var km by remember { mutableStateOf(item?.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(item?.intervalMonths?.toString() ?: "") }
    var component by remember { mutableStateOf(item?.isComponent ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Item baru" else "Ubah item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Nama")
                Dropdown("Kategori", data.categories.map { it.id }, cat, { id -> data.categoryById[id]?.name ?: "" }, { cat = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberInput(km, { km = it }, "Interval", Modifier.weight(1f), suffix = "km")
                    NumberInput(months, { months = it.take(3) }, "Interval", Modifier.weight(1f), suffix = "bln")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = component, onCheckedChange = { component = it })
                    Text("Komponen fisik (punya riwayat pemasangan)")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val saved = (item ?: MaintenanceItem(categoryId = cat, name = name)).copy(
                    categoryId = cat, name = name.trim(), intervalKm = Fmt.parseDigits(km)?.takeIf { it > 0 },
                    intervalMonths = months.toIntOrNull()?.takeIf { it > 0 }, isComponent = component,
                )
                onDismiss()
                actions.run("Katalog disimpan") { saveItem(saved) }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- bengkel

@Composable
fun WorkshopsScreen(data: AppData, nav: Nav) {
    val actions = LocalActions.current
    var editing by remember { mutableStateOf<Workshop?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Workshop?>(null) }

    ScreenScaffold(
        title = "Bengkel",
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Bengkel") })
        },
    ) { pad ->
        if (data.workshops.isEmpty()) {
            EmptyState(Icons.Default.Store, "Belum ada bengkel", "Bengkel otomatis tersimpan saat Anda mencatat servis, atau tambahkan manual.", Modifier.padding(pad))
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(data.workshops, key = { it.id }) { w ->
                    val visits = data.visits.count { it.workshopId == w.id || it.workshopName.equals(w.name, ignoreCase = true) }
                    SectionCard(onClick = { editing = w }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, fontWeight = FontWeight.SemiBold)
                                val info = listOf(w.phone, w.address).filter { it.isNotBlank() }.joinToString(" · ")
                                if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill("$visits kunjungan", MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { deleting = w }) { Icon(Icons.Default.Delete, "Hapus") }
                        }
                    }
                }
            }
        }
    }
    if (creating) WorkshopDialog(null, onDismiss = { creating = false })
    editing?.let { WorkshopDialog(it, onDismiss = { editing = null }) }
    deleting?.let { w ->
        ConfirmDialog(
            "Hapus bengkel?", "\"${w.name}\" dihapus dari daftar. Nama bengkel di riwayat servis tetap tersimpan.", "Hapus",
            onConfirm = { actions.run("Bengkel dihapus") { deleteWorkshop(w) } }, onDismiss = { deleting = null }, destructive = true,
        )
    }
}

@Composable
private fun WorkshopDialog(w: Workshop?, onDismiss: () -> Unit) {
    val actions = LocalActions.current
    var name by remember { mutableStateOf(w?.name ?: "") }
    var phone by remember { mutableStateOf(w?.phone ?: "") }
    var address by remember { mutableStateOf(w?.address ?: "") }
    var notes by remember { mutableStateOf(w?.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (w == null) "Bengkel baru" else "Ubah bengkel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Nama bengkel")
                TextInput(phone, { phone = it }, "Telepon/WA", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                TextInput(address, { address = it }, "Alamat", singleLine = false)
                TextInput(notes, { notes = it }, "Catatan", singleLine = false)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val saved = (w ?: Workshop(name = name)).copy(name = name.trim(), phone = phone.trim(), address = address.trim(), notes = notes.trim())
                onDismiss()
                actions.run("Bengkel disimpan") { saveWorkshop(saved) }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
