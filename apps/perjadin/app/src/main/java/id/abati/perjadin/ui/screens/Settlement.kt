package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Trip
import id.abati.perjadin.ui.components.DateField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.InfoRow
import id.abati.perjadin.ui.components.MoneyField
import id.abati.perjadin.ui.components.SectionTitle
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.ui.theme.Success
import id.abati.perjadin.ui.theme.Warning
import id.abati.perjadin.util.Fmt

/** Label arah penyelesaian: siapa membayar ke siapa. */
private fun directionLabel(trip: Trip): String = when {
    trip.amountDueToEmployee > 0 -> "Kantor membayar ke Anda (reimburse)"
    trip.amountDueToEmployee < 0 -> "Anda mengembalikan sisa uang muka"
    else -> "Tidak ada selisih"
}

/** Kartu ringkasan uang muka, realisasi, dan status reimburse/pengembalian. */
@Composable
fun SettlementCard(trip: Trip, onEdit: () -> Unit) {
    val due = trip.amountDueToEmployee
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Penyelesaian & Reimburse", subtitle = "Lacak uang yang belum kembali / harus dikembalikan")
            InfoRow("Uang muka diterima", Fmt.rupiah(trip.advance))
            InfoRow("Total realisasi", Fmt.rupiah(trip.totalActual))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InfoRow(
                directionLabel(trip),
                if (due == 0L) "Lunas" else Fmt.rupiah(kotlin.math.abs(due)),
                valueColor = when {
                    due > 0 -> Danger
                    due < 0 -> MaterialTheme.colorScheme.primary
                    else -> Success
                },
            )
            Spacer(Modifier.height(8.dp))

            val (icon, color, text) = when {
                trip.isSettled -> Triple(
                    Icons.Filled.CheckCircle, Success,
                    "Selesai ${Fmt.date(trip.settledDay)} · ${Fmt.rupiah(trip.settledAmount)}" +
                        if (trip.settledAmount != kotlin.math.abs(due)) " (beda ${Fmt.rupiah(kotlin.math.abs(due) - trip.settledAmount)})" else "",
                )
                trip.isSubmitted -> Triple(Icons.Filled.Send, Warning, "Diajukan ${Fmt.date(trip.submittedDay)} · menunggu diproses")
                due == 0L -> Triple(Icons.Filled.CheckCircle, Success, "Tidak ada yang perlu diselesaikan")
                else -> Triple(Icons.Filled.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, "Belum diajukan ke kantor")
            }
            StatusLine(icon, color, text)
            if (trip.settlementNote.isNotBlank()) {
                Text(trip.settlementNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Perbarui Status Reimburse") }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Dialog mencatat tanggal pengajuan, tanggal selesai, dan nominal yang diterima/dikembalikan. */
@Composable
fun SettlementDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (submittedDay: Long, settledDay: Long, settledAmount: Long, note: String) -> Unit,
) {
    val due = kotlin.math.abs(trip.amountDueToEmployee)
    var submitted by remember { mutableStateOf(trip.isSubmitted) }
    var submittedDay by remember { mutableLongStateOf(if (trip.isSubmitted) trip.submittedDay else Fmt.today()) }
    var settled by remember { mutableStateOf(trip.isSettled) }
    var settledDay by remember { mutableLongStateOf(if (trip.isSettled) trip.settledDay else Fmt.today()) }
    var amount by remember { mutableLongStateOf(if (trip.isSettled) trip.settledAmount else due) }
    var note by remember { mutableStateOf(trip.settlementNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status Reimburse") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${directionLabel(trip)}: ${Fmt.rupiah(due)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ToggleRow("Laporan sudah diajukan", submitted) { submitted = it }
                if (submitted) DateField(day = submittedDay, onChange = { submittedDay = it }, label = "Tanggal diajukan")
                ToggleRow(if (trip.amountDueToEmployee >= 0) "Reimburse sudah dibayar" else "Sisa sudah dikembalikan", settled) {
                    settled = it
                    if (it) submitted = true
                }
                if (settled) {
                    DateField(day = settledDay, onChange = { settledDay = it }, label = "Tanggal selesai")
                    MoneyField(
                        value = amount, onValueChange = { amount = it },
                        label = if (trip.amountDueToEmployee >= 0) "Nominal diterima" else "Nominal dikembalikan",
                        placeholder = Fmt.number(due),
                        supporting = "Isi sesuai bukti transfer. Bisa berbeda jika ada potongan.",
                    )
                }
                FormField(
                    value = note, onValueChange = { note = it },
                    label = "Catatan (opsional)",
                    placeholder = "Contoh: Ditransfer via payroll 25 Nov",
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    if (submitted || settled) submittedDay else -1,
                    if (settled) settledDay else -1,
                    if (settled) amount else 0,
                    note.trim(),
                )
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
