package id.abati.perjadin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.abati.perjadin.ui.components.SectionTitle

private val faqs = listOf(
    "Apa itu Nomor ODR?" to
        "ODR (On Duty Request) adalah nomor surat tugas / permohonan perjalanan dinas dari kantor. Masukkan persis seperti di dokumen, misalnya ODR/2026/10/0123. Nomor ini menjadi identitas utama perjalanan dan tercetak di laporan.",
    "Apa beda Anggaran dan Realisasi?" to
        "Anggaran = plafon biaya yang disetujui kantor (mis. tiket Rp2.000.000). Realisasi = uang yang benar-benar Anda bayarkan (mis. tiket Rp1.650.000). Aplikasi menghitung selisih & persentase serapan secara otomatis.",
    "Bagaimana jika pengeluaran melebihi anggaran?" to
        "Tetap catat sesuai nominal sebenarnya. Aplikasi akan menandai kategori tersebut dengan warna merah dan peringatan, sehingga Anda bisa menyiapkan alasan atau persetujuan atasan sebelum laporan dikirim.",
    "Apa itu Uang Muka dan Penyelesaian (settlement)?" to
        "Uang muka adalah dana yang Anda terima dari kantor sebelum berangkat. Setelah perjalanan, aplikasi menghitung: jika realisasi lebih kecil, sisa dikembalikan ke kantor; jika lebih besar, kekurangannya dapat diajukan reimburse.",
    "Kantor saya memakai sistem uang harian lumpsum, bagaimana mencatatnya?" to
        "Jika kebijakan kantor memberikan uang harian tetap (lumpsum) tanpa perlu bukti, catat sebagai pengeluaran kategori \"Uang Harian\" sebesar nominal yang ditetapkan, dan tulis dasar kebijakannya di Keterangan (mis. \"Sesuai SK uang harian golongan III\").",
    "Cara tercepat mencatat pengeluaran?" to
        "Tekan \"Catat Pengeluaran\" di Beranda. Aplikasi langsung memilih perjalanan yang sedang berlangsung (atau menanyakan jika ada beberapa). Ketik nominal → pilih kategori → Simpan. Gunakan \"Simpan & Tambah Lagi\" untuk mencatat beberapa struk berturut-turut, dan chip \"Isi cepat dari riwayat\" untuk biaya yang berulang.",
    "Bagaimana melampirkan foto struk?" to
        "Di form pengeluaran, tekan Kamera atau Galeri pada bagian \"Foto struk\". Foto dikompres & disimpan di HP, lalu otomatis dilampirkan di halaman terakhir laporan PDF (bisa dimatikan di layar Laporan). Catatan: file backup .json hanya berisi data, tidak termasuk foto.",
    "Dinas rutin ke kota yang sama, harus isi anggaran dari awal?" to
        "Tidak perlu. Buka perjalanan lama → menu ⋮ → \"Duplikat\". Isi No. ODR & tanggal baru. Anggaran berdasar \"Per hari\" / \"Per malam\" (mis. uang harian, hotel) otomatis dihitung ulang sesuai durasi baru, dan Anda melihat pratinjaunya sebelum menyimpan. Pengeluaran & uang muka tidak ikut disalin.",
    "Apa beda dasar perhitungan anggaran?" to
        "Nominal tetap: angka langsung (mis. tiket PP Rp2.000.000). Per hari: tarif × jumlah hari perjalanan (mis. uang harian). Per malam: tarif × (hari − 1) (mis. hotel). Jumlah × tarif: jumlah manual (mis. 2 kali taksi bandara). Per hari/malam otomatis menyesuaikan saat tanggal perjalanan diubah.",
    "Bagaimana mengubah anggaran yang sudah diisi?" to
        "Buka perjalanan → tab Anggaran → ketuk item anggaran (ikon pensil) untuk mengubah kategori, uraian, atau nominal, atau menghapusnya.",
    "Bisakah saya menambah kategori sendiri?" to
        "Bisa. Saat mengisi anggaran / pengeluaran, ketuk \"Tambah kategori baru\" di bawah pilihan kategori, atau buka Pengaturan → Kategori Biaya. Atur nama, contoh isian, warna, dan ikon. Semua kategori (termasuk bawaan) bisa diubah nama, ikon, dan warnanya, dan bisa dihapus selama belum dipakai.",
    "Bagaimana melacak reimburse yang belum dibayar?" to
        "Di tab Ringkasan ada kartu \"Penyelesaian & Reimburse\". Tekan \"Perbarui Status Reimburse\" untuk mencatat tanggal laporan diajukan, tanggal dibayar, dan nominal yang benar-benar diterima. Selama belum selesai, Beranda menampilkan total uang yang masih menunggu reimburse atau yang perlu dikembalikan.",
    "Bagaimana menambahkan logo dan tanda tangan di PDF?" to
        "Buka Pengaturan → \"Logo & Tanda Tangan\". Pilih logo dari galeri (sebaiknya PNG transparan) dan gambar tanda tangan dengan jari. Keduanya otomatis tercetak di kop dan kolom \"Dilaporkan oleh\" pada laporan PDF.",
    "Bagaimana mengaktifkan pengingat?" to
        "Pengaturan → Pengingat → aktifkan \"Pengingat harian\" dan pilih jamnya. Selama dinas, Anda diingatkan jika belum mencatat pengeluaran hari itu; setelah pulang, diingatkan jika laporan belum ditandai \"Dilaporkan\". Izinkan notifikasi saat diminta.",
    "Bagaimana mengirim laporan ke WhatsApp atau Email?" to
        "Buka perjalanan → tombol \"Buat Laporan PDF\" → pilih \"Kirim via WhatsApp\" atau \"Kirim via Email\". File PDF otomatis terlampir beserta pesan pengantar yang bisa Anda ubah sebelum dikirim.",
    "Di mana data saya disimpan? Apakah butuh internet?" to
        "Semua data tersimpan lokal di memori internal HP Anda dan tidak dikirim ke server mana pun. Aplikasi bekerja penuh tanpa internet. Internet hanya dibutuhkan oleh WhatsApp/Email saat mengirim laporan.",
    "Bagaimana agar data tidak hilang saat ganti HP?" to
        "Buka Pengaturan → \"Ekspor Backup\" untuk menyimpan file .json ke Google Drive / penyimpanan. Di HP baru, pasang aplikasi lalu pilih \"Impor Backup\". Lakukan backup secara berkala.",
    "Bagaimana cara mengisi nominal?" to
        "Ketik angka saja tanpa titik, contoh 1500000. Aplikasi otomatis menampilkan 1.500.000. Untuk tarif per hari/malam, aktifkan \"Hitung otomatis\" di form anggaran (jumlah × tarif).",
    "Bagaimana mengubah atau menghapus data?" to
        "Ketuk item anggaran / pengeluaran untuk mengubah atau menghapus. Untuk mengubah data perjalanan, ketuk ikon pensil di kanan atas. Hapus perjalanan melalui menu titik tiga (⋮).",
    "Apa arti status perjalanan?" to
        "Rencana = belum berangkat. Berlangsung = sedang dinas. Selesai = sudah pulang, laporan belum dikirim. Dilaporkan = laporan sudah diserahkan ke kantor. Status bisa diubah di tab Ringkasan.",
    "Kenapa nama atasan di PDF kosong?" to
        "Isi Profil di menu Pengaturan (ikon roda gigi): nama perusahaan, jabatan, nama atasan & jabatannya. Data ini dipakai untuk kop laporan dan kolom tanda tangan.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit, onTutorial: () -> Unit) {
    var expanded by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bantuan & FAQ", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Baru pertama kali?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Alur singkat: Buat Perjalanan (No. ODR) → Isi Anggaran → Catat Pengeluaran → Kirim Laporan PDF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onTutorial) {
                            Icon(Icons.Filled.PlayCircle, null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Putar Tutorial")
                        }
                    }
                }
            }
            item { SectionTitle("Pertanyaan yang Sering Diajukan") }
            itemsIndexed(faqs) { i, (q, a) ->
                val open = expanded == i
                val rotation by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
                Surface(
                    onClick = { expanded = if (open) -1 else i },
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 0.5.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(q, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.rotate(rotation))
                        }
                        AnimatedVisibility(open) {
                            Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
