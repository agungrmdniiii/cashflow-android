# Fitur Hutang (Debt Tracker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menambahkan fitur Hutang Saya (standalone debt tracker) pada aplikasi Android Duit Aing di tab Aset & Saldo dengan segmented switcher, pelacakan sisa pinjaman & cicilan, indikator jatuh tempo, dan metrik kekayaan bersih riil.

**Architecture:** Model data `Debt` dan enum `DebtStatus` disimpan dalam tabel SQLite `debts` (`DatabaseHelper` v2). `CashflowRepository` mengalirkan StateFlow ke `CashflowViewModel` yang mengkalkulasi metrik sisa hutang dan kekayaan bersih. UI diintegrasikan ke dalam `AssetsScreen` menggunakan segmented switcher dengan Neo-Brutalist design tokens.

**Tech Stack:** Kotlin, Jetpack Compose, SQLite (`SQLiteOpenHelper`), AndroidViewModel, Kotlin Coroutines & StateFlow, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-07-fitur-hutang-design.md`

## Global Constraints

- Standalone Tracker: Pembayaran hutang tidak otomatis mengurangi saldo aset kas/bank pengguna.
- Backward Compatibility: `DATABASE_VERSION` dinaikkan ke `2`, skema tabel lama (`assets`, `categories`, `transactions`, `budgets`, `financial_goals`) tetap utuh.
- Color Palette: Strictly Jade Pebble Morning (`PebbleBackground`, `DarkSurface`, `JadePrimary`, `BorderDark`, `StatusNegative`, `StatusWarning`).
- Spacing & Typography: Mengikuti Neo-Brutalism tokens dan global spacing terpusat (8dp, 12dp, 16dp, 20dp, 24dp).

## Review Focus

- Skenario pelunasan cicilan yang melebihi sisa hutang: `paidAmount` dibatasi maksimal sebesar `totalAmount`, status otomatis menjadi `paid`.
- Skenario format tanggal jatuh tempo tidak valid atau tanggal lampau: penanganan exception ISO_LOCAL_DATE yang aman tanpa force close, dan status `isOverdue` bernilai true hanya jika belum lunas (`status != "paid"`).
- Skenario daftar hutang kosong: Empty state informatif yang ramah dan rapi ("Tidak ada catatan hutang. Keuangan Anda bebas dari beban pinjaman").
- Sinkronisasi state: Setelah penambahan atau pembayaran hutang, `totalRemainingDebt` dan `netWorth` langsung ter-update secara reaktif pada UI.
- Validasi form tambah hutang: Nominal <= 0 atau nama peminjam kosong harus menampilkan peringatan dan mencegah insert.

---

### Task 1: Debt Data Model & Helper Properties

**Files:**
- Create: `app/src/main/java/com/cashflow/app/data/model/Debt.kt`
- Create: `app/src/test/java/com/cashflow/app/DebtBusinessRulesTest.kt`

**Interfaces:**
- Produces:
  - `enum class DebtStatus(val key: String, val label: String)`: `UNPAID("unpaid", "Belum Lunas")`, `PARTIAL("partial", "Sebagian")`, `PAID("paid", "Lunas")`
  - `data class Debt(val id: String, val title: String, val lenderName: String, val totalAmount: Long, val paidAmount: Long = 0L, val dueDate: String, val status: String = "unpaid", val note: String = "", val createdAt: Long, val updatedAt: Long)`
  - Properties: `remainingAmount: Long`, `progressPercentage: Float`, `isOverdue: Boolean`

- [ ] **Step 1: Write failing unit test for Debt business rules**

Tambahkan file `app/src/test/java/com/cashflow/app/DebtBusinessRulesTest.kt`:
```kotlin
package com.cashflow.app

import com.cashflow.app.data.model.Debt
import com.cashflow.app.data.model.DebtStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DebtBusinessRulesTest {

    @Test
    fun testDebtRemainingAmountAndProgress() {
        val debt = Debt(
            id = "debt_1",
            title = "Pinjam Renovasi",
            lenderName = "Budi",
            totalAmount = 10000000L,
            paidAmount = 2500000L,
            dueDate = LocalDate.now().plusDays(30).toString(),
            status = DebtStatus.PARTIAL.key
        )
        assertEquals(7500000L, debt.remainingAmount)
        assertEquals(0.25f, debt.progressPercentage, 0.001f)
        assertFalse(debt.isOverdue)
    }

    @Test
    fun testDebtOverdueDetection() {
        val overdueDebt = Debt(
            id = "debt_2",
            title = "Cicilan Laptop",
            lenderName = "Toko",
            totalAmount = 5000000L,
            paidAmount = 1000000L,
            dueDate = LocalDate.now().minusDays(5).toString(),
            status = DebtStatus.PARTIAL.key
        )
        assertTrue(overdueDebt.isOverdue)

        val paidPastDebt = overdueDebt.copy(status = DebtStatus.PAID.key, paidAmount = 5000000L)
        assertFalse(paidPastDebt.isOverdue)
    }

    @Test
    fun testNetWorthCalculation() {
        val totalAssets = 25000000L
        val totalRemainingDebt = 7500000L
        val netWorth = totalAssets - totalRemainingDebt
        assertEquals(17500000L, netWorth)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat testDebugUnitTest --tests com.cashflow.app.DebtBusinessRulesTest"`
Expected: FAIL with "Unresolved reference: Debt"

- [ ] **Step 3: Implement `Debt.kt` and `DebtStatus.kt` in `app/src/main/java/com/cashflow/app/data/model/Debt.kt`**

Implementasikan `data class Debt` dan `DebtStatus` sesuai spesifikasi dengan helper properties `remainingAmount`, `progressPercentage`, dan `isOverdue`.

- [ ] **Step 4: Run test to verify it passes**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat testDebugUnitTest --tests com.cashflow.app.DebtBusinessRulesTest"`
Expected: PASS

---

### Task 2: Database Schema & Migration in `DatabaseHelper.kt`

**Files:**
- Modify: `app/src/main/java/com/cashflow/app/data/db/DatabaseHelper.kt`

**Interfaces:**
- Consumes: `Debt`, `DebtStatus`
- Produces:
  - `DatabaseHelper.TABLE_DEBTS = "debts"`
  - `DatabaseHelper.DATABASE_VERSION = 2`
  - `DatabaseHelper.createDebtsTable(db: SQLiteDatabase)`
  - `DatabaseHelper.cursorToDebt(cursor: Cursor): Debt`
  - `DatabaseHelper.getAllDebts(): List<Debt>`
  - `DatabaseHelper.insertDebt(debt: Debt): Long`
  - `DatabaseHelper.updateDebt(debt: Debt): Int`
  - `DatabaseHelper.recordDebtPayment(id: String, paymentAmount: Long): Boolean`
  - `DatabaseHelper.deleteDebt(id: String): Int`

- [ ] **Step 1: Inspect and update table constants and onCreate / onUpgrade**

Ubah `DATABASE_VERSION = 2`. Tambahkan `TABLE_DEBTS = "debts"`.
Tambahkan DDL tabel `debts` pada `onCreate(db)` dan migrasi aman pada `onUpgrade(db, oldVersion, newVersion)`.

- [ ] **Step 2: Implement CRUD operations in `DatabaseHelper.kt`**

Implementasikan helper methods:
- `cursorToDebt(cursor: Cursor): Debt`
- `getAllDebts(): List<Debt>`
- `insertDebt(debt: Debt): Long`
- `updateDebt(debt: Debt): Int`
- `recordDebtPayment(id: String, paymentAmount: Long): Boolean`:
  Mengambil data hutang saat ini, menambahkan `paid_amount`, menentukan status (`paid` jika `paidAmount >= totalAmount`, jika tidak `partial`), dan meng-update row SQLite.
- `deleteDebt(id: String): Int`

- [ ] **Step 3: Verify build compilation**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

---

### Task 3: Repository Layer Integration (`CashflowRepository.kt`)

**Files:**
- Modify: `app/src/main/java/com/cashflow/app/data/repository/CashflowRepository.kt`

**Interfaces:**
- Consumes: `DatabaseHelper`, `Debt`, `DebtStatus`
- Produces:
  - `val debts: StateFlow<List<Debt>>`
  - `suspend fun addDebt(title: String, lenderName: String, totalAmount: Long, dueDate: String, note: String)`
  - `suspend fun recordDebtPayment(debtId: String, amount: Long)`
  - `suspend fun updateDebt(debt: Debt)`
  - `suspend fun deleteDebt(debtId: String)`

- [ ] **Step 1: Add `_debts` StateFlow and loading logic in `CashflowRepository.kt`**

Tambahkan:
```kotlin
private val _debts = MutableStateFlow<List<Debt>>(emptyList())
val debts: StateFlow<List<Debt>> = _debts.asStateFlow()
```
Di dalam `loadFromDb()`, muat data dari tabel `TABLE_DEBTS` dengan pengurutan `CASE WHEN status = 'paid' THEN 1 ELSE 0 END ASC, due_date ASC` dan perbarui `_debts.value`.

- [ ] **Step 2: Add mutation methods in `CashflowRepository.kt`**

Implementasikan:
- `suspend fun addDebt(title: String, lenderName: String, totalAmount: Long, dueDate: String, note: String)`
- `suspend fun recordDebtPayment(debtId: String, amount: Long)`
- `suspend fun updateDebt(debt: Debt)`
- `suspend fun deleteDebt(debtId: String)`

- [ ] **Step 3: Verify compilation**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

---

### Task 4: ViewModel Layer Integration (`CashflowViewModel.kt`)

**Files:**
- Modify: `app/src/main/java/com/cashflow/app/ui/CashflowViewModel.kt`

**Interfaces:**
- Consumes: `CashflowRepository.debts`, `Debt`, `DebtStatus`
- Produces:
  - `val debts: StateFlow<List<Debt>>`
  - `val totalRemainingDebt: StateFlow<Long>`
  - `val totalPaidDebt: StateFlow<Long>`
  - `val activeDebtsCount: StateFlow<Int>`
  - `val overdueDebtsCount: StateFlow<Int>`
  - `val netWorth: StateFlow<Long>`
  - `val assetScreenSubTab: MutableStateFlow<Int>` (0: ASET SAYA, 1: HUTANG SAYA)
  - `val selectedDebtFilter: MutableStateFlow<String>` ("ALL", "ACTIVE", "PAID")
  - `val isAddDebtOpen: MutableStateFlow<Boolean>`
  - `val payingDebt: MutableStateFlow<Debt?>`
  - `fun setAssetScreenSubTab(tab: Int)`
  - `fun setDebtFilter(filter: String)`
  - `fun addDebt(title: String, lenderName: String, totalAmount: Long, dueDate: String, note: String)`
  - `fun recordDebtPayment(debtId: String, amount: Long)`
  - `fun deleteDebt(debtId: String)`

- [ ] **Step 1: Add StateFlows and computed values to `CashflowViewModel.kt`**

Koneksikan repository `debts`, kalkulasikan `totalRemainingDebt`, `totalPaidDebt`, `activeDebtsCount`, `overdueDebtsCount`, dan kombinasikan `totalWealth` dengan `totalRemainingDebt` untuk menghasilkan `netWorth`.

- [ ] **Step 2: Add actions and modal states to `CashflowViewModel.kt`**

Tambahkan method `addDebt`, `recordDebtPayment`, dan `deleteDebt` dengan `viewModelScope.launch` dan notifikasi `showSnackbar`.

- [ ] **Step 3: Verify unit tests and compilation**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat testDebugUnitTest"`
Expected: BUILD SUCCESSFUL

---

### Task 5: UI Components (`DebtComponents.kt` & Dialogs)

**Files:**
- Create: `app/src/main/java/com/cashflow/app/ui/components/DebtComponents.kt`

**Interfaces:**
- Consumes: `Debt`, `DebtStatus`, `CashflowViewModel`, design tokens (`PebbleBackground`, `JadePrimary`, `DarkSurface`, `StatusNegative`, dll.)
- Produces:
  - `@Composable fun DebtSummaryCard(totalRemaining: Long, totalPaid: Long, activeCount: Int, netWorth: Long)`
  - `@Composable fun DebtItemCard(debt: Debt, onPayClick: () -> Unit, onDeleteClick: () -> Unit)`
  - `@Composable fun AddDebtDialog(isOpen: Boolean, onDismiss: () -> Unit, onSave: (title: String, lender: String, amount: Long, dueDate: String, note: String) -> Unit)`
  - `@Composable fun PayDebtDialog(debt: Debt?, onDismiss: () -> Unit, onConfirmPay: (debtId: String, amount: Long) -> Unit)`

- [ ] **Step 1: Implement `DebtSummaryCard` and `DebtItemCard` in `DebtComponents.kt`**

Terapkan styling *Refined Neo-Brutalism* dengan borders, rounded corners, linear progress bar pelunasan, status badges, dan badge peringatan jika hutang telah jatuh tempo (`isOverdue`).

- [ ] **Step 2: Implement `AddDebtDialog` and `PayDebtDialog` in `DebtComponents.kt`**

Sediakan field input judul, nama pemberi pinjaman, nominal pinjaman, tanggal jatuh tempo (format ISO atau date selector), dan catatan opsional. Pada `PayDebtDialog`, sertakan quick chip "Lunasi Semua" untuk pengalaman 1-tap.

- [ ] **Step 3: Verify compilation**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat compileDebugKotlin"`
Expected: BUILD SUCCESSFUL

---

### Task 6: Screen Integration in `AssetsScreen.kt` & Full Verification

**Files:**
- Modify: `app/src/main/java/com/cashflow/app/ui/screens/AssetsScreen.kt`

**Interfaces:**
- Consumes: `DebtComponents`, `CashflowViewModel`

- [ ] **Step 1: Integrate Segmented Switcher in `AssetsScreen.kt`**

Tambahkan switcher `[ ASET SAYA ]` | `[ HUTANG SAYA ]` di bawah header `AssetsScreen`.
Ubah teks header dan tombol kanan atas secara dinamis sesuai sub-tab yang dipilih:
- Saat sub-tab 0 (ASET SAYA): "+ ASET"
- Saat sub-tab 1 (HUTANG SAYA): "+ HUTANG"

- [ ] **Step 2: Render Debt Section when sub-tab 1 is active**

Ketika sub-tab 1 aktif:
- Render `DebtSummaryCard`
- Render filter chips `[ SEMUA ]`, `[ BELUM LUNAS ]`, `[ LUNAS ]`
- Render list `DebtItemCard` dengan empty state jika list kosong
- Render dialog `AddDebtDialog` dan `PayDebtDialog`

- [ ] **Step 3: Run full unit test suite**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat testDebugUnitTest"`
Expected: All tests PASS

- [ ] **Step 4: Run full debug build verification**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr&& gradlew.bat assembleDebug"`
Expected: BUILD SUCCESSFUL (APK generated with zero errors)
