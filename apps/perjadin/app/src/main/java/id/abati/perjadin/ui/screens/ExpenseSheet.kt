package id.abati.perjadin.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.abati.perjadin.data.Category
import id.abati.perjadin.data.Expense
import id.abati.perjadin.data.PAYMENT_METHODS
import id.abati.perjadin.data.Trip
import id.abati.perjadin.data.find
import id.abati.perjadin.report.ShareHelper
import id.abati.perjadin.ui.components.CategoryDialog
import id.abati.perjadin.ui.components.ConfirmDialog
import id.abati.perjadin.ui.components.DropdownField
import id.abati.perjadin.ui.components.FormField
import id.abati.perjadin.ui.components.MoneyField
import id.abati.perjadin.ui.components.PickDateDialog
import id.abati.perjadin.ui.components.hint
import id.abati.perjadin.ui.components.iconVector
import id.abati.perjadin.ui.components.tint
import id.abati.perjadin.ui.theme.Danger
import id.abati.perjadin.util.Fmt
import id.abati.perjadin.util.ReceiptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Form cepat catat / ubah pengeluaran dalam bottom sheet.
 * Alur tercepat: ketik nominal → pilih kategori → Simpan. Detail lain opsional.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseSheet(
    trip: Trip,
    categories: List<Category>,
    history: List<Expense>,
    expense: Expense?,
    onSave: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    onCreateCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = expense == null

    val today = Fmt.today()
    val defaultDay = if (today in trip.startDay..trip.endDay) today else trip.startDay
    val defaultCategory = trip.expenses.lastOrNull()?.categoryId ?: trip.budgets.firstOrNull()?.categoryId ?: categories.first().id

    var day by remember { mutableLongStateOf(expense?.day ?: defaultDay) }
    var categoryId by remember { mutableStateOf(expense?.categoryId ?: defaultCategory) }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var amount by remember { mutableLongStateOf(expense?.amount ?: 0L) }
    var payment by remember { mutableStateOf(expense?.paymentMethod ?: history.firstOrNull()?.paymentMethod ?: PAYMENT_METHODS.first()) }
    var receipt by remember { mutableStateOf(expense?.receiptNo ?: "") }
    var note by remember { mutableStateOf(expense?.note ?: "") }
    var photo by remember { mutableStateOf(expense?.photo ?: "") }
    var showDetails by remember { mutableStateOf(expense != null && (expense.receiptNo.isNotBlank() || expense.note.isNotBlank())) }
    var submitted by remember { mutableStateOf(false) }
    var savedCount by remember { mutableIntStateOf(0) }
    var pickDate by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    // Kunci untuk mengulang fokus ke nominal setelah "Simpan & Tambah Lagi"
    var focusKey by remember { mutableIntStateOf(0) }
    val amountFocus = remember { FocusRequester() }

    val category = categories.find(categoryId)
    val amountError = if (amount <= 0) "Isi nominal yang dibayar" else null

    // Kategori yang paling sering dipakai tampil lebih dulu
    val usage = history.groupingBy { it.categoryId }.eachCount()
    val orderedCategories = categories.sortedByDescending { usage[it.id] ?: 0 }

    // Riwayat isian unik terbaru untuk isi ulang satu ketukan
    val quickFills = if (isNew) {
        history.distinctBy { Triple(it.description.lowercase(), it.categoryId, it.amount) }.take(8)
    } else {
        emptyList()
    }

    val catBudget = trip.budgetFor(categoryId)
    val catRemaining = catBudget - trip.expenses.filter { it.categoryId == categoryId && it.id != expense?.id }.sumOf { it.amount } - amount

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun build(): Expense? {
        submitted = true
        if (amountError != null) return null
        return (expense ?: Expense(day = day)).copy(
            day = day,
            categoryId = categoryId,
            description = description.trim().ifBlank { category.label },
            amount = amount,
            paymentMethod = payment,
            receiptNo = receipt.trim(),
            note = note.trim(),
            photo = photo,
        )
    }

    fun importPhoto(uri: Uri, temp: File? = null) {
        photoBusy = true
        scope.launch {
            try {
                photo = withContext(Dispatchers.IO) { ReceiptStore.importImage(context, uri) }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat foto: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                temp?.delete()
                photoBusy = false
            }
        }
    }

    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) importPhoto(Uri.fromFile(f), temp = f) else f?.delete()
        cameraFile = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importPhoto(uri)
    }

    LaunchedEffect(focusKey) {
        if (isNew) {
            delay(350) // tunggu animasi sheet agar keyboard muncul mulus
            try {
                amountFocus.requestFocus()
            } catch (_: IllegalStateException) {
                // field belum terpasang; abaikan
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isNew) "Catat Pengeluaran" else "Ubah Pengeluaran", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${trip.odrNumber} · ${trip.destination}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (savedCount > 0) {
                Text(
                    "✓ $savedCount pengeluaran tersimpan. Lanjutkan isi berikutnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Nominal (besar & langsung fokus)
            MoneyField(
                value = amount,
                onValueChange = { amount = it },
                label = "Nominal dibayar *",
                placeholder = "185.000",
                supporting = if (submitted && amountError != null) amountError else null,
                isError = submitted && amountError != null,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.focusRequester(amountFocus),
            )

            // Kategori sebagai chip
            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                orderedCategories.forEach { c ->
                    val selected = c.id == categoryId
                    FilterChip(
                        selected = selected,
                        onClick = { categoryId = c.id },
                        label = { Text(c.label) },
                        leadingIcon = { Icon(c.iconVector, null, tint = if (selected) c.tint else c.tint.copy(alpha = 0.8f), modifier = Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.tint.copy(alpha = 0.16f)),
                    )
                }
                AssistChip(
                    onClick = { newCategory = true },
                    label = { Text("Kategori baru") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)) },
                )
            }

            if (trip.isLumpsumCategory(categoryId)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${category.label} dibayar lumpsum. Catatan ini hanya untuk Anda & tidak memengaruhi laporan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (catBudget > 0 && amount > 0) {
                val over = catRemaining < 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (over) Icons.Filled.Warning else Icons.Filled.Info, null, tint = if (over) Danger else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (over) "Melebihi anggaran ${category.label} ${Fmt.rupiah(-catRemaining)}" else "Sisa anggaran ${category.label}: ${Fmt.rupiah(catRemaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FormField(
                value = description, onValueChange = { description = it },
                label = "Uraian (opsional)",
                placeholder = category.hint(),
                supporting = "Kosongkan untuk memakai nama kategori",
            )

            // Tanggal: pilihan cepat
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = day == today, onClick = { day = today }, label = { Text("Hari ini") })
                FilterChip(selected = day == today - 1, onClick = { day = today - 1 }, label = { Text("Kemarin") })
                FilterChip(
                    selected = day != today && day != today - 1,
                    onClick = { pickDate = true },
                    label = { Text(if (day != today && day != today - 1) Fmt.date(day) else "Tanggal lain") },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(18.dp)) },
                )
            }
            if (day < trip.startDay || day > trip.endDay) {
                Text("Di luar tanggal perjalanan (${Fmt.range(trip.startDay, trip.endDay)})", style = MaterialTheme.typography.bodySmall, color = Danger)
            }

            // Foto struk
            Text("Foto struk (opsional)", style = MaterialTheme.typography.labelLarge)
            ReceiptPhotoRow(
                photo = photo,
                busy = photoBusy,
                onCamera = {
                    val f = ReceiptStore.newCameraFile(context)
                    cameraFile = f
                    cameraLauncher.launch(ShareHelper.uriOf(context, f))
                },
                onGallery = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onView = { ShareHelper.viewImage(context, ReceiptStore.file(context, photo)) },
                onRemove = { photo = "" },
            )

            // Riwayat isian cepat
            if (quickFills.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Isi cepat dari riwayat", style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickFills.forEach { h ->
                        val c = categories.find(h.categoryId)
                        AssistChip(
                            onClick = {
                                categoryId = h.categoryId
                                description = h.description
                                amount = h.amount
                                payment = h.paymentMethod
                            },
                            label = { Text("${h.description} · ${Fmt.rupiah(h.amount)}", maxLines = 1) },
                            leadingIcon = { Icon(c.iconVector, null, tint = c.tint, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            // Detail tambahan (disembunyikan agar form tetap ringkas)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { showDetails = !showDetails }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Detail lainnya: pembayaran, no. struk, keterangan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Icon(if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(showDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DropdownField(
                        label = "Metode Pembayaran",
                        options = PAYMENT_METHODS,
                        selected = payment,
                        optionLabel = { it },
                        onSelect = { payment = it },
                        leading = Icons.Filled.CreditCard,
                    )
                    FormField(
                        value = receipt, onValueChange = { receipt = it },
                        label = "No. Struk / Invoice / Kode Booking",
                        placeholder = "Contoh: INV-SNT-0931 atau Struk 0045",
                        leading = Icons.Filled.Receipt,
                    )
                    FormField(
                        value = note, onValueChange = { note = it },
                        label = "Keterangan",
                        placeholder = "Contoh: Dibayar bersama 2 rekan, bagian saya saja",
                        singleLine = false,
                    )
                }
            }

            // Tombol simpan
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) {
                    OutlinedButton(
                        onClick = {
                            build()?.let {
                                onSave(it)
                                savedCount++
                                // Reset isian, tetap pertahankan kategori, tanggal & metode bayar
                                amount = 0
                                description = ""
                                receipt = ""
                                note = ""
                                photo = ""
                                submitted = false
                                focusKey++
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Simpan & Tambah Lagi", maxLines = 1) }
                }
                Button(
                    onClick = { build()?.let { onSave(it); close() } },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Simpan") }
            }
        }
    }

    if (pickDate) PickDateDialog(day = day, onPick = { day = it }, onDismiss = { pickDate = false })

    if (newCategory) {
        CategoryDialog(
            category = null,
            existingLabels = categories.map { it.label.lowercase() }.toSet(),
            onDismiss = { newCategory = false },
            onSave = { c ->
                onCreateCategory(c)
                categoryId = c.id
            },
        )
    }

    if (confirmDelete && expense != null) {
        ConfirmDialog(
            title = "Hapus pengeluaran?",
            message = "\"${expense.description}\" senilai ${Fmt.rupiah(expense.amount)} akan dihapus.",
            confirmText = "Hapus",
            onConfirm = {
                onDelete(expense)
                close()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun ReceiptPhotoRow(
    photo: String,
    busy: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onView: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, photo) {
        value = if (photo.isBlank()) null else withContext(Dispatchers.IO) { ReceiptStore.load(context, photo, 400)?.asImageBitmap() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            busy -> Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            thumb != null -> {
                Image(
                    bitmap = thumb!!,
                    contentDescription = "Foto struk",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onView),
                )
                Column {
                    TextButton(onClick = onCamera) { Text("Ganti foto") }
                    TextButton(onClick = onRemove) { Text("Hapus foto", color = MaterialTheme.colorScheme.error) }
                }
            }
            else -> {
                OutlinedButton(onClick = onCamera) {
                    Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kamera")
                }
                OutlinedButton(onClick = onGallery) {
                    Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeri")
                }
            }
        }
    }
}

/** Sheet untuk memilih perjalanan saat mencatat pengeluaran dari Beranda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPickerSheet(trips: List<Trip>, onPick: (Trip) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Catat ke perjalanan mana?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Perjalanan yang sedang berlangsung ditampilkan paling atas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            trips.forEach { t ->
                Surface(
                    onClick = { onPick(t) },
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(t.odrNumber, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(t.purpose, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${t.destination} · ${Fmt.range(t.startDay, t.endDay)} · ${t.status.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
