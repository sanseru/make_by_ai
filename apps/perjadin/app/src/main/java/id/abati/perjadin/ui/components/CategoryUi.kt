package id.abati.perjadin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.CATEGORY_COLORS
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.newId

/**
 * Pilihan kategori + tombol "Tambah kategori baru" yang membuka [CategoryDialog].
 * [onCreate] dipanggil dengan kategori baru; pemanggil menyimpannya lalu memilihnya.
 */
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onCreate: (Category) -> Unit,
    supporting: String? = null,
) {
    var showNew by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId } ?: Category.unknown(selectedId)
    Column {
        DropdownField(
            label = "Kategori",
            options = categories,
            selected = selected,
            optionLabel = { it.label },
            onSelect = { onSelect(it.id) },
            optionIcon = { CategoryBadge(it, size = 28) },
            supporting = supporting,
        )
        TextButton(onClick = { showNew = true }) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tambah kategori baru")
        }
    }
    if (showNew) {
        CategoryDialog(
            category = null,
            existingLabels = categories.map { it.label.lowercase() }.toSet(),
            onDismiss = { showNew = false },
            onSave = { c ->
                onCreate(c)
                onSelect(c.id)
            },
        )
    }
}

/** Dialog membuat / mengubah kategori buatan pengguna: nama, contoh, warna, ikon. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    category: Category?,
    existingLabels: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(category?.label ?: "") }
    var example by remember { mutableStateOf(category?.example?.removePrefix("Contoh: ") ?: "") }
    var color by remember { mutableLongStateOf(category?.color ?: CATEGORY_COLORS.first()) }
    var icon by remember { mutableStateOf(category?.icon ?: "more") }
    var submitted by remember { mutableStateOf(false) }

    val error = when {
        label.isBlank() -> "Nama kategori wajib diisi"
        label.trim().lowercase() != category?.label?.lowercase() && label.trim().lowercase() in existingLabels -> "Nama kategori sudah ada"
        else -> null
    }
    val preview = Category(category?.id ?: "preview", label.ifBlank { "Kategori baru" }, "", color, icon)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Kategori Baru" else "Ubah Kategori") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(preview, size = 44)
                    Spacer(Modifier.width(12.dp))
                    Text(preview.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FormField(
                    value = label, onValueChange = { label = it.take(40) },
                    label = "Nama Kategori *",
                    placeholder = "Contoh: Bensin & Tol",
                    supporting = if (submitted && error != null) error else null,
                    isError = submitted && error != null,
                )
                FormField(
                    value = example, onValueChange = { example = it },
                    label = "Contoh isian (opsional)",
                    placeholder = "Contoh: Tol Jakarta–Bandung PP",
                    supporting = "Ditampilkan sebagai petunjuk di kolom uraian",
                )
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLORS.forEach { c ->
                        val isSel = c == color
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text("Ikon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.forEach { (key, vector) ->
                        val isSel = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Color(color).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (isSel) 2.dp else 0.dp, if (isSel) Color(color) else Color.Transparent, CircleShape)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, key, tint = if (isSel) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (error == null) {
                    onSave(
                        Category(
                            id = category?.id ?: newId(),
                            label = label.trim(),
                            example = example.trim().let { if (it.isBlank()) "" else "Contoh: $it" },
                            color = color,
                            icon = icon,
                            builtIn = category?.builtIn ?: false,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

/** Teks contoh untuk kolom uraian; kategori tanpa contoh memakai teks umum. */
fun Category.hint(): String = example.ifBlank { "Contoh: Uraian singkat pengeluaran $label" }
