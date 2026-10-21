# Pembaruan APK Duit Aing

Fitur ini ditujukan untuk distribusi APK kepada penguji. Pengguna membuka Pengaturan > Tentang Aplikasi > Pembaruan aplikasi, membaca catatan perubahan, mengunduh APK di aplikasi, dan menyetujui pemasangan melalui Android. Aplikasi tidak memasang pembaruan secara diam-diam.

## Versi dan kompatibilitas

`version.properties` menentukan nama versi dan nomor internal untuk build lokal. Versi awal fitur ini adalah 1.0.0 dengan nomor internal 111, lebih tinggi dari APK uji sebelumnya (110). Penamaan ulang versi tidak menurunkan nomor internal. GitHub Actions memilih nomor yang lebih besar dari build lokal dan rilis terakhir. Bila perlu menginstal APK lokal sesudah build CI, gunakan nomor yang lebih besar melalui `-PappVersionCode=...`.

APK baru harus memakai identitas paket dan kunci penandatanganan yang sama dengan APK penguji. Jangan mengganti debug keystore atau meminta pengguna uninstall untuk mengatasi ketidakcocokan. Sidik sertifikat publik yang diharapkan disimpan di `scripts/android-signing-cert.sha256`.

## Mengaktifkan penerbitan otomatis

Penerbitan repositori ini sudah diaktifkan. Langkah berikut menjadi acuan jika pengaturan repositori perlu dipulihkan.

1. Simpan debug keystore yang benar sebagai secret repositori `DUITAING_DEBUG_KEYSTORE_BASE64`. Jangan commit keystore atau menampilkan nilainya pada log. Workflow menggunakan password dan alias standar debug Android, dengan lokasi keystore yang ditentukan secara eksplisit untuk build CI.
2. Masukkan workflow dan perubahan aplikasi ke branch `main` repositori `agungrmdniiii/cashflow-android`.
3. Pastikan GitHub Actions diizinkan menjalankan workflow. Workflow memakai izin `contents: write` untuk membuat rilis.
4. Periksa keberhasilan workflow pertama serta aset `DuitAing.apk` dan `update.json`. Feed baru aktif setelah rilis selesai diterbitkan. Sebelum itu, aplikasi menampilkan bahwa paket pembaruan belum tersedia.

Untuk menambahkan secret tanpa menampilkan nilai di terminal, GitHub CLI dapat menerima base64 melalui stdin. Gunakan keystore yang sidik sertifikatnya cocok, bukan keystore baru yang dibuat di komputer lain.

## Setiap revisi berikutnya

- Ubah aplikasi dan tulis catatan perubahan aktual dalam `app/src/main/res/raw/release_notes.txt` dengan bahasa Indonesia yang profesional, ringkas, dan berorientasi pada manfaat pengguna.
- Ubah `versionName` di `version.properties` ketika menerbitkan nama versi baru; nomor internal CI bertambah otomatis.
- Push ke `main`. Workflow memeriksa catatan perubahan, menjalankan tes dan lint, membangun APK teroptimasi, memverifikasi sertifikat, lalu menerbitkan paket.
- Catatan yang sama dengan rilis terakhir ditolak agar revisi tidak diterbitkan dengan penjelasan usang. Push yang hanya mengubah dokumentasi tidak memicu penerbitan APK.
- Penerbitan memakai draft hingga kedua aset siap. Workflow tidak mengganti rilis yang telah diterbitkan.

Metadata mencakup nomor dan nama versi, API Android minimum, ukuran APK, SHA-256, URL unduhan, dan catatan perubahan. GitHub menyimpan hanya berkas aplikasi dan informasi rilis; pemeriksaan pembaruan tidak mengirim data transaksi, foto, atau cadangan.

## Unduhan dan pemasangan

Sumber unduhan dibatasi ke rilis repositori ini melalui HTTPS. APK ditulis ke cache privat secara streaming. Ukuran dan checksum harus cocok; identitas paket, versi, dan sertifikat APK harus sesuai sebelum tombol pemasangan tersedia. Berkas yang rusak, tidak lengkap, atau dibatalkan dihapus. Unduhan berjalan selama proses aplikasi masih hidup dan dapat dilanjutkan di tampilan pengaturan saat pengguna berpindah menu; setelah proses dihentikan, pengguna dapat memulai unduhan ulang.

Android mengelola izin pemasangan dari sumber ini dan konfirmasi installer. Jika izin ditolak, pengguna mendapat penjelasan dan dapat mencoba kembali. Membatalkan installer tidak menghapus data aplikasi. Tidak ada migrasi database baru pada fitur pembaruan ini.

## Google Play

Izin `REQUEST_INSTALL_PACKAGES` dan antarmuka pembaruan APK hanya aktif pada build debug untuk distribusi uji. Build release tidak membawa izin tersebut. Jika aplikasi didistribusikan melalui Play Store, gunakan mekanisme pembaruan Google Play, bukan pemasang APK mandiri.

## Pemeriksaan

- `python -m unittest discover -s scripts -p test_update_release.py`
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -PoptimizedReview=true`
- Android: `AppUpdateTest` memeriksa identitas/sertifikat/versi APK dan intent installer tanpa meluncurkan pemasangan atau menulis transaksi.
