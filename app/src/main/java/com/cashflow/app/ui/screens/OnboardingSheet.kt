package com.cashflow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashflow.app.ui.theme.duit

enum class AppTourStep(
    val targetId: Int, val tab: Int, val title: String,
    val description: String, val example: String
) {
    BALANCE(5, 0, "Pahami saldo awal",
        "Saldo awal adalah uang yang sudah ada saat kamu mulai memakai aplikasi. Total saldo menjumlahkan rekening aktif, bukan pemasukan bulan ini.",
        "Punya Rp300.000 di dompet? Isi sebagai saldo awal, jangan catat lagi sebagai pemasukan."),
    ASSETS(3, 3, "Pisahkan tempat uangmu",
        "Buat aset untuk setiap dompet, bank, atau e-wallet. Pilih rekening yang benar saat mencatat agar saldo masing-masing tetap sesuai.",
        "Contoh: Cash untuk tunai, BCA untuk bank, dan GoPay untuk e-wallet."),
    ADD(4, 0, "Mulai dari tombol +",
        "Tombol + membuka formulir pencatatan. Berikutnya kita telusuri bagian formulir dengan contoh pengeluaran, tanpa menyimpannya.",
        "Sesudah tur, kamu bisa mencatat transaksi sungguhan."),
    TYPE(10, 0, "Pilih jenis yang tepat",
        "Pengeluaran untuk uang yang dibelanjakan. Pemasukan untuk uang yang diterima. Transfer untuk memindahkan uang antar rekening sendiri.",
        "Gaji = pemasukan. Beli makan = pengeluaran. BCA ke GoPay = transfer, agar tidak dihitung sebagai belanja."),
    AMOUNT(11, 0, "Isi nominal transaksi",
        "Isi jumlah uang untuk satu transaksi. Ketik angka tanpa Rp atau titik. Tombol +20rb menambahkan Rp20.000 ke nominal yang sudah terisi.",
        "Contoh: makan siang Rp20.000. Contoh tur tidak disimpan."),
    CATEGORY(12, 0, "Pilih kategori sesuai tujuan",
        "Kategori membantu laporan mengelompokkan uangmu. Pilih Makanan untuk makan siang. Tekan Lihat semua bila kategori belum terlihat.",
        "Rekening menjawab uangnya dari mana; kategori menjawab uangnya untuk apa."),
    ACCOUNT(13, 0, "Tentukan rekening asal",
        "Pilih rekening yang benar-benar dipakai membayar. Saldo rekening itu akan berkurang setelah transaksi disimpan.",
        "Bayar tunai: pilih Cash. Bayar dari bank: pilih rekening bank. Saat transfer, pilih juga rekening tujuan yang berbeda."),
    DETAILS(14, 0, "Periksa tanggal dan catatan",
        "Buka Tambah Detail untuk mengubah tanggal, menulis keterangan, memberi tag, atau melampirkan struk. Transaksi lama perlu tanggal yang sesuai.",
        "Contoh keterangan: Makan siang. Catat belanja kemarin dengan tanggal kemarin, bukan hari ini."),
    REVIEW(15, 0, "Periksa sebelum menyimpan",
        "Pastikan jenis, nominal, kategori, rekening, dan tanggal benar. Tombol simpan aktif saat isian wajib valid. Setelah disimpan, saldo dan laporan ikut berubah.",
        "Ini masih contoh tur: tombol simpan tidak menjalankan transaksi. Tekan Lanjut untuk melihat cara mengecek hasilnya."),
    TRANSACTIONS(16, 1, "Cek dan temukan catatan",
        "Transaksi tersimpan muncul di sini. Cari lewat kolom pencarian; tombol filter membatasi tanggal, kategori, atau rekening. Ketuk catatan untuk melihat detail.",
        "Salah nominal atau rekening? Buka detail lalu edit transaksi itu, bukan membuat catatan pengganti yang menggandakan jumlah."),
    AI_ENTRY(18, 0, "Kenalan dengan Vina",
        "Tekan ikon bintang di bagian atas Beranda untuk membuka Tanya Vina. Kamu bisa menanyakan pengeluaran, pemasukan, kategori, dan kondisi keuangan dari catatan aplikasi.",
        "Berikutnya kita lihat layar Vina. Tur ini tidak mengirim pertanyaan atau memanggil API."),
    AI_MODE(21, 0, "Kenali mode Vina",
        "Vina membaca catatan langsung di perangkat, tanpa internet dan kunci API. Tanyakan pengeluaran, pemasukan, anggaran, hutang, atau target.",
        "Sebutkan periode dan rekening bila perlu. Vina memakai data tercatat; periksa catatan jika hasilnya belum sesuai."),
    AI_QUESTION(20, 0, "Tanya dengan periode jelas",
        "Ketik pertanyaan di kolom bawah, lalu tekan kirim. Sebutkan periode, kategori, atau rekening agar hasilnya lebih tepat. Jawaban mengikuti transaksi yang sudah tercatat.",
        "Coba: Berapa pengeluaran saya bulan lalu? Lanjutkan: Kalau bulan ini? Jika hasilnya kosong, periksa tanggal dan data transaksi."),
    REPORTS(17, 2, "Baca pola pengeluaran",
        "Pilih periode laporan atau gunakan kalender untuk rentang sendiri. Kategori terbesar menunjukkan tujuan belanja; tren menunjukkan perubahan dari waktu ke waktu.",
        "Laporan kosong? Periksa periode dan tanggal transaksi. Transfer antar rekening sendiri tidak dihitung sebagai pemasukan atau pengeluaran.");

    val isTransactionForm: Boolean get() = targetId in 10..15
    val isChatPreview: Boolean get() = targetId in 20..21
}

@Composable
fun AppGuideSheet(onStartTour: () -> Unit, onDismiss: () -> Unit) {
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    val duit = MaterialTheme.duit
    val topics = remember {
        listOf(
            "Anggaran belanja" to listOf(
                "Dari Beranda, buka bagian Anggaran lalu pilih Tambah.",
                "Pilih kategori, batas belanja, serta periode mingguan atau bulanan. Contoh: Makanan Rp500.000 per bulan.",
                "Pengeluaran pada kategori dan periode itu mengisi pemakaian anggaran secara otomatis. Anggaran adalah batas rencana, bukan rekening atau uang tambahan."
            ),
            "Target tabungan" to listOf(
                "Buka Aset lalu gulir ke bagian Target Keuangan untuk membuat target baru.",
                "Isi nama, nominal tujuan, dan tanggal target. Contoh: Dana darurat Rp3.000.000.",
                "Saat menambah tabungan, pilih rekening sumber yang benar. Cek progres target dan saldo rekening setelah mencatat setoran."
            ),
            "Hutang & piutang" to listOf(
                "Buka Aset, lalu pilih tab Hutang / Piutang.",
                "Hutang berarti kamu meminjam dan perlu membayar. Piutang berarti orang lain meminjam uangmu.",
                "Isi pihak terkait, nominal, dan jatuh tempo. Catat pembayaran melalui fitur ini agar sisa pinjaman ikut berkurang; hindari mencatat pembayaran yang sama dua kali."
            ),
            "Tanya Vina" to listOf(
                "Tekan ikon Tanya Vina di bagian atas Beranda.",
                "Coba: Berapa pengeluaran saya bulan lalu? Atau: Kategori apa yang paling banyak saya belanjakan minggu ini?",
                "Vina bekerja sepenuhnya offline dengan catatan di aplikasi. Hasil bergantung pada tanggal, kategori, dan kelengkapan transaksi."
            ),
            "Backup & pemulihan" to listOf(
                "Cadangkan sebelum ganti HP atau menghapus aplikasi.",
                "Buka Pengaturan > Data & Cadangan. Buat backup, pilih lokasi penyimpanan, lalu pastikan file berhasil tersimpan. Simpan salinan di luar HP bila perlu.",
                "Untuk memulihkan, pilih file backup dan periksa ringkasan sebelum melanjutkan. Pemulihan mengganti data yang ada; buat cadangan data sekarang terlebih dahulu."
            ),
            "Catat lebih cepat" to listOf(
                "Gunakan Suara di Beranda untuk mendikte transaksi, lalu periksa nominal, kategori, rekening, dan tanggal sebelum menyimpan.",
                "Di formulir transaksi, buka Tambah Detail untuk melampirkan atau memindai struk. Selalu periksa hasil pembacaan.",
                "Menu titik tiga di Transaksi menyediakan jadwal berulang dan pengelolaan pintasan untuk pencatatan yang sering dipakai."
            )
        )
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = duit.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Panduan aplikasi", style = MaterialTheme.typography.titleLarge, color = duit.textPrimary)
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("Mulai dengan pencatatan pertama, lalu pelajari fitur lain saat dibutuhkan.",
                        style = MaterialTheme.typography.bodyLarge, color = duit.textSecondary)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onStartTour, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Mulai tur langkah demi langkah")
                    }
                    Spacer(Modifier.height(24.dp))
                    topics.forEachIndexed { index, (title, paragraphs) ->
                        HorizontalDivider(color = duit.border)
                        TextButton(onClick = { expanded = if (expanded == index) -1 else index },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(if (expanded == index) "Tutup" else "Baca", style = MaterialTheme.typography.labelMedium)
                        }
                        if (expanded == index) {
                            paragraphs.forEachIndexed { number, paragraph ->
                                Text("${number + 1}. $paragraph", style = MaterialTheme.typography.bodyMedium,
                                    color = duit.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Highlights measured navigation controls on the actual app, without writing sample data. */
@Composable
fun OnboardingSheet(
    step: Int,
    target: Rect?,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onOpenGuide: (() -> Unit)? = null
) {
    val duit = MaterialTheme.duit
    val page = AppTourStep.entries[step]
    val density = LocalDensity.current
    val bodyScroll = key(step) { rememberScrollState() }
    BackHandler { if (step > 0) onBack() else onDismiss() }
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .semantics { paneTitle = "Tur aplikasi" }
            .pointerInput(target, onNext) {
                detectTapGestures { point -> if (target?.contains(point) == true) onNext() }
            }
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        val isDetail = page.isTransactionForm || page.isChatPreview
        val cardAtTop = isDetail && target != null && target.center.y > heightPx * 0.48f
        val room = with(density) {
            (if (cardAtTop) target!!.top else heightPx - (target?.bottom ?: 0f)).toDp()
        }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val cardHeight = if (isDetail)
            (room - (if (cardAtTop) topInset + 24.dp else 24.dp)).coerceIn(220.dp, 350.dp)
            else 420.dp
        Canvas(Modifier.fillMaxSize()) {
            val hole = target?.inflate(4.dp.toPx())
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                hole?.let { addRoundRect(RoundRect(it, CornerRadius(16.dp.toPx()))) }
            }
            drawPath(shade, Color.Black.copy(alpha = 0.64f))
            hole?.let {
                drawRoundRect(duit.accentLime, it.topLeft, it.size,
                    cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        Surface(
            modifier = Modifier.align(if (cardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .statusBarsPadding().navigationBarsPadding()
                .padding(top = if (cardAtTop) 12.dp else 0.dp,
                    bottom = if (isDetail) 12.dp else 92.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 480.dp).fillMaxWidth().heightIn(max = cardHeight),
            shape = RoundedCornerShape(16.dp),
            color = duit.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${step + 1} dari ${AppTourStep.entries.size}", style = MaterialTheme.typography.labelLarge, color = duit.textSecondary)
                    TextButton(onClick = onDismiss) { Text("Lewati tur") }
                }
                LinearProgressIndicator(progress = { (step + 1).toFloat() / AppTourStep.entries.size },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = duit.primary, trackColor = duit.border)
                AnimatedContent(targetState = step,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(bodyScroll),
                    transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tourStep") { index ->
                    Column {
                        val content = AppTourStep.entries[index]
                        Text(content.title, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = duit.textPrimary,
                            modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        Text(content.description, style = MaterialTheme.typography.bodyMedium, color = duit.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        Text(content.example, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, color = duit.textPrimary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kembali") }
                    }
                    Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (step == AppTourStep.entries.lastIndex) "Mulai mencatat" else "Lanjut")
                    }
                }
                if (step == AppTourStep.entries.lastIndex && onOpenGuide != null) {
                    TextButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                        Text("Panduan fitur lainnya")
                    }
                }
            }
        }
    }
}
