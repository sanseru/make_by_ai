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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Profile
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.report.PdfReport
import id.abati.perjadin.report.ShareHelper
import id.abati.perjadin.ui.components.InfoRow
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.ui.components.TipCard
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.ui.theme.Success
import id.abati.perjadin.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ShareTarget { ANY, WHATSAPP, EMAIL, VIEW, SAVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    trip: Trip,
    profile: Profile,
    categories: List<Category>,
    onBack: () -> Unit,
    onMarkReported: (TripStatus) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeBudget by rememberSaveable { mutableStateOf(true) }
    var includeExpenses by rememberSaveable { mutableStateOf(true) }
    var includeSignature by rememberSaveable { mutableStateOf(true) }
    var includeNotes by rememberSaveable { mutableStateOf(true) }
    var includePhotos by rememberSaveable { mutableStateOf(true) }
    val photoCount = trip.expenses.count { it.photo.isNotBlank() }
    var busy by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<File?>(null) }
    var lastShared by remember { mutableStateOf(false) }

    val subject = "Laporan Perjalanan Dinas ${trip.odrNumber}"
    val message = buildString {
        append("Yth. Bapak/Ibu,\n\nBerikut saya lampirkan laporan pertanggungjawaban perjalanan dinas:\n")
        append("• No. ODR: ${trip.odrNumber}\n")
        append("• Keperluan: ${trip.purpose}\n")
        append("• Tujuan: ${trip.route.ifBlank { trip.destination }}\n")
        append("• Tanggal: ${Fmt.range(trip.startDay, trip.endDay)}\n")
        append("• Total anggaran: ${Fmt.rupiah(trip.totalBudget)}\n")
        append("• Total realisasi: ${Fmt.rupiah(trip.totalActual)}\n")
        append("\nTerima kasih.\n${trip.travelerName.ifBlank { profile.name }}")
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingSave
        pendingSave = null
        if (uri != null && file != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                Toast.makeText(context, "PDF tersimpan", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun export(target: ShareTarget) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    PdfReport.generate(
                        context, trip, profile, categories,
                        PdfReport.Options(includeBudget, includeExpenses, includeSignature, includeNotes, includePhotos),
                    )
                }
                when (target) {
                    ShareTarget.ANY -> ShareHelper.shareAny(context, file, subject, message)
                    ShareTarget.WHATSAPP -> ShareHelper.shareWhatsApp(context, file, subject, message)
                    ShareTarget.EMAIL -> ShareHelper.shareEmail(context, file, subject, message)
                    ShareTarget.VIEW -> ShareHelper.view(context, file)
                    ShareTarget.SAVE -> {
                        pendingSave = file
                        saveLauncher.launch(file.name)
                    }
                }
                if (target != ShareTarget.VIEW && target != ShareTarget.SAVE) lastShared = true
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Laporan PDF", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Pratinjau isi laporan
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = Danger.copy(alpha = 0.12f)) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = Danger, modifier = Modifier.padding(10.dp).size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(PdfReport.fileName(trip), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Ukuran A4 · siap dicetak / dikirim", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        InfoRow("No. ODR", trip.odrNumber)
                        InfoRow("Pelaksana", trip.travelerName)
                        InfoRow("Tanggal", Fmt.range(trip.startDay, trip.endDay))
                        InfoRow("Total anggaran", Fmt.rupiah(trip.totalBudget))
                        InfoRow("Total realisasi", Fmt.rupiah(trip.totalActual))
                        InfoRow(
                            if (trip.remaining >= 0) "Sisa anggaran" else "Melebihi anggaran",
                            Fmt.rupiah(kotlin.math.abs(trip.remaining)),
                            valueColor = if (trip.remaining >= 0) Success else Danger,
                        )
                        InfoRow("Jumlah transaksi", "${trip.expenses.size} pengeluaran")
                    }
                }

                if (!profile.isComplete || profile.approverName.isBlank()) {
                    TipCard(
                        "Nama perusahaan, jabatan, dan nama atasan (penyetuju) belum lengkap. Lengkapi di Pengaturan agar kop & kolom tanda tangan terisi rapi.",
                        title = "Profil belum lengkap",
                    )
                    TextButton(onClick = onOpenSettings) { Text("Buka Pengaturan Profil") }
                }

                SectionTitle("Isi Laporan")
                OptionSwitch("Rincian anggaran", "Daftar plafon per item", includeBudget) { includeBudget = it }
                OptionSwitch("Rincian pengeluaran", "Tabel transaksi + no. bukti", includeExpenses) { includeExpenses = it }
                OptionSwitch("Catatan perjalanan", "Tampilkan kolom catatan", includeNotes) { includeNotes = it }
                OptionSwitch("Kolom tanda tangan", "Pelapor & atasan penyetuju", includeSignature) { includeSignature = it }
                OptionSwitch(
                    "Lampiran foto struk",
                    if (photoCount > 0) "$photoCount foto, di halaman terpisah" else "Belum ada foto struk",
                    includePhotos && photoCount > 0,
                ) { includePhotos = it }

                SectionTitle("Kirim / Simpan", subtitle = "PDF dibuat ulang setiap kali tombol ditekan, sesuai data terbaru")
                Button(
                    onClick = { export(ShareTarget.WHATSAPP) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White),
                ) { ButtonContent(Icons.Filled.Chat, "Kirim via WhatsApp") }
                Button(onClick = { export(ShareTarget.EMAIL) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    ButtonContent(Icons.Filled.Email, "Kirim via Email")
                }
                FilledTonalButton(onClick = { export(ShareTarget.ANY) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    ButtonContent(Icons.Filled.Share, "Bagikan ke Aplikasi Lain")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { export(ShareTarget.VIEW) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        ButtonContent(Icons.Filled.Visibility, "Lihat")
                    }
                    OutlinedButton(onClick = { export(ShareTarget.SAVE) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        ButtonContent(Icons.Filled.Save, "Simpan File")
                    }
                }

                if (lastShared && trip.status != TripStatus.REPORTED) {
                    Surface(color = Success.copy(alpha = 0.1f), shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Success)
                            Spacer(Modifier.width(8.dp))
                            Text("Sudah dikirim ke kantor?", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onMarkReported(TripStatus.REPORTED) }) { Text("Tandai Dilaporkan") }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (busy) {
                Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.medium, shadowElevation = 6.dp) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Membuat PDF…")
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}

@Composable
private fun OptionSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
