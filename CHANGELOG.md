# 📋 Changelog — Duit Aing

Format changelog ini mengacu pada standar [Keep a Changelog](https://keepachangelog.com/id/1.0.0/) dan [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.1.0] - 2026-10-27 · build 143

### Ditambahkan

- Cadangan lengkap lokal dan Google Drive, termasuk foto struk. Arsip Drive dienkripsi memakai kata sandi pemulihan; akses Google bersifat opsional dan terbatas pada folder data aplikasi.
- Jadwal cadangan harian, mingguan, atau bulanan dengan jendela waktu dan pilihan jaringan. Rincian jadwal hanya tampil saat otomatis aktif.
- Notifikasi kegagalan cadangan dengan tindakan Coba lagi atau Hubungkan ulang; notifikasi berulang dibatasi dan percobaan ulang mengikuti batasan Android.
- Riwayat pembaruan pada Tentang Aplikasi, termasuk catatan versi sebelumnya dan akses offline untuk riwayat yang tersimpan.

### Diperbaiki

- Pertanyaan lanjutan Vina offline mempertahankan konteks topik, kategori, rekening, dan periode. Rekomendasi pertanyaan tetap tersedia dan menyesuaikan hasil percakapan.
- Ekspor CSV/PDF mengikuti pencarian dan filter transaksi, termasuk hasil kosong. Ekspor dari Pengaturan mengikuti periode laporan aktif.
- Kekayaan bersih di menu Aset mengikuti perhitungan saldo rekening, sisa piutang, dan sisa utang yang sama dengan widget.
- Mode sembunyikan nominal ikut menyamarkan judul dan isi insight serta teks aksesibilitasnya.
- Judul, tombol, tab, dan badge rekening menyesuaikan layar sempit serta ukuran huruf besar.
- Pemulihan cicilan melalui Urungkan mempertahankan kaitan utang/piutang sehingga edit atau penghapusan berikutnya tetap memperbarui saldo dengan benar.
- Menu Data & Cadangan menggunakan baris pengaturan dan tombol ringkas yang konsisten dengan halaman Pengaturan lainnya.

### Catatan pengujian

- Akun Google di luar penguji lama berhasil terhubung dan membaca daftar cadangan; pengguna mengonfirmasi tidak ada langkah Advanced untuk melewati peringatan aplikasi belum diverifikasi pada percobaan tersebut.
- Arsip contoh 257.031 byte berhasil melalui unggah/unduh Drive dan pemulihan ke basis data terpisah pada emulator lain. Transaksi, rekening, foto, saldo, laporan, dan pengaturan portabel sesuai sumber.
- Kata sandi salah, arsip terpotong, dan pembatalan unduhan parsial tidak mengganti data tujuan. [Rincian pengujian](docs/DEVICE_RESTORE_AUDIT.md).

### Keterbatasan yang diketahui

- Transfer arsip dengan foto besar mengalami timeout atau koneksi terputus dalam pengujian; kestabilannya belum dipastikan.
- Login dan pengunduhan Drive langsung pada emulator tujuan belum diuji. Pengujian lintas perangkat memakai berkas terenkripsi yang sudah diunduh serta diverifikasi di sumber.

## [1.0.0] - 2026-10-08

### 🎉 Rilis Perdana (Initial Release)
- **Dasbor Finansial**:
  - Saldo Total dengan animasi taktil *Mechanical Rolling Odometer*.
  - Mode sensor privasi saldo sekali ketuk.
  - Ringkasan pemasukan & pengeluaran bulanan.
  - Pemantauan alokasi dan batas anggaran kategori bulanan (maks 2 kartu di beranda).
- **Catat Suara NLP (Voice-to-Transaction)**:
  - Input suara lokal berbahasa Indonesia (tanpa internet / on-device).
  - Ekstraksi nominal, kategori, dan rekening otomatis.
- **Manajemen Aset & Dompet**:
  - Dukungan multi-rekening (Bank, E-Wallet, Tunai, Investasi).
  - Transfer saldo antar rekening.
- **Target Finansial & Hutang/Piutang**:
  - Pelacakan tabungan target (*Financial Goals*).
  - Manajemen hutang & piutang dengan pelunasan bertahap.
- **Penyempurnaan UX (Impeccable Polish)**:
  - Pencegahan kesalahan (*Error Prevention*): Dialog konfirmasi sebelum menghapus anggaran atau target finansial.
  - Validasi form anggaran & aset (tombol dinonaktifkan saat input belum valid).
  - Responsif font saldo 34sp untuk mencegah angka terpotong pada berbagai skala layar.
