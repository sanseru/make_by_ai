package id.abati.perjadin.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.Path as ComposePath

/**
 * Dialog untuk menggambar tanda tangan dengan jari. Hasil dikembalikan sebagai bitmap
 * transparan (tinta hitam) yang sudah dipotong rapat ke area coretan.
 */
@Composable
fun SignatureDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    // Setiap coretan = daftar titik; disimpan sebagai state agar Canvas menggambar ulang
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokeWidthPx = 6f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Tanda Tangan") },
        text = {
            Column {
                Text(
                    "Tanda tangani di dalam kotak menggunakan jari. Akan dicetak otomatis di kolom \"Dilaporkan oleh\" pada laporan PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.small)
                        // Latar putih agar tinta hitam terlihat juga di mode gelap
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    if (strokes.isEmpty() && current.isEmpty()) {
                        Text("Tanda tangan di sini", color = Color.LightGray)
                    }
                    Canvas(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { current = listOf(it) },
                                    onDrag = { change, _ -> current = current + change.position },
                                    onDragEnd = {
                                        if (current.size > 1) strokes.add(current)
                                        current = emptyList()
                                    },
                                )
                            },
                    ) {
                        (strokes + listOf(current)).forEach { pts ->
                            if (pts.size > 1) {
                                val path = ComposePath().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(path, Color.Black, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    onSave(renderSignature(strokes.toList(), strokeWidthPx))
                    onDismiss()
                },
            ) { Text("Simpan") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { strokes.clear() }) { Text("Ulangi") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

/** Menggambar coretan ke bitmap transparan yang dipotong ke bounding box + margin. */
private fun renderSignature(strokes: List<List<Offset>>, strokeWidth: Float): Bitmap {
    val pad = strokeWidth * 2
    val all = strokes.flatten()
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = 0f; var maxY = 0f
    all.forEach { minX = min(minX, it.x); minY = min(minY, it.y); maxX = max(maxX, it.x); maxY = max(maxY, it.y) }
    val w = (maxX - minX + 2 * pad).toInt().coerceAtLeast(1)
    val h = (maxY - minY + 2 * pad).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val canvas = AndroidCanvas(bmp)
    strokes.forEach { pts ->
        val path = Path().apply {
            moveTo(pts[0].x - minX + pad, pts[0].y - minY + pad)
            pts.drop(1).forEach { lineTo(it.x - minX + pad, it.y - minY + pad) }
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}
