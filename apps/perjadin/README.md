# Perjadin — Catatan Perjalanan Dinas (Android)

Aplikasi Android offline untuk mencatat perjalanan dinas berbasis **No. ODR (On Duty Request)**:
anggaran per kategori, realisasi pengeluaran, penyelesaian uang muka, dan **laporan PDF**
yang bisa langsung dikirim ke WhatsApp / Email.

## Fitur

| Fitur | Keterangan |
|---|---|
| Duplikat perjalanan | Salin perjalanan lama sebagai template dengan No. ODR & tanggal baru; anggaran per hari/malam dihitung ulang dengan pratinjau |
| Perjalanan per No. ODR | Keperluan, rute, tanggal, pelaksana, uang muka, status (Rencana → Dilaporkan) |
| Anggaran & kategori | Plafon per kategori: nominal tetap, per hari, per malam (otomatis ikut tanggal), atau jumlah × tarif. Semua kategori (bawaan & buatan sendiri) bisa diubah nama/ikon/warna |
| Pengeluaran | Tombol cepat di Beranda + form bottom sheet (nominal → kategori → simpan), "Simpan & Tambah Lagi", isi cepat dari riwayat, foto struk (kamera/galeri), peringatan jika melebihi anggaran |
| Ringkasan | Anggaran vs realisasi per kategori, % serapan, sisa / kelebihan, settlement uang muka |
| Laporan PDF (A4) | Kop perusahaan + logo, tanda tangan digital, rekap, rincian anggaran & pengeluaran, pernyataan, kolom tanda tangan, lampiran foto struk |
| Berbagi | Kirim via WhatsApp, Email (PDF terlampir + pesan pengantar), aplikasi lain, lihat, simpan file |
| Status reimburse | Catat tanggal diajukan, tanggal dibayar & nominal diterima; Beranda menampilkan total yang masih menunggu |
| Widget & shortcut | Widget 4×2 berisi perjalanan aktif, progres & sisa anggaran, tombol catat pengeluaran; shortcut tekan-lama ikon |
| Pengingat | Notifikasi harian: belum catat pengeluaran saat dinas, laporan belum dikirim setelah pulang |
| Onboarding & FAQ | Tutorial 5 langkah, data contoh, FAQ, placeholder berisi contoh input |
| Data lokal | JSON berversi di internal storage (AtomicFile), salinan terakhir yang valid, karantina file rusak, migrasi otomatis |
| Backup lengkap | `.zip` berisi `manifest.json` (SHA-256 tiap file), `data.json`, foto struk, logo, tanda tangan; restore dengan validasi, pratinjau, backup otomatis sebelum restore & tombol batalkan; backup `.json` lama tetap didukung |

## Install APK

1. Buka tab **Actions** / **Releases** repo ini → unduh `Perjadin-v1.0.N.apk` (dibuild otomatis oleh
   workflow `.github/workflows/perjadin-android.yml` setiap ada perubahan di folder ini).
2. Buka file APK di HP → izinkan *Install dari sumber tidak dikenal*.

APK ditandatangani dengan keystore `app/perjadin-debug.keystore` agar versi baru bisa di-install
di atas versi lama tanpa kehilangan data. Untuk kunci rilis sendiri, isi GitHub Secrets:
`PERJADIN_KEYSTORE_BASE64`, `PERJADIN_KEYSTORE_PASSWORD`, `PERJADIN_KEY_ALIAS`, `PERJADIN_KEY_PASSWORD`
(catatan: berganti kunci mengharuskan uninstall — ekspor backup terlebih dulu).

## Build lokal

Butuh JDK 17 + Android SDK (platform 35).

```bash
cd apps/perjadin
./gradlew assembleRelease
# hasil: app/build/outputs/apk/release/app-release.apk
```

## Pengujian

| Jenis | Lokasi | Cakupan |
|---|---|---|
| Unit test JVM | `app/src/test` | Aturan keuangan (per hari/malam, uang muka, reimburse, duplikat), migrasi data v1–v4, validasi, backup/restore ZIP (checksum, path traversal, format baru, rollback) |
| Instrumented | `app/src/androidTest` | PDF (80 transaksi + 7 foto), FileProvider, backup → restore lewat Repository; dijalankan CI di emulator **Android 8 (API 26)** dan **Android 14 (API 34)** |

```bash
./gradlew testDebugUnitTest              # unit test
./gradlew connectedDebugAndroidTest      # butuh emulator/HP terhubung
```

Rilis hanya dipublikasikan bila build, unit test, dan instrumented test di kedua emulator lulus.

## Struktur

```
app/src/main/java/id/abati/perjadin/
├── backup/    BackupManager (ZIP + manifest + validasi), BackupService (SAF)
├── data/      Models (+ versi data, migrasi, validasi), Repository, DemoData
├── reminder/  Pengingat harian (WorkManager)
├── widget/    Widget layar utama
├── report/    PdfReport (android.graphics.pdf), ShareHelper (WhatsApp/Email/FileProvider)
├── ui/        App (navigasi), theme, components, screens/*
└── util/      Fmt (format Rupiah & tanggal Indonesia)
```

Stack: Kotlin 2.0, Jetpack Compose (Material 3), minSdk 26 (Android 8.0), tanpa library pihak ketiga
selain AndroidX.
