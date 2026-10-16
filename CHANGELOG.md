# 📋 Changelog — Duit Aing

Format changelog ini mengacu pada standar [Keep a Changelog](https://keepachangelog.com/id/1.0.0/) dan [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Ditambahkan
- Tur pengguna baru 14 langkah, termasuk panduan formulir transaksi dan Vina, serta panduan fitur yang dapat dipilih.
- Vina lokal dengan pertanyaan berbasis riwayat, periode, kategori, rekening, perbandingan, dan pertanyaan lanjutan.
- Rentang tanggal laporan melalui kalender, impor CSV, serta pemeriksaan integritas dan regresi ledger.

### Diperbaiki
- Pengelompokan grafik mengikuti periode dan rentang laporan yang dipilih.
- Kategori bawaan lebih ringkas dan beragam, serta interaksi pemilihan kategori diperbaiki.
- Header Transaksi diringkas; pencarian, filter, dan menu tindakan disusun ulang.
- Pengaturan dikelompokkan ke halaman terpisah dengan transisi navigasi.
- Konsistensi saldo, pemulihan backup, penanganan jaringan, dan preferensi privasi diperkuat.
- README ditulis ulang sesuai implementasi, termasuk instruksi build, pengujian, dan batasan fitur online.

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
