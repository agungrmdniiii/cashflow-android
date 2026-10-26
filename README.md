# Duit Aing

Aplikasi pencatatan keuangan pribadi untuk Android. Catat pemasukan dan pengeluaran, pisahkan saldo rekening, lalu periksa anggaran dan laporan. Data utama tersimpan di perangkat; tidak perlu membuat akun.

[Unduh APK uji coba](https://github.com/agungrmdniiii/cashflow-android/releases/latest/download/DuitAing.apk) · [Laporkan masalah](https://github.com/agungrmdniiii/cashflow-android/issues)

## Video aplikasi

https://github.com/user-attachments/assets/f9e0fda8-0727-4432-98fe-afd1ebe67755

## Instalasi di HP

Memerlukan **Android 8.0 atau lebih baru**. Aplikasi menargetkan Android 16. Versi **1.1.0** tersedia sebagai APK untuk uji coba.

1. [Unduh DuitAing.apk](https://github.com/agungrmdniiii/cashflow-android/releases/latest/download/DuitAing.apk). Jika membuka halaman Releases, pilih **DuitAing.apk** pada bagian **Assets**, bukan *Source code*.
2. Setelah unduhan selesai, buka APK dari notifikasi unduhan atau **Pengelola File → Download**.
3. Jika Android meminta izin, buka **Setelan → Izinkan dari sumber ini** untuk browser atau pengelola file yang dipakai, lalu kembali ke pemasangan.
4. Tekan **Instal**, tunggu selesai, lalu pilih **Buka**.
5. Buat rekening pertama dan isi saldo awal. Ikuti tur aplikasi, lalu gunakan tombol **+** untuk mencatat transaksi.

**Memperbarui aplikasi:** mulai versi 1.0.0, buka **Pengaturan → Tentang Aplikasi → Pembaruan aplikasi**. Baca catatan perubahan, unduh, lalu setujui pemasangan Android. Untuk APK yang belum memiliki pemeriksaan pembaruan, pasang APK terbaru sekali secara manual di atas aplikasi yang ada. Jangan hapus aplikasi terlebih dahulu. Jika muncul konflik paket, hubungi pengembang untuk memperoleh APK dengan tanda tangan yang sesuai. Cadangan dapat dibuat melalui **Pengaturan → Data & Cadangan**.

## Yang bisa digunakan

- Pemasukan, pengeluaran, dan transfer antar rekening; tersedia input manual, suara, serta pemindaian struk.
- 62 kategori pengeluaran dan 36 pemasukan. Tandai kategori favorit agar tampil lebih dahulu; kategori lainnya diurutkan menurut penggunaan.
- Anggaran, utang/piutang, target tabungan, dan laporan dengan rentang tanggal yang bisa dipilih.
- Asisten **Vina** untuk membaca catatan keuangan sepenuhnya offline, tanpa kunci API. Coba “Pengeluaran bulan lalu”, lalu “Rincikan” atau “Kalau bulan ini?”.
- Cadangan lokal dan Google Drive beserta foto struk, dengan jadwal otomatis serta pilihan jaringan. Format JSON, ZIP, dan arsip terenkripsi tetap didukung. [Cara pencadangan dan pemulihan](docs/GOOGLE_DRIVE_BACKUP.md).
- Riwayat pembaruan yang dapat dibuka kembali dari Tentang Aplikasi; catatan yang sudah tersimpan tetap tersedia saat offline.
- Empat pilihan widget: Saldo & pintasan, Saldo ringkas, Catat cepat, dan Ringkasan bulanan. Tombol mata menyembunyikan nominal pada widget keuangan.
- Ekspor PDF/CSV, tema gelap, dan kunci biometrik.

Google Drive memerlukan akun dan izin pengguna sendiri. Transfer cadangan dengan foto besar belum konsisten dalam pengujian; pastikan cadangan berhasil sebelum mengandalkannya untuk pindah perangkat. [Hasil uji pemulihan](docs/DEVICE_RESTORE_AUDIT.md).

Vina memakai aturan bahasa dan perhitungan transaksi di perangkat. Percakapan dan data keuangan tidak dikirim ke penyedia AI. Jawabannya mengikuti data yang tercatat; Vina belum dapat menjawab semua pertanyaan bebas. Pengenalan suara dan OCR mengikuti dukungan perangkat dan pengaturannya sendiri.

## Menjalankan kode sumber

Siapkan **Android Studio**, **Android SDK 36**, dan **JDK 25**. Versi Gradle dan dependensi mengikuti konfigurasi proyek.

```bash
git clone https://github.com/agungrmdniiii/cashflow-android.git
cd cashflow-android
```

Buka proyek di Android Studio, atur lokasi SDK jika diminta, lalu tunggu sinkronisasi selesai. Build APK debug melalui terminal proyek:

```powershell
# Windows
.\gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

Hasilnya berada di `app/build/outputs/apk/debug/app-debug.apk`. Untuk memasang lewat USB, aktifkan USB debugging pada perangkat dan jalankan:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Jika lebih dari satu perangkat terhubung, gunakan `adb -s SERIAL install -r ...`. Opsi `-r` memperbarui pemasangan yang kompatibel tanpa menghapus data.

Untuk menilai kelancaran di HP, buat APK review teroptimasi. Lokasi APK dan tanda tangan pemasangan tetap sama:

```powershell
.\gradlew.bat :app:assembleDebug -PoptimizedReview=true
```

Mode review menonaktifkan debugger dan mengaktifkan optimasi kode. Jalankan build biasa tanpa properti tersebut saat membutuhkan debugger Android Studio.

## Pengujian

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Validasi lokal versi **1.1.0 build 143**: **308 tes unit** dan **4 tes skrip pembaruan** lulus. Lint lengkap tidak memiliki error dan masih mencatat **186 peringatan**. Pengujian native sebelumnya pada build 142 mencakup pemulihan arsip contoh di dua emulator, integritas foto, perhitungan saldo/laporan, dan perlindungan data saat pemulihan gagal. [Rincian serta batas pengujian](docs/DEVICE_RESTORE_AUDIT.md). Hasil ini tidak menjamin perilaku yang sama pada semua HP; build 143 belum diuji langsung pada POCO F6.

Tes UI memerlukan emulator atau perangkat. Gunakan emulator khusus untuk tes database audit karena sebagian tes mengubah data pengujian.

Untuk melaporkan bug, sertakan versi Android, versi aplikasi, dan langkah untuk mengulang masalahnya. Hapus informasi pribadi dari tangkapan layar atau contoh data.

## Pembaruan dari aplikasi

Buka **Pengaturan → Tentang Aplikasi → Pembaruan aplikasi** untuk memeriksa versi terbaru, membaca catatan perubahan, dan mengunduh APK. Android meminta konfirmasi sebelum memasang pembaruan. Fitur ini mulai tersedia pada versi 1.0.0; penguji APK yang belum memiliki fitur ini perlu memasang APK terbaru satu kali secara manual. [Panduan penerbitan otomatis](docs/ANDROID_UPDATES.md).
