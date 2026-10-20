package id.abati.perjadin.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String, val example: String?)

private val pages = listOf(
    Page(
        Icons.Filled.Luggage,
        "Selamat datang di Perjadin",
        "Catat perjalanan dinas, anggaran, dan pengeluaran di satu tempat. Semua data tersimpan di HP Anda — tanpa internet & tanpa akun.",
        null,
    ),
    Page(
        Icons.Filled.Description,
        "1. Buat perjalanan dari ODR",
        "Masukkan nomor On Duty Request (ODR), keperluan, rute, tanggal, dan uang muka yang diterima dari kantor.",
        "ODR/2026/10/0123 · Jakarta → Medan · 12–15 Okt 2026",
    ),
    Page(
        Icons.Filled.AccountBalanceWallet,
        "2. Susun anggaran",
        "Isi plafon anggaran yang disetujui kantor per kategori: tiket, hotel, uang harian, konsumsi, dan lainnya.",
        "Tiket pesawat PP · Rp2.000.000",
    ),
    Page(
        Icons.Filled.ReceiptLong,
        "3. Catat setiap pengeluaran",
        "Tekan \"Catat Pengeluaran\" di Beranda: ketik nominal, pilih kategori, foto struknya. Realisasi & sisa anggaran dihitung otomatis, dengan peringatan jika melebihi plafon.",
        "Taksi bandara → hotel · Rp185.000 · Struk GRB-552301",
    ),
    Page(
        Icons.Filled.PictureAsPdf,
        "4. Kirim laporan PDF",
        "Buat laporan pertanggungjawaban yang rapi dalam satu ketukan, lalu kirim lewat WhatsApp, Email, atau simpan ke perangkat.",
        "Laporan_Perjadin_ODR-2026-10-0123.pdf",
    ),
)

@Composable
fun OnboardingScreen(firstRun: Boolean, onFinish: (loadDemo: Boolean) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    var loadDemo by remember { mutableStateOf(true) }
    val isLast = pagerState.currentPage == pages.lastIndex
    val cs = MaterialTheme.colorScheme

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.background, cs.background))),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (!isLast) TextButton(onClick = { onFinish(firstRun && loadDemo) }) { Text("Lewati") }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(cs.primary, cs.secondary))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(page.icon, null, tint = cs.onPrimary, modifier = Modifier.size(68.dp))
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                    if (page.example != null) {
                        Spacer(Modifier.height(20.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerLowest, tonalElevation = 1.dp) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Contoh", style = MaterialTheme.typography.labelSmall, color = cs.primary, fontWeight = FontWeight.Bold)
                                Text(page.example, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    if (index == pages.lastIndex && firstRun) {
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = loadDemo, onCheckedChange = { loadDemo = it })
                            Text("Muat 1 perjalanan contoh agar mudah dicoba", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Indikator halaman
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    val w by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                    val c by animateColorAsState(if (selected) cs.primary else cs.outline, label = "dotColor")
                    Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(c))
                }
            }

            Button(
                onClick = {
                    if (isLast) onFinish(firstRun && loadDemo)
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).height(52.dp),
            ) {
                Text(if (isLast) (if (firstRun) "Mulai Sekarang" else "Selesai") else "Lanjut", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
