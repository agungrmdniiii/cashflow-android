# Spesifikasi Desain: Fitur Hutang (Debt Tracker) — Duit Aing

- **Tanggal**: 2026-10-07
- **Status**: Disetujui (Approved)
- **Target Platform**: Android (Jetpack Compose, SQLite via `DatabaseHelper`)
- **Penempatan Fitur**: Layar Aset & Saldo (`AssetsScreen.kt`) dengan Segmented Switcher

---

## 1. Latar Belakang & Tujuan

Aplikasi "Duit Aing" membutuhkan sarana pencatatan kewajiban finansial berupa **Hutang Saya** (uang yang dipinjam dari pihak/orang lain yang harus dikembalikan atau dicicil).

### Tujuan (Goals)
1. Memberikan kemampuan kepada pengguna untuk mencatat hutang secara mandiri (*standalone debt tracker*).
2. Menyimpan detail pinjaman: keterangan/tujuan hutang, nama pemberi pinjaman (*lender*), nominal total pokok pinjaman, jumlah yang telah dicicil/terbayar, tanggal jatuh tempo, status pelunasan, dan catatan.
3. Memungkinkan pencatatan cicilan/pembayaran parsial secara bertahap hingga lunas, dengan update status otomatis (`unpaid` -> `partial` -> `paid`).
4. Menyediakan ringkasan finansial kewajiban: Total Sisa Hutang, Total Sudah Terbayar, Jumlah Hutang Aktif, Indikator Jatuh Tempo, dan estimasi Kekayaan Bersih Riil (`Total Aset - Total Sisa Hutang`).
5. Mempertahankan filosofi visual **Refined Neo-Brutalism** dengan palet warna **Jade Pebble Morning** serta interaksi selector dan kartu yang rapi dan elegan.

### Batasan & Non-Goals
- **Standalone Tracker**: Pencatatan pembayaran hutang tidak secara otomatis memotong saldo aset kas/bank pengguna. Pengguna mengelola hutang sebagai catatan kewajiban tersendiri.
- **Tanpa Piutang (Receivables)**: Versi ini hanya memfokuskan pada kewajiban hutang pengguna ke pihak lain, bukan piutang yang dipinjamkan ke orang lain.
- **Zero Breakage**: Tidak mengubah atau merusak struktur tabel yang sudah ada (`assets`, `categories`, `transactions`, `budgets`, `financial_goals`).

---

## 2. Arsitektur Data & Skema Database

### 2.1 Model Data (`Debt.kt` & `DebtStatus.kt`)
File baru di `com.cashflow.app.data.model.Debt.kt`:

```kotlin
package com.cashflow.app.data.model

import java.time.LocalDate

enum class DebtStatus(val key: String, val label: String) {
    UNPAID("unpaid", "Belum Lunas"),
    PARTIAL("partial", "Sebagian"),
    PAID("paid", "Lunas")
}

data class Debt(
    val id: String,
    val title: String,               // Contoh: "Pinjam Renovasi", "Cicilan Laptop"
    val lenderName: String,          // Contoh: "Budi", "Bank Mandiri"
    val totalAmount: Long,           // Total nominal pinjaman awal
    val paidAmount: Long = 0L,       // Total nominal yang telah dicicil/dibayar
    val dueDate: String,             // Format ISO: "YYYY-MM-DD"
    val status: String = "unpaid",   // "unpaid" | "partial" | "paid"
    val note: String = "",           // Catatan opsional
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val remainingAmount: Long
        get() = (totalAmount - paidAmount).coerceAtLeast(0L)

    val progressPercentage: Float
        get() = if (totalAmount > 0) {
            (paidAmount.toFloat() / totalAmount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isOverdue: Boolean
        get() = try {
            if (status == "paid") false
            else LocalDate.parse(dueDate).isBefore(LocalDate.now())
        } catch (_: Exception) {
            false
        }
}
```

### 2.2 Skema SQLite & Migrasi (`DatabaseHelper.kt`)
- Naikkan `DATABASE_VERSION` dari `1` ke `2`.
- Tambahkan konstanta nama tabel: `const val TABLE_DEBTS = "debts"`.
- DDL tabel `debts`:
  ```sql
  CREATE TABLE debts (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      lender_name TEXT NOT NULL,
      total_amount INTEGER NOT NULL,
      paid_amount INTEGER NOT NULL DEFAULT 0,
      due_date TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'unpaid',
      note TEXT DEFAULT '',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
  );
  ```
- Implementasi `onUpgrade`:
  ```kotlin
  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      if (oldVersion < 2) {
          db.execSQL(
              """
              CREATE TABLE $TABLE_DEBTS (
                  id TEXT PRIMARY KEY,
                  title TEXT NOT NULL,
                  lender_name TEXT NOT NULL,
                  total_amount INTEGER NOT NULL,
                  paid_amount INTEGER NOT NULL DEFAULT 0,
                  due_date TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'unpaid',
                  note TEXT DEFAULT '',
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
              );
              """.trimIndent()
          )
      }
  }
  ```
- Query dan pemetaan helper:
  - `cursorToDebt(cursor: Cursor): Debt`
  - `getAllDebts(): List<Debt>`
  - `insertDebt(debt: Debt): Long`
  - `updateDebt(debt: Debt): Int`
  - `recordDebtPayment(id: String, paymentAmount: Long): Boolean`
  - `deleteDebt(id: String): Int`

---

## 3. Logika Bisnis & ViewModel

### 3.1 Repository (`CashflowRepository.kt`)
- Aliran data reaktif:
  - `private val _debts = MutableStateFlow<List<Debt>>(emptyList())`
  - `val debts: StateFlow<List<Debt>> = _debts.asStateFlow()`
- Pemanggilan `loadFromDb()` mencakup pemuatan tabel `debts`, diurutkan dengan `status ASC, due_date ASC`.
- Metode CRUD:
  - `suspend fun addDebt(title: String, lenderName: String, totalAmount: Long, dueDate: String, note: String)`
  - `suspend fun recordDebtPayment(debtId: String, amount: Long)`
  - `suspend fun updateDebt(debt: Debt)`
  - `suspend fun deleteDebt(debtId: String)`

### 3.2 State & Operasi di ViewModel (`CashflowViewModel.kt`)
- State streams:
  - `val debts: StateFlow<List<Debt>> = repository.debts`
  - `val totalRemainingDebt: StateFlow<Long>` (hasil akumulasi `remainingAmount` semua hutang)
  - `val totalPaidDebt: StateFlow<Long>` (hasil akumulasi `paidAmount` semua hutang)
  - `val activeDebtsCount: StateFlow<Int>` (jumlah hutang dengan status != "paid")
  - `val overdueDebtsCount: StateFlow<Int>` (jumlah hutang yang `isOverdue == true`)
  - `val netWorth: StateFlow<Long>` (`totalWealth - totalRemainingDebt`)
- UI and Dialog States:
  - `val assetScreenSubTab = MutableStateFlow(0)` (0: ASET SAYA, 1: HUTANG SAYA)
  - `val selectedDebtFilter = MutableStateFlow("ALL")` ("ALL", "ACTIVE", "PAID")
  - `val isAddDebtOpen = MutableStateFlow(false)`
  - `val payingDebt = MutableStateFlow<Debt?>(null)`
- Aksi:
  - `fun setAssetScreenSubTab(tabIndex: Int)`
  - `fun setDebtFilter(filter: String)`
  - `fun addDebt(title: String, lenderName: String, totalAmount: Long, dueDate: String, note: String)`
  - `fun recordDebtPayment(debtId: String, amount: Long)`
  - `fun deleteDebt(debtId: String)`

---

## 4. Spesifikasi UI & Interaksi (`AssetsScreen.kt`)

### 4.1 Header & Segmented Switcher
- Header memiliki dynamic label:
  - Sub-tab 0: `ASET & SALDO` / `SUMBER DANA & SIMPANAN` dengan tombol `+ ASET`
  - Sub-tab 1: `HUTANG SAYA` / `CATATAN KEWAJIBAN PEMBAYARAN` dengan tombol `+ HUTANG`
- Segmented Switcher ditempatkan langsung di bawah header:
  - Pill/Tab button dengan border hitam 1.5dp, latar aktif `JadePrimary` teks putih, latar non-aktif transparan teks `DarkSurface`.

### 4.2 Tampilan "HUTANG SAYA"
1. **Summary Card (Editorial Neo-Brutalist)**:
   - Judul: "TOTAL KEWAJIBAN HUTANG"
   - Angka Pokok Sisa: `Rp X.XXX.XXX` (bold, besar, kontras)
   - Badge / Metadata: "Kekayaan Bersih Riil: Rp (Aset - Hutang)"
   - 2 Sub-metrik:
     - "Sudah Dicicil": Rp X.XXX.XXX (Aksen Jade)
     - "Hutang Aktif": X Pinjaman (Merah bila ada yang jatuh tempo)
2. **Filter Chips**:
   - Chips: `SEMUA` | `BELUM LUNAS` | `LUNAS`
3. **Debt Item Card (`DebtItemCard`)**:
   - Container: NeoCard dengan border 1.5dp, shadow 2dp.
   - Baris 1: Judul pinjaman (Bold), Badge status (`Lunas` warna Jade, `Sebagian` warna Amber, `Belum Lunas` warna Dark/Red, plus badge peringatan `Jatuh Tempo` jika tanggal sudah lewat).
   - Baris 2: Nama pemberi pinjaman ("Pemberi pinjaman: [Nama]").
   - Baris 3: Sisa hutang (Besar) & Total pinjaman ("Sisa: Rp X / Total: Rp Y").
   - Baris 4: Progress bar cicilan (Custom LinearProgressIndicator dengan warna `JadePrimary` dan track `SurfaceNeutral`).
   - Baris 5: Tanggal jatuh tempo ("Jatuh tempo: [Tanggal]").
   - Aksi:
     - Tombol `[ CICIL / BAYAR ]` (hanya aktif jika sisa hutang > 0).
     - Tombol Ikon Hapus / Opsi dengan konfirmasi.
4. **Empty State**:
   - Jika daftar kosong: Ilustrasi/Ikon centang dengan pesan: *"Tidak ada catatan hutang. Keuangan Anda bebas dari beban pinjaman."*

### 4.3 Dialogs
1. **`AddDebtDialog`**:
   - Input judul pinjaman
   - Input nama pemberi pinjaman
   - Input total pinjaman (keyboard numeric)
   - Input tanggal jatuh tempo (dengan default hari ini + 30 hari atau pemilih tanggal)
   - Input catatan opsional
   - Validasi error jika nominal <= 0 atau field wajib kosong
2. **`PayDebtDialog`**:
   - Menampilkan sisa pokok hutang
   - Input nominal pembayaran yang ingin dicicil
   - Chip cepat: `[ Lunasi Semua (Rp ...) ]`
   - Validasi: nominal harus > 0 dan tidak boleh melebihi sisa hutang
   - Tombol konfirmasi pencatatan cicilan

---

## 5. Rencana Pengujian & Verifikasi
1. **Database Unit Tests**:
   - Verifikasi migrasi versi 1 ke versi 2 membuat tabel `debts` tanpa merusak tabel lain.
   - Verifikasi operasi insert, update, delete, dan perhitungan `recordDebtPayment`.
2. **Model Unit Tests**:
   - Verifikasi helper properties `remainingAmount`, `progressPercentage`, dan `isOverdue`.
3. **ViewModel Integration Tests**:
   - Verifikasi perhitungan `totalRemainingDebt`, `totalPaidDebt`, `activeDebtsCount`, dan `netWorth`.
   - Verifikasi pencatatan pembayaran cicilan mengubah status hutang secara tepat (unpaid -> partial -> paid).
4. **Build & Lint Verification**:
   - Menjalankan `./gradlew.bat testDebugUnitTest` dan `./gradlew.bat assembleDebug` untuk memastikan kompilasi 100% bebas error.
