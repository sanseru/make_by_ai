package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Category
import id.abati.perjadin.ui.components.CategoryBadge
import id.abati.perjadin.ui.components.DropdownField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.MoneyField

/** Dialog tambah / ubah pos anggaran, dengan kalkulator jumlah × harga satuan. */
@Composable
fun BudgetDialog(
    item: BudgetItem?,
    durationDays: Long,
    onDismiss: () -> Unit,
    onSave: (BudgetItem) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var category by remember { mutableStateOf(item?.category ?: Category.TRANSPORT) }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var amount by remember { mutableLongStateOf(item?.amount ?: 0L) }
    var useCalc by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf(durationDays.toString()) }
    var unit by remember { mutableLongStateOf(0L) }
    var submitted by remember { mutableStateOf(false) }

    // Saat kalkulator aktif, nominal dihitung dari jumlah × tarif (tanpa menulis state saat komposisi)
    val calcAmount = (qty.toLongOrNull() ?: 0L) * unit
    val finalAmount = if (useCalc) calcAmount else amount
    val amountError = if (finalAmount <= 0) "Nominal anggaran harus lebih dari 0" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Tambah Anggaran" else "Ubah Anggaran") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DropdownField(
                    label = "Kategori",
                    options = Category.entries,
                    selected = category,
                    optionLabel = { it.label },
                    onSelect = { category = it },
                    optionIcon = { CategoryBadge(it, size = 28) },
                )
                FormField(
                    value = description, onValueChange = { description = it },
                    label = "Uraian",
                    placeholder = category.example,
                    supporting = "Sesuai rincian di dokumen ODR",
                    singleLine = false,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Hitung otomatis", style = MaterialTheme.typography.bodyMedium)
                        Text("Jumlah hari/malam × tarif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = useCalc, onCheckedChange = { on ->
                        if (!on) amount = calcAmount
                        useCalc = on
                    })
                }
                if (useCalc) {
                    Row {
                        OutlinedTextField(
                            value = qty,
                            onValueChange = { v -> qty = v.filter(Char::isDigit).take(4) },
                            label = { Text("Jumlah") },
                            placeholder = { Text("4") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.35f),
                        )
                        Spacer(Modifier.width(8.dp))
                        MoneyField(
                            value = unit, onValueChange = { unit = it },
                            label = "Tarif",
                            placeholder = "150.000",
                            modifier = Modifier.weight(0.65f),
                        )
                    }
                }
                MoneyField(
                    value = finalAmount,
                    onValueChange = { amount = it; useCalc = false },
                    label = "Nominal Anggaran",
                    placeholder = "Contoh: 2.000.000",
                    supporting = if (submitted && amountError != null) amountError else "Plafon yang disetujui kantor",
                    isError = submitted && amountError != null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (amountError == null) {
                    onSave(
                        (item ?: BudgetItem()).copy(
                            category = category,
                            description = description.trim().ifBlank { category.label },
                            amount = finalAmount,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (item != null && onDelete != null) {
                    TextButton(onClick = { onDelete(item.id); onDismiss() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}
