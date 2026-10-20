# Perjadin — Catatan Perjalanan Dinas (Android)

Aplikasi Android offline untuk mencatat perjalanan dinas berbasis **No. ODR (On Duty Request)**:
anggaran per kategori, realisasi pengeluaran, penyelesaian uang muka, dan **laporan PDF**
yang bisa langsung dikirim ke WhatsApp / Email.

## Fitur

| Fitur | Keterangan |
|---|---|
| Perjalanan per No. ODR | Keperluan, rute, tanggal, pelaksana, uang muka, status (Rencana → Dilaporkan) |
| Anggaran | Plafon per kategori (7 kategori bawaan + **kategori buatan sendiri** dengan warna & ikon) + kalkulator jumlah × tarif |
| Pengeluaran | Tanggal, kategori, nominal, metode bayar, no. struk/invoice, peringatan jika melebihi anggaran |
| Ringkasan | Anggaran vs realisasi per kategori, % serapan, sisa / kelebihan, settlement uang muka |
| Laporan PDF (A4) | Kop perusahaan, rekap, rincian anggaran & pengeluaran, pernyataan, kolom tanda tangan |
| Berbagi | Kirim via WhatsApp, Email (PDF terlampir + pesan pengantar), aplikasi lain, lihat, simpan file |
| Onboarding & FAQ | Tutorial 5 langkah, data contoh, FAQ, placeholder berisi contoh input |
| Data lokal | JSON di internal storage (AtomicFile), ekspor / impor backup |

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

## Struktur

```
app/src/main/java/id/abati/perjadin/
├── data/      Models, Repository (penyimpanan JSON lokal), DemoData
├── report/    PdfReport (android.graphics.pdf), ShareHelper (WhatsApp/Email/FileProvider)
├── ui/        App (navigasi), theme, components, screens/*
└── util/      Fmt (format Rupiah & tanggal Indonesia)
```

Stack: Kotlin 2.0, Jetpack Compose (Material 3), minSdk 26 (Android 8.0), tanpa library pihak ketiga
selain AndroidX.
