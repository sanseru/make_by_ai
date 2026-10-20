## Perjadin v1.1.0 — Data aman, angka akurat, laporan siap kirim

> ⚠️ **Sebelum memasang:** ekspor backup di versi lama Anda (Pengaturan → Ekspor Backup) untuk berjaga-jaga. Update dapat dipasang langsung di atas versi lama; data tetap tersimpan.

### 🛡️ Keandalan data
- **Backup lengkap (.zip)**: data + foto struk + logo + tanda tangan, dengan checksum SHA-256 tiap file.
- **Restore aman**: file diperiksa dulu (versi, keutuhan, isi tidak aman) → pratinjau jumlah data → konfirmasi. Data saat ini **disalin otomatis** sebelum restore dan bisa dikembalikan lewat "Batalkan restore terakhir". Jika restore gagal di tengah jalan, data lama tetap utuh.
- Backup `.json` dari v1.0.x tetap bisa dipulihkan.
- File data yang rusak tidak lagi ditimpa: disimpan terpisah dan dipulihkan dari salinan valid terakhir.
- Format data berversi (v4) dengan migrasi otomatis; data dari versi aplikasi yang lebih baru dibuka read-only.
- Info "Backup terakhir" + pengingat di Beranda bila belum backup > 14 hari.

### 💰 Akurasi keuangan
- Anggaran **per hari** (hari kalender) dan **per malam** (hari − 1), otomatis dihitung ulang saat tanggal diubah.
- Status reimburse terpisah dari pengeluaran (tidak dihitung dua kali).
- Status perjalanan **Dibatalkan**.

### 📄 Laporan PDF
- Status uang muka & reimburse di rekap, logo, tanda tangan digital, lampiran foto bernomor sesuai rincian.
- Nama file: `Perjadin_<No-ODR>_<tanggal>.pdf`.

### ⚡ Pencatatan cepat & akses
- Form catat pengeluaran cepat, riwayat isian, "Simpan & Tambah Lagi", foto struk.
- Duplikat perjalanan sebagai template.
- Widget layar utama, shortcut ikon, pengingat harian.
- Kategori bisa ditambah & diubah (nama, ikon, warna).

### ✅ Pengujian
Lulus 30 unit test (keuangan, migrasi, backup) dan instrumented test di emulator Android 8 & Android 14.
Uji manual di HP sungguhan tetap disarankan: mode pesawat, ukuran teks besar, kirim PDF ke WhatsApp/Email.

### Catatan
- Tanda tangan digital = gambar tanda tangan pada PDF, **bukan** tanda tangan elektronik tersertifikasi.
- Data hanya ada di HP ini dan hilang bila aplikasi di-uninstall — lakukan backup rutin.
