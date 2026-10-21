# AutoCare — Catatan & Pengingat Kendaraan

Aplikasi Android *offline-first* untuk buku servis digital: riwayat perawatan, penggantian komponen, biaya, dan pengingat servis berbasis tanggal **dan/atau** kilometer. Tanpa akun, server, atau internet.

## Fitur (MVP)

| Area | Isi |
|---|---|
| Beranda | Ringkasan kendaraan, odometer terakhir, pengingat terdekat/terlambat, biaya bulan & tahun ini, servis terakhir, kondisi komponen, tombol catat cepat |
| Kendaraan | Multi-mobil, profil lengkap (nopol, VIN, no. mesin, BBM, transmisi, foto), arsip untuk mobil yang dijual, hapus dengan konfirmasi |
| Odometer | Riwayat km gabungan (manual, servis, inspeksi, pengeluaran), peringatan bila nilai lebih rendah/tidak masuk akal |
| Pengingat | Waktu, km, atau keduanya (mana yang lebih dulu); sekali/berulang; tahapan *segera → jatuh tempo → terlambat*; tunda; data parsial tanpa mengarang |
| Servis | Satu kunjungan berisi banyak pekerjaan; tindakan *diganti/dipasang/diservis/diperbaiki/diperiksa/direncanakan*; draft vs selesai; biaya suku cadang × jumlah + jasa + biaya tambahan − diskon; garansi; nota & foto |
| Komponen | Episode pemasangan per komponen (riwayat lama tidak pernah ditimpa), umur pakai, status normal/segera/terlambat, timeline |
| Biaya | Rekap per kendaraan & periode, per kategori, tren bulanan, pengeluaran non-bengkel (pajak, asuransi, aksesori, dll) |
| Riwayat | Timeline servis, pengeluaran, inspeksi; cari berdasarkan pekerjaan, komponen, bengkel, tanggal, km; filter periode/jenis/bengkel |
| Inspeksi | Checklist mandiri (ban, lampu, rem, cairan, wiper, kebocoran, dll) + item sendiri |
| Laporan | PDF A4 per kendaraan: identitas, ringkasan biaya, status jadwal, riwayat servis, pengeluaran, komponen, inspeksi |
| Backup | ZIP berisi `manifest.json` + `data.json` + seluruh lampiran; restore divalidasi (versi, SHA-256, relasi) sebelum diterapkan; mode *gabung* (tanpa duplikat) atau *ganti semua* |
| Notifikasi | WorkManager harian (jam dapat diatur) + pengecekan segera setelah km/servis berubah; pengingat update odometer & backup |

Katalog bawaan (oli & filter, ban & kaki-kaki, kelistrikan, mesin & pendinginan, rem & keselamatan, transmisi, AC, bodi, modifikasi, administrasi pajak/asuransi) dapat diubah dan ditambah. **Interval bawaan hanya titik awal, bukan rekomendasi universal.**

## Arsitektur

| Lapisan | Teknologi / lokasi |
|---|---|
| UI | Jetpack Compose + Material 3, Navigation Compose (`ui/`) |
| Data | Room/SQLite (`data/AppDatabase.kt`, `data/Entities.kt`), DataStore untuk pengaturan |
| Domain | Kotlin murni & teruji: `domain/ReminderEngine.kt`, `domain/Money.kt`, `domain/Insights.kt` |
| Backup | `backup/BackupArchive.kt` (JVM murni) + `backup/BackupService.kt` (SAF) |
| Pengingat | `reminder/Reminders.kt` (WorkManager + notifikasi lokal) |
| Laporan | `report/PdfReport.kt` (`android.graphics.pdf`, tanpa library) |

Keputusan desain penting:

- **ID UUID string** untuk semua entitas → restore gabung tidak menggandakan data dan siap untuk sinkronisasi cloud.
- **Jatuh tempo tidak disimpan**, melainkan dihitung dari acuan awal + pekerjaan selesai terbaru yang tertaut. Koreksi/hapus servis otomatis memperbarui pengingat.
- **Episode komponen**: penggantian menutup episode lama (`removed*`, `removedByTaskId`) dan membuat episode baru; menghapus servis memulihkan episode lama.
- Rupiah & km disimpan sebagai `Long`; jumlah suku cadang dalam seperseribu; pembulatan HALF_UP per baris.

## Build & test

```bash
cd apps/autocare
./gradlew testDebugUnitTest          # unit test JVM: reminder, biaya, pencarian, backup
./gradlew connectedDebugAndroidTest  # Room & repository di emulator/perangkat
./gradlew assembleRelease            # APK (ditandatangani keystore debug repo bila tanpa secret)
```

CI: `.github/workflows/autocare-android.yml` menjalankan unit test, build APK, instrumented test di API 26 & 34, lalu merilis APK ke GitHub Releases (branch `main` / manual). Kunci rilis opsional via secrets `AUTOCARE_KEYSTORE_BASE64`, `AUTOCARE_KEYSTORE_PASSWORD`, `AUTOCARE_KEY_ALIAS`, `AUTOCARE_KEY_PASSWORD`.

Minimum Android 8.0 (API 26), target API 35.
