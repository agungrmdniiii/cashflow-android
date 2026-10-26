# Cadangan Duit Aing

Cadangan utama berupa arsip berisi data aplikasi dan foto struk. Menu **Pengaturan → Data & Cadangan** menyediakan penyimpanan di HP, Google Drive, dan pemulihan melalui satu pemilih berkas. JSON, ZIP, dan format terenkripsi lama tetap dikenali dari isinya.

## Di HP

Tekan **Cadangkan**, pilih lokasi melalui pemilih berkas Android, lalu simpan. Status berhasil baru berubah setelah aplikasi membaca kembali dan memverifikasi berkas. ZIP biasa tidak memakai kata sandi; simpan di lokasi pribadi. Format tambahan tetap tersedia melalui pilihan lanjutan.

Untuk pemulihan, tekan **Pulihkan**, pilih berkas, dan masukkan kata sandi bila diperlukan. Periksa ringkasan sebelum mengonfirmasi penggantian data. Membuka atau memvalidasi berkas belum mengubah catatan.

## Google Drive

Hubungkan akun melalui pemilih akun Android dan berikan izin cadangan aplikasi. Buat kata sandi pemulihan yang akan digunakan untuk membuka arsip di perangkat lain. Kata sandi ini berbeda dari kata sandi Google; aplikasi tidak meminta kata sandi Google. Simpan kata sandi pemulihan di tempat aman karena pengembang tidak dapat memulihkannya.

Cadangan Drive mencakup foto struk dan dienkripsi sebelum diunggah. Berkas berada di folder data khusus aplikasi (`appDataFolder`), sehingga tidak muncul sebagai folder biasa pada Drive. Gunakan **Pulihkan** di bagian Google Drive untuk melihat arsip yang tersedia. Koneksi Drive bersifat opsional.

Memutuskan koneksi menghentikan cadangan otomatis ke Drive dan menghapus pilihan akun dari aplikasi. Arsip yang sudah diunggah tetap berada di akun Google. Jika pencabutan izin di Google gagal karena jaringan, aplikasi menjelaskannya; pengguna dapat mencabut akses melalui pengaturan akun Google. Penghapusan data di aplikasi tidak menghapus cadangan eksternal.

## Otomatis

Aktifkan **Cadangan otomatis**, pilih tujuan, frekuensi harian/mingguan/bulanan, dan jendela waktu. Untuk penyimpanan lokal, pilih folder khusus yang dapat ditulis. Untuk Drive, pilih **Wi-Fi saja** atau **Wi-Fi & seluler**. Wi-Fi saja juga menolak jaringan berkuota dan jaringan dengan transport seluler; aplikasi tidak memintas VPN pengguna.

Rincian jadwal hanya tampil setelah cadangan otomatis aktif. Jika tujuan belum siap, aplikasi menawarkan pilihan HP atau Drive terlebih dahulu, kemudian meminta penyiapan folder atau akun yang sesuai. Membatalkan penyiapan tidak mengaktifkan jadwal. Mematikan otomatis menyembunyikan rincian tanpa menghapus pilihan sebelumnya.

Android menentukan waktu pelaksanaan dalam jendela tersebut berdasarkan jaringan, baterai, serta pembatasan sistem. Ini bukan alarm tepat waktu. Jika syarat belum terpenuhi, cadangan dicoba kembali; arsip lama tidak ditimpa. Status terakhir berhasil terpisah untuk HP dan Drive. Jadwal menggunakan waktu lokal, menyesuaikan perubahan zona waktu dan tanggal pendek pada bulan tertentu.

Jika percobaan otomatis gagal, aplikasi menampilkan pemberitahuan ringkas dengan **Coba lagi**, atau **Hubungkan ulang** jika Google memerlukan izin kembali. Menekan isi pemberitahuan membuka pengaturan cadangan. Gangguan yang sama tidak mengirim pemberitahuan berulang dalam satu jendela; perubahan menjadi masalah izin tetap memperbarui tindakan. Notifikasi memerlukan izin dan kanal yang aktif. Menunggu jaringan atau Doze bukan kegagalan.

**Coba lagi** meminta kesempatan pelaksanaan dalam satu jam berikutnya, termasuk di luar jendela biasa, dengan tetap mengikuti pilihan jaringan dan syarat baterai. Android menentukan waktu eksekusi; tombol ini tidak memaksa unggahan langsung. Tindakan lama tidak berlaku setelah cadangan berhasil, otomatis dinonaktifkan, atau tujuan diganti.

## Konfigurasi pengembang

- Proyek Google Cloud: `duit-aing-android-2026`.
- Google Drive API diaktifkan; aplikasi OAuth eksternal bernama **Duit Aing**.
- Client Android terikat pada package `com.cashflow.app` dan SHA-1 sertifikat APK yang dibagikan. APK dengan sertifikat berbeda memerlukan client Android lain pada proyek yang sama. Play App Signing nantinya mempunyai sertifikat distribusinya sendiri.
- Scope yang digunakan hanya `https://www.googleapis.com/auth/drive.appdata`. Aplikasi tidak meminta akses seluruh Drive.
- SDK resmi Google menangani otorisasi. Token dipakai sementara di memori; tidak ditulis ke kode, log, atau cadangan.
- Pilihan akun/folder dan kata sandi pemulihan yang dilindungi Android Keystore tersimpan khusus perangkat, dikecualikan dari ekspor dan pemindahan preferensi. Perangkat baru harus dihubungkan ulang.
- Tidak memerlukan service account, client secret Android, atau server penyimpan data keuangan.

Pada 26 Oktober 2026, setelah persetujuan pemilik, status OAuth telah diverifikasi **In production** dengan tipe pengguna **External**. Halaman informasi dan privasi sudah terbit, kepemilikan situs terverifikasi, serta URL dan domain tersimpan di Branding. Koneksi akun berbeda dari satu-satunya penguji lama telah diuji pada emulator Pixel 10 Pro dengan APK142: pengguna menyelesaikan persetujuan Google, status terhubung tampil, dan pembacaan daftar Drive berhasil dengan hasil belum ada cadangan. Uji lanjutan pada akun ini berhasil mengunggah dan mengunduh arsip contoh terenkripsi 257.031 byte, kemudian memulihkannya di basis data terpisah pada emulator baru; catatan utama tetap utuh. Kata sandi salah, arsip terpotong, dan pembatalan transfer parsial ditangani tanpa mengganti data tujuan. Percobaan foto lebih besar mengalami timeout/reset jaringan sehingga stabilitas cadangan besar belum dipastikan. Lihat [hasil uji pindah perangkat](DEVICE_RESTORE_AUDIT.md). Pengguna mengonfirmasi proses langsung ke persetujuan izin tanpa peringatan "Google hasn't verified this app" atau langkah Advanced/Lanjutan pada percobaan ini. Hasil ini berlaku untuk akun dan perangkat yang diuji. Pengujian unit/emulator tidak menggantikan pengujian unggah dan pemulihan Drive dengan akun Google nyata.

Pengujian langsung pada POCO F6 telah mencakup koneksi akun, pembacaan daftar, unggahan terenkripsi, unduhan kembali, pembukaan arsip dengan kata sandi oleh pengguna, dan validasi ringkasan/checksum. Pratinjau dibatalkan sebelum mengganti data di HP. Publikasi halaman saja dan aktivasi OAuth publik kemudian disetujui secara terpisah oleh pemilik; tidak ada APK baru yang dirilis untuk perubahan konfigurasi ini.

Pekerjaan otomatis Drive juga telah diuji langsung pada POCO F6: saat Wi-Fi mati pekerjaan menunggu, lalu berjalan tanpa pemicu paksa setelah Wi-Fi kembali ketika aplikasi berada di latar belakang. Pada uji Doze dengan penghemat baterai aktif, pekerjaan tertunda sesuai pembatasan Android dan berhasil setelah mode uji dilepas. Setelah restart HP dan pembukaan kunci pertama, jadwal tetap tersedia tanpa membuka aplikasi. Unggahan berikutnya selesai ketika layar mati; arsip baru terlihat di Drive, lalu jadwal berikutnya terbentuk. Pengaturan otomatis, Wi-Fi, dan mode daya awal dipulihkan setelah uji. HP terhubung USB; hasil ini belum mencakup penggunaan tanpa pengisi daya dalam waktu lama atau force-stop oleh sistem OEM.

Referensi: [Authorization Android](https://developer.android.com/identity/authorization), [folder data aplikasi Drive](https://developers.google.com/workspace/drive/api/guides/appdata), [pengelolaan branding OAuth](https://support.google.com/cloud/answer/15549049).
