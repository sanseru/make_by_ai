package id.abati.perjadin.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.perjadin.backup.BackupManager
import id.abati.perjadin.backup.BackupService
import id.abati.perjadin.data.AppData
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.components.InfoRow
import id.abati.perjadin.ui.components.PillButton
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.ui.theme.Warning
import id.abati.perjadin.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatTime(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH.mm", Fmt.locale).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Ekspor backup lengkap (.zip) dan pemulihan dengan pratinjau.
 * Alur restore: pilih file → diperiksa (checksum, versi, validasi) → pratinjau → konfirmasi →
 * backup otomatis data saat ini → data & aset diganti. Gagal di tengah = data lama utuh.
 */
@Composable
fun BackupSection(data: AppData, repo: Repository, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupManager.Preview?>(null) }
    var previewIsUndo by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var undoFile by remember { mutableStateOf(BackupService.latestPreRestore(repo)) }

    fun inspect(load: () -> File, isUndo: Boolean) {
        busy = "Memeriksa backup…"
        scope.launch {
            try {
                preview = withContext(Dispatchers.IO) { BackupService.manager(context).inspect(load()) }
                previewIsUndo = isUndo
            } catch (e: BackupManager.BackupException) {
                error = e.message
            } catch (e: Exception) {
                error = "File tidak dapat dibaca sebagai backup Perjadin (${e.message})."
            } finally {
                busy = null
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = "Membuat backup…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) { BackupService.exportTo(context, repo, uri) }
                Toast.makeText(context, "Backup lengkap tersimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                error = "Gagal menyimpan backup: ${e.message}"
            } finally {
                busy = null
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspect({ BackupService.copyToCache(context, uri) }, isUndo = false)
    }

    // Status backup terakhir
    val last = data.lastBackupAt
    val stale = last == 0L || System.currentTimeMillis() - last > 14L * Fmt.DAY_MS
    Surface(
        color = if (stale && data.trips.isNotEmpty()) Warning.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            InfoRow("Backup terakhir", if (last == 0L) "Belum pernah" else formatTime(last))
            Text(
                "Backup .zip berisi semua perjalanan, pengeluaran, kategori, profil, pengaturan, foto struk, logo & tanda tangan. " +
                    "Simpan ke Google Drive / kirim ke diri sendiri agar aman saat HP hilang atau ganti HP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Ekspor Backup", Icons.Filled.CloudUpload, {
            exportLauncher.launch("Perjadin_Backup_${LocalDate.now()}.zip")
        }, Modifier.weight(1f))
        PillButton("Pulihkan", Icons.Filled.CloudDownload, {
            importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/plain", "*/*"))
        }, Modifier.weight(1f))
    }
    undoFile?.let { f ->
        PillButton("Batalkan restore terakhir (${formatTime(f.lastModified())})", Icons.Filled.History, {
            inspect({ f }, isUndo = true)
        }, Modifier.fillMaxWidth())
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(msg)
                }
            },
        )
    }

    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            icon = { Icon(Icons.Filled.Warning, null, tint = Danger) },
            title = { Text("Backup tidak dapat dipakai") },
            text = { Text("$msg\n\nData Anda saat ini tidak diubah.") },
            confirmButton = { TextButton(onClick = { error = null }) { Text("Tutup") } },
        )
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = {
                BackupService.manager(context).discard(p)
                preview = null
            },
            title = { Text(if (previewIsUndo) "Kembalikan data sebelum restore?" else "Pulihkan backup ini?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InfoRow("Jenis", if (p.isFullBackup) "Backup lengkap (.zip)" else "Backup lama (.json)")
                    if (p.exportedAt > 0) InfoRow("Dibuat", formatTime(p.exportedAt))
                    if (p.appVersion != "-") InfoRow("Versi aplikasi", p.appVersion)
                    InfoRow("Perjalanan", "${p.tripCount}")
                    InfoRow("Pengeluaran", "${p.expenseCount}")
                    if (p.isFullBackup) InfoRow("Foto struk", "${p.photoCount}")
                    p.warnings.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = Warning)
                    }
                    Text(
                        "Data di HP ini akan DIGANTI. Salinan data saat ini disimpan otomatis dan bisa dikembalikan lewat \"Batalkan restore terakhir\".",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    busy = "Memulihkan data…"
                    scope.launch {
                        try {
                            // Saat membatalkan restore, jangan buat backup "pre-restore" baru agar tidak menimpa riwayat
                            withContext(Dispatchers.IO) { BackupService.restore(context, repo, p, makeSafetyBackup = !previewIsUndo) }
                            onRestored()
                            undoFile = BackupService.latestPreRestore(repo)
                            Toast.makeText(
                                context,
                                "Berhasil: ${p.tripCount} perjalanan, ${p.expenseCount} pengeluaran dipulihkan",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (e: Exception) {
                            error = "Pemulihan gagal: ${e.message}"
                        } finally {
                            busy = null
                        }
                    }
                }) { Text("Pulihkan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    BackupService.manager(context).discard(p)
                    preview = null
                }) { Text("Batal") }
            },
        )
    }
}
