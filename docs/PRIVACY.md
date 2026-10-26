# Kebijakan Privasi Duit Aing

Terakhir diperbarui: 25 Oktober 2026.

Duit Aing adalah aplikasi pencatatan keuangan pribadi untuk Android yang dikembangkan melalui proyek [cashflow-android](https://github.com/agungrmdniiii/cashflow-android). Aplikasi dapat dipakai tanpa membuat akun Duit Aing. Koneksi Google Drive bersifat opsional.

## Data di perangkat

Catatan transaksi, rekening, anggaran, utang/piutang, target, foto struk, dan preferensi disimpan di perangkat. Vina memproses pertanyaan dan data keuangan secara offline di perangkat. Percakapan tersebut tidak dikirim ke penyedia AI.

Pengguna dapat mengekspor atau membagikan data melalui tindakan yang dipilih sendiri. Berkas ekspor dan cadangan lokal biasa dapat memuat informasi keuangan serta foto struk tanpa enkripsi. Penerima dan lokasi penyimpanannya ditentukan pengguna.

## Cadangan Google Drive

Setelah pengguna menghubungkan akun dan memberi izin, Duit Aing menggunakan akses `drive.appdata` untuk menyimpan dan membaca cadangan aplikasi pada folder data khusus aplikasi di akun tersebut. Izin ini tidak memberikan akses ke seluruh berkas Drive pengguna.

Cadangan berisi data aplikasi dan foto struk, dienkripsi di perangkat menggunakan kata sandi pemulihan sebelum dikirim melalui HTTPS ke Google Drive. Aplikasi menggunakan identitas akun yang dipilih dan metadata berkas seperti nama, ukuran, serta waktu pembuatan untuk menampilkan dan memulihkan cadangan. Tidak ada server pengembang yang menerima salinan cadangan. Penyimpanan serta layanan akun Google mengikuti kebijakan Google.

Kata sandi Google tidak diminta atau disimpan oleh Duit Aing. Kata sandi pemulihan dilindungi Android Keystore pada perangkat agar pencadangan otomatis dapat berjalan. Token akses Google dipakai sementara dan tidak dimasukkan ke arsip cadangan. Pengguna harus menghubungkan ulang akun dan memasukkan kata sandi pemulihan ketika berpindah perangkat.

Data Google digunakan untuk menyediakan fungsi pencadangan dan pemulihan yang diminta pengguna. Data tersebut tidak dijual, tidak digunakan untuk iklan atau pelatihan model AI, dan tidak dibagikan kepada pengembang. Penggunaan data Google oleh Duit Aing mematuhi [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), termasuk ketentuan Limited Use.

## Izin dan layanan perangkat

- **Mikrofon:** digunakan ketika pengguna memulai pencatatan suara. Pengenalan mengikuti layanan suara Android yang tersedia dan dapat melibatkan pemrosesan oleh penyedia layanan tersebut. Ini terpisah dari Vina offline.
- **Foto dan kamera:** foto yang dipilih atau diambil digunakan untuk struk. Pembacaan teks memakai Google ML Kit di perangkat; model pendukung dapat diunduh melalui layanan Google.
- **Notifikasi:** digunakan untuk pengingat yang diaktifkan pengguna. Pengguna dapat menolak atau mematikan izin dari pengaturan Android.
- **Berkas dan folder:** akses cadangan memakai pemilih berkas Android. Cadangan otomatis lokal memakai folder yang secara khusus diizinkan pengguna.
- **Biometrik:** kunci aplikasi menggunakan mekanisme Android. Duit Aing tidak memperoleh templat sidik jari atau wajah.
- **Pembaruan:** pemeriksaan dan pengunduhan APK berkomunikasi dengan GitHub. GitHub dapat menerima informasi koneksi seperti alamat IP sesuai kebijakannya.

Pencadangan atau pemindahan data bawaan Android juga dapat berlaku sesuai pengaturan perangkat dan akun pengguna. Aturan aplikasi mengecualikan preferensi akun cadangan, kata sandi yang dilindungi perangkat, dan kredensial dari proses tersebut. Mekanisme ini berbeda dari cadangan Drive yang dibuat langsung oleh Duit Aing.

## Kendali dan penghapusan

Pengguna dapat menonaktifkan jadwal, memutuskan koneksi Drive, atau mencabut izin melalui pengaturan akun Google. Memutuskan koneksi tidak otomatis menghapus arsip yang sudah tersimpan. Arsip Drive dipertahankan sampai dihapus pengguna melalui pengelolaan data aplikasi di Google Drive atau mekanisme Google yang tersedia.

Penghapusan data melalui aplikasi menghapus data lokal yang dicakup tindakan tersebut, setelah konfirmasi. Berkas yang sudah diekspor, dibagikan, atau dicadangkan di luar aplikasi tetap harus dihapus dari lokasinya masing-masing. Pengembang tidak dapat membuka cadangan terenkripsi tanpa kata sandi pemulihan pengguna.

## Pertanyaan dan perubahan

Pertanyaan mengenai kebijakan ini dapat disampaikan melalui [halaman proyek Duit Aing](https://github.com/agungrmdniiii/cashflow-android/issues). Jangan sertakan catatan keuangan, kata sandi, atau informasi pribadi pada laporan publik. Kontak dukungan yang terdaftar juga tersedia pada layar persetujuan Google.

Perubahan kebijakan akan dicantumkan pada halaman ini beserta tanggal pembaruannya.
