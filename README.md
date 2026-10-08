# 💸 Duit Aing — Personal Cashflow & Wealth Tracker

<div align="center">

![Duit Aing Banner](docs/screenshots/screenshot_home_final.png)

**Aplikasi Pencatatan Keuangan Pribadi yang Cepat, Taktil, dan Mandiri (100% On-Device & Offline-First).**  
Dibangun dengan arsitektur modern Android Jetpack Compose, Material 3, dan gaya visual **Neo-Brutalist "Jade Pebble Morning"**.

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Latest-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Room-Offline%20First-4285F4.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## ✨ Fitur Utama

- 🎙️ **Voice-to-Transaction NLP (Bahasa Indonesia)**
  - Catat transaksi dalam hitungan detik hanya dengan suara (misal: *"Beli nasi padang 25 ribu pakai GoPay"* atau *"Token listrik 100 ribu bayar pakai BCA"*).
  - Diproses 100% secara lokal di perangkat tanpa mengirim data suara ke server eksternal.
- 📊 **Dasbor Finansial & Odometer Taktil**
  - Tampilan Total Saldo dengan animasi mekanis *Rolling Odometer* yang memuaskan dan responsif.
  - Fitur Privasi Saldo (Sensor/Masking) dengan satu ketukan.
  - Pemantauan alokasi dan batas anggaran bulanan per kategori secara real-time.
- 💼 **Manajemen Multi-Aset & Rekening**
  - Kelola rekening Bank, Dompet Digital (E-Wallet), Uang Tunai (Cash), hingga Investasi dalam satu tempat.
  - Transfer antar-rekening dengan pencatatan otomatis.
- 🎯 **Target Keuangan & Pencatatan Hutang/Piutang**
  - Lacak *Financial Goals* (misal: Dana Darurat, DP Rumah, Liburan) dengan visualisasi progres.
  - Pantau piutang yang dipinjamkan maupun hutang yang perlu dilunasi secara terstruktur.
- 🛡️ **Privasi Penuh & Pencegahan Kesalahan (Error Prevention)**
  - Tidak ada pelacak pihak ketiga (*zero telemetry*).
  - Seluruh tindakan destruktif dilindungi dialog konfirmasi bertema Neo-Brutalist.

---

## 📱 Tangkapan Layar (Screenshots)

<div align="center">

| Beranda & Anggaran | Manajemen Aset & Hutang | Detail Transaksi |
|:---:|:---:|:---:|
| <img src="docs/screenshots/screenshot_home_final.png" width="240" /> | <img src="docs/screenshots/screenshot_aset.png" width="240" /> | <img src="docs/screenshots/screenshot_tx.png" width="240" /> |

</div>

---

## 🛠️ Arsitektur & Teknologi

- **Bahasa**: Kotlin (Coroutines + Flow / StateFlow)
- **UI Framework**: Jetpack Compose (Material 3 + Custom Neo-Brutalist Components)
- **Arsitektur**: Clean Architecture MVVM (Model-View-ViewModel) + Single Source of Truth (SSOT)
- **Penyimpanan Lokal**: Room Database (SQLite Engine)
- **Animasi & Taktil**: Compose Animation (`animateIntAsState`, rolling digit transitions) + Haptic Feedback API

---

## 🚀 Cara Menjalankan Proyek

### Prasyarat
- Android Studio Ladybug (2024.2+) atau versi lebih baru
- JDK 17 atau JDK 21
- Android SDK 35 (Android 15)

### Langkah Instalasi
1. Clone repositori ini:
   ```bash
   git clone <URL_REPOSITORY_ANDA>
   cd CASH
   ```
2. Buka folder proyek di **Android Studio**.
3. Tunggu proses **Gradle Sync** selesai.
4. Hubungkan perangkat Android fisik atau jalankan Android Emulator (API 34+ direkomendasikan).
5. Klik tombol **Run** (`Shift + F10`) atau jalankan melalui terminal:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 Lisensi
Didistribusikan di bawah lisensi Apache License 2.0.
