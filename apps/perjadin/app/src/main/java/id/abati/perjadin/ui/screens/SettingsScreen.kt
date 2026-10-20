package id.abati.perjadin.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<Category?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray(Charsets.UTF_8)) }
            Toast.makeText(context, "Backup tersimpan", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal menyimpan backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            pendingImport = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membaca file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun saveProfile() {
        repo.saveProfile(
            Profile(
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

            SectionTitle("Data & Backup", subtitle = "Data hanya tersimpan di HP ini. Backup rutin agar aman.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Ekspor Backup", Icons.Filled.CloudUpload, {
                    exportLauncher.launch("perjadin-backup-${LocalDate.now()}.json")
                }, Modifier.weight(1f))
                PillButton("Impor Backup", Icons.Filled.CloudDownload, {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }, Modifier.weight(1f))
            }
            PillButton("Tambah Data Contoh", Icons.Filled.Science, {
                repo.loadDemo()
                Toast.makeText(context, "1 perjalanan contoh ditambahkan", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth())
            PillButton("Lihat Tutorial", Icons.Filled.PlayCircle, onTutorial, Modifier.fillMaxWidth())
            PillButton("Hapus Semua Data", Icons.Filled.DeleteForever, { confirmClear = true }, Modifier.fillMaxWidth())

            SectionTitle("Tentang")
            Text(
                "Perjadin — Catatan Perjalanan Dinas\n${data.trips.size} perjalanan tersimpan · ${Fmt.date(Fmt.today())}\n" +
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

    pendingImport?.let { text ->
        ConfirmDialog(
            title = "Pulihkan dari backup?",
            message = "Data saat ini akan DIGANTI dengan isi file backup. Lanjutkan?",
            confirmText = "Pulihkan",
            destructive = false,
            onConfirm = {
                try {
                    repo.importJson(text)
                    val np = repo.state.value.profile
                    name = np.name; employeeId = np.employeeId; position = np.position; department = np.department
                    company = np.company; approverName = np.approverName; approverTitle = np.approverTitle
                    Toast.makeText(context, "Backup berhasil dipulihkan", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "File backup tidak valid", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}
