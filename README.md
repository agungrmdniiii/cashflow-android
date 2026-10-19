# Duit Aing

Aplikasi pencatatan keuangan pribadi untuk Android. Catat pemasukan dan pengeluaran, pisahkan saldo rekening, lalu periksa anggaran dan laporan. Data utama tersimpan di perangkat; tidak perlu membuat akun.

[Unduh APK uji coba](https://github.com/agungrmdniiii/cashflow-android/releases/download/v1.1.0-beta.1/DuitAing.apk) · [Laporkan masalah](https://github.com/agungrmdniiii/cashflow-android/issues)

## Video aplikasi

https://github.com/user-attachments/assets/f9e0fda8-0727-4432-98fe-afd1ebe67755

## Instalasi di HP

Memerlukan **Android 8.0 atau lebih baru**. Aplikasi menargetkan Android 16. Versi yang tersedia saat ini adalah **1.1.0 beta**, berupa APK debug untuk uji coba.

1. [Unduh DuitAing.apk](https://github.com/agungrmdniiii/cashflow-android/releases/download/v1.1.0-beta.1/DuitAing.apk). Jika membuka halaman Releases, pilih **DuitAing.apk** pada bagian **Assets**, bukan *Source code*.
2. Setelah unduhan selesai, buka APK dari notifikasi unduhan atau **Pengelola File → Download**.
3. Jika Android meminta izin, buka **Setelan → Izinkan dari sumber ini** untuk browser atau pengelola file yang dipakai, lalu kembali ke pemasangan.
4. Tekan **Instal**, tunggu selesai, lalu pilih **Buka**.
5. Buat rekening pertama dan isi saldo awal. Ikuti tur aplikasi, lalu gunakan tombol **+** untuk mencatat transaksi.

**Memperbarui aplikasi:** buat cadangan melalui **Pengaturan → Data & Cadangan**, lalu pasang APK baru di atas versi lama. Jangan hapus aplikasi terlebih dahulu. Pembaruan memerlukan tanda tangan APK yang sama; jika muncul pesan konflik paket atau aplikasi tidak terpasang, pastikan cadangan sudah tersimpan sebelum mencoba instalasi ulang.

## Yang bisa digunakan

- Pemasukan, pengeluaran, dan transfer antar rekening; tersedia input manual, suara, serta pemindaian struk.
- 50 kategori pengeluaran dan 30 pemasukan. Kategori yang sering dipakai naik ke pilihan awal berdasarkan transaksi tersimpan.
- Anggaran, utang/piutang, target tabungan, dan laporan dengan rentang tanggal yang bisa dipilih.
- Asisten **Vina** untuk membaca catatan keuangan. Coba “Pengeluaran bulan lalu”, lalu “Rincikan” atau “Kalau bulan ini?”. Mode lokal tidak memerlukan API key; mode online memakai Gemini dan diatur lewat **Pengaturan → AI & Suara**.
- Cadangan dan pemulihan data, ekspor PDF/CSV, tema gelap, serta kunci biometrik.

Vina lokal memakai aturan bahasa dan perhitungan transaksi. Jawabannya mengikuti data yang tercatat. Saat AI online diaktifkan, pertanyaan dan konteks keuangan dikirim ke provider. Pengenalan suara dan OCR mengikuti dukungan perangkat.

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

## Pengujian

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Verifikasi terakhir: **215 tes unit** dan **12 tes UI di emulator** lulus. Build debug berhasil; lint masih memiliki peringatan. Performa diuji pada POCO F6, tetapi pembukaan pertama masih dapat tersendat.

Tes UI memerlukan emulator atau perangkat. Gunakan emulator khusus untuk tes database audit karena sebagian tes mengubah data pengujian.

Untuk melaporkan bug, sertakan versi Android, versi aplikasi, dan langkah untuk mengulang masalahnya. Hapus informasi pribadi dari tangkapan layar atau contoh data.
