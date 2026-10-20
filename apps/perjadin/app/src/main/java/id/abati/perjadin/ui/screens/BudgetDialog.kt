package id.abati.perjadin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.BudgetBasis
import id.abati.perjadin.data.BudgetItem
import id.abati.perjadin.data.Category
import id.abati.perjadin.ui.components.CategoryPicker
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.MoneyField
import id.abati.perjadin.ui.components.hint
import id.abati.perjadin.util.Fmt

/**
 * Dialog tambah / ubah pos anggaran. Anggaran bisa berupa nominal tetap, per hari,
 * per malam (otomatis mengikuti tanggal perjalanan), atau jumlah × tarif manual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetDialog(
    item: BudgetItem?,
    categories: List<Category>,
    durationDays: Long,
    onCreateCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
    onSave: (BudgetItem) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var categoryId by remember { mutableStateOf(item?.categoryId ?: categories.first().id) }
    val category = categories.firstOrNull { it.id == categoryId } ?: Category.unknown(categoryId)
    var description by remember { mutableStateOf(item?.description ?: "") }
    var basis by remember { mutableStateOf(item?.basis ?: BudgetBasis.FIXED) }
    var amount by remember { mutableLongStateOf(item?.amount ?: 0L) }
    var rate by remember { mutableLongStateOf(item?.rate ?: 0L) }
    var qty by remember { mutableStateOf(item?.qty?.takeIf { it > 0 }?.toString() ?: "1") }
    var lumpsum by remember { mutableStateOf(item?.lumpsum ?: (categoryId == "PER_DIEM")) }
    var submitted by remember { mutableStateOf(false) }

    val draft = BudgetItem(basis = basis, rate = rate, qty = qty.toLongOrNull() ?: 0L, amount = amount)
    val finalAmount = draft.recalc(durationDays).amount
    val amountError = when {
        basis != BudgetBasis.FIXED && rate <= 0 -> "Isi tarif per ${basis.unit.ifBlank { "satuan" }}"
        finalAmount <= 0 -> "Nominal anggaran harus lebih dari 0"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Tambah Anggaran" else "Ubah Anggaran") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                    onCreate = onCreateCategory,
                )
                FormField(
                    value = description, onValueChange = { description = it },
                    label = "Uraian",
                    placeholder = category.hint(),
                    supporting = "Sesuai rincian di dokumen ODR",
                    singleLine = false,
                )

                Text("Dasar perhitungan", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BudgetBasis.entries.forEach { b ->
                        FilterChip(
                            selected = basis == b,
                            onClick = {
                                // Saat beralih ke nominal tetap, bawa nilai hasil hitungan terakhir
                                if (b == BudgetBasis.FIXED && basis != BudgetBasis.FIXED) amount = finalAmount
                                basis = b
                            },
                            label = { Text(b.label) },
                        )
                    }
                }

                if (basis == BudgetBasis.FIXED) {
                    MoneyField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Nominal Anggaran",
                        placeholder = "Contoh: 2.000.000",
                        supporting = if (submitted && amountError != null) amountError else "Plafon yang disetujui kantor",
                        isError = submitted && amountError != null,
                    )
                } else {
                    Row {
                        if (basis == BudgetBasis.PER_QTY) {
                            OutlinedTextField(
                                value = qty,
                                onValueChange = { v -> qty = v.filter(Char::isDigit).take(4) },
                                label = { Text("Jumlah") },
                                placeholder = { Text("2") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.35f),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        MoneyField(
                            value = rate,
                            onValueChange = { rate = it },
                            label = if (basis == BudgetBasis.PER_QTY) "Tarif" else "Tarif per ${basis.unit}",
                            placeholder = if (basis == BudgetBasis.PER_NIGHT) "500.000" else "150.000",
                            isError = submitted && amountError != null,
                            modifier = Modifier.weight(0.65f),
                        )
                    }
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                draft.formula(durationDays, Fmt::rupiah) ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "= ${Fmt.rupiah(finalAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (basis == BudgetBasis.PER_DAY || basis == BudgetBasis.PER_NIGHT) {
                                Text(
                                    "Jumlah ${basis.unit} otomatis dari tanggal perjalanan ($durationDays hari). " +
                                        "Jika tanggal diubah atau perjalanan diduplikat, nominal ikut dihitung ulang.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    if (submitted && amountError != null) {
                        Text(amountError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                // Lumpsum: uang harian/makan/saku yang dibayar tetap tanpa bukti
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lumpsum (tanpa bukti)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (basis == BudgetBasis.PER_DAY || basis == BudgetBasis.PER_NIGHT) {
                                "Untuk uang harian/makan/saku. Dibayar penuh sesuai hari aktual, tidak dikembalikan walau belanja lebih sedikit. Pulang lebih cepat → kekurangan hari dikembalikan; diperpanjang → ditambah."
                            } else {
                                "Dibayar tetap sebesar nominal ini, tidak perlu struk & tidak dikembalikan."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = lumpsum, onCheckedChange = { lumpsum = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (amountError == null) {
                    onSave(
                        (item ?: BudgetItem()).copy(
                            categoryId = categoryId,
                            description = description.trim().ifBlank { category.label },
                            basis = basis,
                            rate = if (basis == BudgetBasis.FIXED) 0 else rate,
                            qty = if (basis == BudgetBasis.PER_QTY) (qty.toLongOrNull() ?: 0L) else 0,
                            amount = finalAmount,
                            lumpsum = lumpsum,
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
