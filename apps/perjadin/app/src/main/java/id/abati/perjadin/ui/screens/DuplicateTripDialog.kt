package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.Trip
import id.abati.perjadin.ui.components.DateField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.util.Fmt

/**
 * Menyalin perjalanan sebagai template. Pengguna mengisi No. ODR & tanggal baru,
 * lalu melihat pratinjau anggaran per hari/malam yang berubah karena durasi berbeda.
 */
@Composable
fun DuplicateTripDialog(
    trip: Trip,
    existingOdr: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (odr: String, startDay: Long, endDay: Long) -> Unit,
) {
    var odr by remember { mutableStateOf("") }
    // Default: mulai hari ini dengan durasi yang sama seperti perjalanan asal
    var start by remember { mutableLongStateOf(Fmt.today()) }
    var end by remember { mutableLongStateOf(Fmt.today() + trip.durationDays - 1) }
    var submitted by remember { mutableStateOf(false) }

    val odrError = when {
        odr.isBlank() -> "Nomor ODR baru wajib diisi"
        odr.trim().lowercase() in existingOdr -> "Nomor ODR ini sudah dipakai"
        else -> null
    }
    val dateError = if (end < start) "Tanggal pulang tidak boleh sebelum berangkat" else null
    val preview = trip.duplicate(odr, start, end.coerceAtLeast(start))
    val changed = trip.budgets.zip(preview.budgets).filter { (old, new) -> old.amount != new.amount }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplikat Perjalanan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Anggaran disalin dari ${trip.odrNumber}. Pengeluaran, uang muka & status tidak ikut disalin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormField(
                    value = odr, onValueChange = { odr = it },
                    label = "Nomor ODR baru *",
                    placeholder = "Contoh: ODR/2026/11/0145",
                    supporting = if (submitted && odrError != null) odrError else null,
                    isError = submitted && odrError != null,
                    leading = Icons.Filled.Tag,
                    capitalization = KeyboardCapitalization.Characters,
                )
                DateField(day = start, onChange = { start = it; if (end < it) end = it }, label = "Tanggal Berangkat")
                DateField(
                    day = end, onChange = { end = it }, label = "Tanggal Pulang",
                    supporting = dateError ?: "Durasi: ${end - start + 1} hari (sebelumnya ${trip.durationDays} hari)",
                    isError = dateError != null,
                )

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pratinjau anggaran", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (changed.isEmpty()) {
                            Text(
                                if (trip.budgets.any { it.basis == BudgetBasis.PER_DAY || it.basis == BudgetBasis.PER_NIGHT }) {
                                    "Durasi sama, nominal anggaran tidak berubah."
                                } else {
                                    "Semua anggaran bernominal tetap; sesuaikan manual setelah disalin jika perlu."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        changed.forEach { (old, new) ->
                            Row {
                                Text(new.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${Fmt.rupiah(old.amount)} → ${Fmt.rupiah(new.amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row {
                            Text("Total anggaran", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(Fmt.rupiah(preview.totalBudget), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "Semua item tetap bisa diubah setelah disalin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (odrError == null && dateError == null) {
                    onConfirm(odr.trim(), start, end)
                    onDismiss()
                }
            }) { Text("Duplikat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
