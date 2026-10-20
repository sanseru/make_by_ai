package id.abati.perjadin.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import android.Manifest
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import id.abati.perjadin.reminder.Reminders
import id.abati.perjadin.ui.components.DropdownField
import id.abati.perjadin.ui.components.SignatureDialog
import id.abati.perjadin.util.ReceiptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.Category
import id.abati.perjadin.ui.components.CategoryBadge
import id.abati.perjadin.ui.components.CategoryDialog
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.components.ConfirmDialog
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.PillButton
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.util.Fmt
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(data: AppData, repo: Repository, onBack: () -> Unit, onTutorial: () -> Unit) {
    val context = LocalContext.current
    val p = data.profile
    var name by rememberSaveable { mutableStateOf(p.name) }
    var employeeId by rememberSaveable { mutableStateOf(p.employeeId) }
    var position by rememberSaveable { mutableStateOf(p.position) }
    var department by rememberSaveable { mutableStateOf(p.department) }
    var company by rememberSaveable { mutableStateOf(p.company) }
    var approverName by rememberSaveable { mutableStateOf(p.approverName) }
    var approverTitle by rememberSaveable { mutableStateOf(p.approverTitle) }
    var confirmClear by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }


    // ---------- logo, tanda tangan, pengingat ----------
    val scope = rememberCoroutineScope()
    var showSignature by remember { mutableStateOf(false) }
    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val name = withContext(Dispatchers.IO) { ReceiptStore.saveLogo(context, uri) }
                val old = repo.state.value.profile
                ReceiptStore.deleteBranding(context, old.logoFile)
                repo.saveProfile(old.copy(logoFile = name))
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat logo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            repo.setReminder(true, data.reminderHour)
            Reminders.schedule(context, true, data.reminderHour, replace = true)
        } else {
            Toast.makeText(context, "Izin notifikasi ditolak. Aktifkan di Pengaturan HP > Aplikasi > Perjadin.", Toast.LENGTH_LONG).show()
        }
    }
    fun setReminder(enabled: Boolean, hour: Int) {
        if (enabled && !Reminders.hasPermission(context) && Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        repo.setReminder(enabled, hour)
        Reminders.schedule(context, enabled, hour, replace = true)
    }

    fun saveProfile() {
        // copy() agar logo & tanda tangan yang sudah tersimpan tidak terhapus
        repo.saveProfile(
            repo.state.value.profile.copy(
                name = name.trim(), employeeId = employeeId.trim(), position = position.trim(),
                department = department.trim(), company = company.trim(),
                approverName = approverName.trim(), approverTitle = approverTitle.trim(),
            ),
        )
        Toast.makeText(context, "Profil disimpan", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Profil Pegawai", subtitle = "Otomatis dipakai di form perjalanan baru & laporan PDF")
            FormField(name, { name = it }, "Nama Lengkap", "Contoh: Budi Santoso", leading = Icons.Filled.Person, capitalization = KeyboardCapitalization.Words)
            FormField(employeeId, { employeeId = it }, "NIK / No. Karyawan", "Contoh: EMP-00421", leading = Icons.Filled.Badge, capitalization = KeyboardCapitalization.Characters)
            FormField(position, { position = it }, "Jabatan", "Contoh: Supervisor Gudang", leading = Icons.Filled.Work, capitalization = KeyboardCapitalization.Words)
            FormField(department, { department = it }, "Departemen / Divisi", "Contoh: Operasional", leading = Icons.Filled.Business, capitalization = KeyboardCapitalization.Words)
            FormField(company, { company = it }, "Nama Perusahaan", "Contoh: PT Maju Bersama Nusantara", supporting = "Tampil sebagai kop laporan PDF", leading = Icons.Filled.Domain, capitalization = KeyboardCapitalization.Words)

            SectionTitle("Atasan / Penyetuju", subtitle = "Untuk kolom tanda tangan \"Disetujui oleh\"")
            FormField(approverName, { approverName = it }, "Nama Atasan", "Contoh: Siti Rahmawati", leading = Icons.Filled.SupervisorAccount, capitalization = KeyboardCapitalization.Words)
            FormField(approverTitle, { approverTitle = it }, "Jabatan Atasan", "Contoh: Manager Operasional", leading = Icons.Filled.Work, capitalization = KeyboardCapitalization.Words)
            Spacer(Modifier.height(4.dp))
            Button(onClick = ::saveProfile, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Simpan Profil") }

            SectionTitle("Logo & Tanda Tangan", subtitle = "Dicetak otomatis di laporan PDF")
            BrandingRow(
                title = "Logo perusahaan",
                subtitle = "Tampil di kop laporan. Gunakan PNG berlatar transparan agar rapi.",
                file = data.profile.logoFile,
                actionLabel = if (data.profile.logoFile.isBlank()) "Pilih Logo" else "Ganti",
                onAction = { logoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = {
                    ReceiptStore.deleteBranding(context, data.profile.logoFile)
                    repo.saveProfile(repo.state.value.profile.copy(logoFile = ""))
                },
            )
            BrandingRow(
                title = "Tanda tangan digital",
                subtitle = "Dicetak di kolom \"Dilaporkan oleh\".",
                file = data.profile.signatureFile,
                actionLabel = if (data.profile.signatureFile.isBlank()) "Buat" else "Ulangi",
                onAction = { showSignature = true },
                onRemove = {
                    ReceiptStore.deleteBranding(context, data.profile.signatureFile)
                    repo.saveProfile(repo.state.value.profile.copy(signatureFile = ""))
                },
            )

            SectionTitle("Pengingat", subtitle = "Notifikasi harian selama & setelah perjalanan dinas")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Pengingat harian", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "\"Sudah catat pengeluaran hari ini?\" saat dinas, dan \"Laporan belum dikirim\" setelah pulang.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = data.reminderEnabled, onCheckedChange = { setReminder(it, data.reminderHour) })
            }
            if (data.reminderEnabled) {
                DropdownField(
                    label = "Jam pengingat",
                    options = listOf(7, 12, 17, 19, 20, 21, 22),
                    selected = data.reminderHour,
                    optionLabel = { "%02d.00".format(it) },
                    onSelect = { setReminder(true, it) },
                    leading = Icons.Filled.Alarm,
                )
            }

            SectionTitle("Kategori Biaya", subtitle = "Ketuk untuk ubah nama, ikon & warna. Tambah sendiri mis. Bensin & Tol, Parkir")
            data.categories.forEach { c ->
                Surface(
                    onClick = { editCategory = c },
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(c, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (c.builtIn) "Bawaan · ketuk untuk ubah" else "Buatan Anda · ketuk untuk ubah",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Filled.Edit, "Ubah", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            PillButton("Tambah Kategori", Icons.Filled.Add, { newCategory = true }, Modifier.fillMaxWidth())

            SectionTitle("Data & Backup", subtitle = "Data hanya tersimpan di HP ini. Backup rutin agar aman saat ganti HP.")
            BackupSection(
                data = data,
                repo = repo,
                onRestored = {
                    val np = repo.state.value.profile
                    name = np.name; employeeId = np.employeeId; position = np.position; department = np.department
                    company = np.company; approverName = np.approverName; approverTitle = np.approverTitle
                },
            )
            PillButton("Tambah Data Contoh", Icons.Filled.Science, {
                repo.loadDemo()
                Toast.makeText(context, "1 perjalanan contoh ditambahkan", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth())
            PillButton("Lihat Tutorial", Icons.Filled.PlayCircle, onTutorial, Modifier.fillMaxWidth())
            PillButton("Hapus Semua Data", Icons.Filled.DeleteForever, { confirmClear = true }, Modifier.fillMaxWidth())

            SectionTitle("Tentang")
            Text(
                "Perjadin ${id.abati.perjadin.BuildConfig.VERSION_NAME} (build ${id.abati.perjadin.BuildConfig.VERSION_CODE}) — Catatan Perjalanan Dinas\n" +
                    "${data.trips.size} perjalanan tersimpan · format data v${id.abati.perjadin.data.DATA_VERSION}\n" +
                    "Seluruh data disimpan offline di perangkat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    val labels = data.categories.map { it.label.lowercase() }.toSet()
    if (newCategory) {
        CategoryDialog(category = null, existingLabels = labels, onDismiss = { newCategory = false }, onSave = repo::upsertCategory)
    }
    editCategory?.let { c ->
        CategoryDialog(
            category = c,
            existingLabels = labels,
            onDismiss = { editCategory = null },
            onSave = repo::upsertCategory,
            onDelete = {
                if (!repo.deleteCategory(c.id)) {
                    Toast.makeText(context, "Kategori masih dipakai di anggaran/pengeluaran (atau satu-satunya kategori), tidak bisa dihapus", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    if (showSignature) {
        SignatureDialog(
            onDismiss = { showSignature = false },
            onSave = { bmp ->
                val old = repo.state.value.profile
                val name = ReceiptStore.savePng(context, bmp, "signature")
                ReceiptStore.deleteBranding(context, old.signatureFile)
                repo.saveProfile(old.copy(signatureFile = name))
            },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Hapus semua data?",
            message = "Semua perjalanan, anggaran, pengeluaran, dan profil akan dihapus permanen. Disarankan ekspor backup terlebih dahulu.",
            confirmText = "Hapus Semua",
            onConfirm = {
                repo.clearAll()
                name = ""; employeeId = ""; position = ""; department = ""; company = ""; approverName = ""; approverTitle = ""
                Toast.makeText(context, "Semua data dihapus", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirmClear = false },
        )
    }

}

/** Baris pratinjau logo / tanda tangan dengan tombol ganti & hapus. */
@Composable
private fun BrandingRow(
    title: String,
    subtitle: String,
    file: String,
    actionLabel: String,
    onAction: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { ReceiptStore.loadBranding(context, file)?.asImageBitmap() }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Latar putih agar logo/tanda tangan gelap tetap terlihat di mode gelap
            Box(
                Modifier.size(64.dp).clip(MaterialTheme.shapes.small).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                val img = image
                if (img != null) {
                    Image(img, title, contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
                } else {
                    Text("–", color = Color.Gray)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                    if (file.isNotBlank()) TextButton(onClick = onRemove) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
