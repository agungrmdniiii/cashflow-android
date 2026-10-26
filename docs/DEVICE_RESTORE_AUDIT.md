# Uji pindah perangkat — 26 Oktober 2026

APK aplikasi tetap build **142 / 1.0.0**. Tidak ada perubahan kode aplikasi, rilis, atau push GitHub dalam pengujian ini. Alat uji: `app/src/androidTest/java/com/cashflow/app/DeviceRestoreAuditTest.kt`.

## Hasil

- Sumber: emulator `Pixel_10_Pro:5556`, memakai akun Drive yang sebelumnya dihubungkan sendiri oleh pengguna dan berbeda dari penguji lama.
- Tujuan: emulator baru `DuitAing_Restore_Audit:5558`, identitas Android berbeda, tanpa akun Google, pilihan akun Drive, atau kata sandi pemulihan perangkat sumber. Keduanya memakai system image SDK 37 yang tersedia; ini bukan pengujian HP fisik Android 16.
- Seluruh pencatatan dan pemulihan memakai basis data sementara berisi contoh, bukan basis data utama aplikasi. Fingerprint catatan utama serta seluruh pengaturan akun, kata sandi terenkripsi, dan otomatisasi diperiksa tetap sama setelah setiap tahap.
- Arsip ZIP beserta JPEG contoh dienkripsi dengan kata sandi fixture khusus, diunggah ke `appDataFolder`, ditemukan dalam daftar Drive dengan ukuran yang sama, lalu diunduh kembali. SHA-256 sumber dan hasil unduhan cocok. Arsip berhasil berukuran **257.031 byte**.
- Unduhan Drive lain dibatalkan setelah **7.480 byte** telah ditulis. Operasi berakhir dan berkas parsial dibersihkan. Ini pengujian pembatalan transfer nyata, bukan simulasi pemadaman Wi-Fi atau batas waktu respons UI.
- Hasil unduhan yang telah diverifikasi dipindahkan ke emulator tujuan. Tidak ada substitusi dengan arsip sebelum unggah dan tidak ada penyalinan token, akun, Keystore, atau kata sandi pengguna.
- Pemulihan valid menghasilkan **5 transaksi, 2 rekening, kategori khusus, 1 anggaran, dan pengaturan portabel yang sama**. Seluruh isi portabel dibandingkan, termasuk ID, jumlah, tanggal, referensi, dan timestamp. Jalur foto dinormalisasi menjadi hash isi agar perpindahan lokasi sah tidak dianggap perubahan data.
- Foto hasil pemulihan dapat didekode sebagai JPEG dan hash-nya sesuai sumber.
- Saldo contoh: kas **Rp1.100.000**, bank **Rp582.500**. Pendapatan Oktober **Rp250.000**, pengeluaran Oktober **Rp50.000**, seluruh pengeluaran **Rp70.000**. Ringkasan, jumlah pada chart, dan rekonsiliasi sesuai; tidak ada selisih saldo.
- Kata sandi salah, arsip terenkripsi terpotong, ZIP terpotong, dan stream salin yang terputus ditolak. Basis data contoh yang sudah ada di tujuan tetap sama; berkas dekripsi/salin parsial dibersihkan.

## Batas yang masih penting

Percobaan awal dengan JPEG contoh 2048 × 2048 mengalami timeout saat meminta respons unduhan. Percobaan berikutnya mengalami koneksi terputus saat unggah. Percobaan JPEG 512 × 512 berhasil. Penyebab gangguan transfer ukuran besar belum dipastikan atau diperbaiki; hasil arsip kecil tidak membuktikan cadangan besar sudah stabil.

Emulator tujuan mengimpor berkas terenkripsi hasil unduhan dari sumber. Login Google dan unduhan Drive langsung pada perangkat tujuan belum diuji. Pengujian ini menjalankan jalur repository/enkripsi/Drive secara native, bukan interaksi seluruh dialog pemulihan melalui UI. Kata sandi fixture tidak menguji ingatan pengguna terhadap kata sandi cadangan pribadinya.

Pengujian tidak mengganti data utama pengguna atau memulihkan cadangan asli. Arsip contoh Drive dipertahankan; tidak ada arsip cloud yang dihapus. Emulator tujuan ditutup setelah artefak hasil disalin untuk membebaskan RAM komputer.

## Bukti dan pengulangan

Ringkasan mesin: `app/build/end-user-audit/device-restore/result.json`. Detail tahap: `source-result.json`, `destination-result.json`, dan log instrumentation pada folder yang sama. Log percobaan timeout/reset dan kesalahan pemilik folder ADB juga disimpan. Folder ADB dikoreksi dengan membuat direktori melalui UID aplikasi; tidak ada izin aplikasi yang dilonggarkan.

Bangun alat uji dengan JDK Android Studio:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :app:assembleDebugAndroidTest -PoptimizedReview=true --console=plain
```

Controller memerlukan opt-in `-e deviceRestoreAudit approved` dan menolak HP fisik. Tahap `source` mengunggah hanya contoh yang dibuat di basis data terpisah; perlu akun Drive yang telah disetujui sendiri oleh pengguna. Untuk mengulang, pindahkan dahulu artefak contoh dari folder `Android/data/com.cashflow.app/files/migration-audit` sehingga tidak bertabrakan dengan `transfer.dab` sebelumnya. Jangan menghapus database atau preferensi utama.

```powershell
adb -s emulator-5556 shell am instrument -w -r -e deviceRestoreAudit approved -e stage source -e class com.cashflow.app.DeviceRestoreAuditTest com.cashflow.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5558 shell am instrument -w -r -e deviceRestoreAudit approved -e stage prepare -e class com.cashflow.app.DeviceRestoreAuditTest com.cashflow.app.test/androidx.test.runner.AndroidJUnitRunner
```

Setelah `prepare`, salin hanya hasil unduhan `transfer.dab` dan manifest contoh `expected.json` ke direktori artefak milik aplikasi di tujuan, kemudian jalankan tahap `destination` dengan command yang sama. Kriteria lulus: `OK (1 test)` pada kedua tahap, `passed: true`, dan `portable_sha256` identik. Manifest tidak berisi data akun Google atau keuangan pengguna.
