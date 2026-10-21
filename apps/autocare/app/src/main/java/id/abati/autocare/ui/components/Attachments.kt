package id.abati.autocare.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.abati.autocare.data.Attachment
import id.abati.autocare.data.AttachmentKind
import id.abati.autocare.util.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tombol "Kamera" & "File/galeri". Hasil impor (sudah disalin ke penyimpanan internal)
 * dikirim ke [onImported]; pemanggil yang memutuskan kapan disimpan ke database.
 */
@Composable
fun AttachmentPicker(onImported: (AttachmentStore.Imported) -> Unit, modifier: Modifier = Modifier, imagesOnly: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = LocalActions.current
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun importUri(uri: Uri) {
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) { AttachmentStore.import(context, uri) }
                onImported(imported)
            } catch (e: Exception) {
                actions.message("Lampiran gagal ditambahkan: ${e.message}")
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = cameraPath
        if (ok && path != null) importUri(AttachmentStore.uriOf(context, java.io.File(path)))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val f = AttachmentStore.newCameraFile(context)
            cameraPath = f.path
            try {
                camera.launch(AttachmentStore.uriOf(context, f))
            } catch (_: Exception) {
                actions.message("Kamera tidak tersedia")
            }
        }) {
            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
            Text("  Kamera")
        }
        OutlinedButton(onClick = {
            try {
                picker.launch(if (imagesOnly) arrayOf("image/*") else arrayOf("image/*", "application/pdf", "*/*"))
            } catch (_: Exception) {
                actions.message("Pemilih file tidak tersedia")
            }
        }) {
            Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
            Text(if (imagesOnly) "  Galeri" else "  Galeri/File")
        }
    }
}

/** Data tampilan lampiran: tersimpan (Attachment) maupun yang belum disimpan (Imported). */
data class AttachmentView(val key: String, val fileName: String, val displayName: String, val mimeType: String)

fun Attachment.view() = AttachmentView(id, fileName, displayName.ifBlank { kind.label }, mimeType)
fun AttachmentStore.Imported.view() = AttachmentView(fileName, fileName, displayName, mimeType)

/** Deretan thumbnail lampiran; ketuk untuk membuka, X untuk menghapus. */
@Composable
fun AttachmentStrip(items: List<AttachmentView>, onDelete: ((AttachmentView) -> Unit)?, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<AttachmentView?>(null) }
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { a ->
            Column(Modifier.width(92.dp)) {
                Box {
                    Thumbnail(
                        a.fileName, a.mimeType,
                        Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)).clickable { AttachmentStore.open(context, a.fileName, a.mimeType) },
                    )
                    if (onDelete != null) {
                        IconButton(
                            onClick = { confirm = a },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(2.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(50)),
                        ) { Icon(Icons.Default.Close, "Hapus lampiran", Modifier.size(16.dp)) }
                    }
                }
                Text(a.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    confirm?.let { a ->
        ConfirmDialog(
            "Hapus lampiran?", "File \"${a.displayName}\" akan dihapus dari perangkat.", "Hapus",
            onConfirm = { onDelete?.invoke(a) }, onDismiss = { confirm = null }, destructive = true,
        )
    }
}

/** Thumbnail gambar, ikon dokumen, atau penanda "file tidak ditemukan". */
@Composable
fun Thumbnail(fileName: String, mimeType: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exists = remember(fileName) { AttachmentStore.exists(context, fileName) }
    val bitmap by produceState<Bitmap?>(null, fileName) {
        value = if (exists && mimeType.startsWith("image/")) withContext(Dispatchers.IO) { AttachmentStore.loadBitmap(context, fileName, 300) } else null
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            !exists -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error)
                Text("File tidak ditemukan", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp))
            }
            else -> Icon(Icons.Default.Description, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Jenis lampiran tebakan dari nama file: nota/invoice bila mengandung kata kunci, selain itu foto/dokumen. */
fun guessKind(imported: AttachmentStore.Imported, default: AttachmentKind): AttachmentKind = when {
    imported.displayName.contains("invoice", true) -> AttachmentKind.INVOICE
    imported.displayName.contains("nota", true) || imported.displayName.contains("kwitansi", true) || imported.displayName.contains("kuitansi", true) -> AttachmentKind.RECEIPT
    !imported.mimeType.startsWith("image/") -> AttachmentKind.DOCUMENT
    else -> default
}
