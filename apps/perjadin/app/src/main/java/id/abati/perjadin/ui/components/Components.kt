package id.abati.perjadin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.TripStatus
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.ui.theme.Success
import id.abati.perjadin.ui.theme.Warning
import id.abati.perjadin.util.Fmt

// ---------------- ikon & warna kategori ----------------

val Category.icon: ImageVector
    get() = when (this) {
        Category.TRANSPORT -> Icons.Filled.Flight
        Category.HOTEL -> Icons.Filled.Hotel
        Category.PER_DIEM -> Icons.Filled.AccountBalanceWallet
        Category.MEAL -> Icons.Filled.Restaurant
        Category.LOCAL_TRANSPORT -> Icons.Filled.LocalTaxi
        Category.COMMUNICATION -> Icons.Filled.Wifi
        Category.OTHER -> Icons.Filled.MoreHoriz
    }

val Category.tint: Color get() = Color(color)

@Composable
fun CategoryBadge(category: Category, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(category.tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = category.label, tint = category.tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun StatusBadge(status: TripStatus) {
    val color = when (status) {
        TripStatus.PLANNED -> MaterialTheme.colorScheme.secondary
        TripStatus.ONGOING -> Warning
        TripStatus.DONE -> MaterialTheme.colorScheme.primary
        TripStatus.REPORTED -> Success
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            status.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Warna progres: hijau aman, kuning mendekati batas, merah melebihi anggaran. */
@Composable
fun usageColor(usage: Float): Color = when {
    usage > 1f -> Danger
    usage >= 0.85f -> Warning
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun BudgetBar(actual: Long, budget: Long, modifier: Modifier = Modifier, height: Int = 8) {
    val usage = if (budget <= 0) (if (actual > 0) 1.01f else 0f) else actual.toFloat() / budget
    LinearProgressIndicator(
        progress = { usage.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)),
        color = usageColor(usage),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

// ---------------- form fields ----------------

/** Helper slot teks opsional untuk parameter `supportingText`, `placeholder`, dll. */
fun textSlot(text: String?): (@Composable () -> Unit)? = if (text == null) null else ({ Text(text) })

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ImageVector? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val leadingSlot: (@Composable () -> Unit)? = if (leading == null) null else ({ Icon(leading, null) })
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        supportingText = textSlot(supporting),
        leadingIcon = leadingSlot,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Input nominal rupiah dengan pemisah ribuan otomatis (5000000 → 5.000.000). */
@Composable
fun MoneyField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(if (value > 0) Fmt.number(value) else "")) }
    // Sinkronkan jika nilai diubah dari luar (mis. kalkulator jumlah × harga)
    LaunchedEffect(value) {
        if (Fmt.parseDigits(tfv.text) != value) {
            val t = if (value > 0) Fmt.number(value) else ""
            tfv = TextFieldValue(t, TextRange(t.length))
        }
    }
    OutlinedTextField(
        value = tfv,
        onValueChange = { nv ->
            val digits = Fmt.parseDigits(nv.text)
            val t = if (nv.text.any(Char::isDigit)) Fmt.number(digits) else ""
            tfv = TextFieldValue(t, TextRange(t.length))
            onValueChange(digits)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        prefix = { Text("Rp ") },
        supportingText = textSlot(supporting),
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    day: Long,
    onChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = Fmt.dateLong(day),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            supportingText = textSlot(supporting),
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Lapisan transparan agar seluruh field bisa diklik untuk membuka kalender
        Box(
            Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { open = true },
        )
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * Fmt.DAY_MS)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it / Fmt.DAY_MS) }
                    open = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ImageVector? = null,
    optionIcon: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val leadingSlot: (@Composable () -> Unit)? = if (leading == null) null else ({ Icon(leading, null) })
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leadingSlot,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = textSlot(supporting),
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val iconSlot: (@Composable () -> Unit)? = if (optionIcon == null) null else ({ optionIcon(option) })
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = iconSlot,
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ---------------- tampilan umum ----------------

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kartu tips berwarna lembut untuk memandu pengguna. */
@Composable
fun TipCard(text: String, modifier: Modifier = Modifier, title: String = "Tips", icon: ImageVector = Icons.Filled.Lightbulb) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.height(2.dp))
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(
            value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            modifier = Modifier.weight(0.58f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun PillButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
