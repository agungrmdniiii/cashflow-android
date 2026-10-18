package com.cashflow.app.ui.components.home

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.model.CashflowBucket
import com.cashflow.app.data.model.PeriodFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.cashflow.app.ui.components.AnimatedNumberText
import com.cashflow.app.ui.components.DoubleBezelCard
import com.cashflow.app.ui.components.Formatters
import com.cashflow.app.ui.theme.duit

/**
 * Kartu Ringkasan Arus Kas untuk Beranda (Tahap 2):
 * - Satu kartu terpadu: pemasukan, pengeluaran, selisih, dan mini bar chart 7 hari.
 * - Pemilih periode dengan target sentuh >= 48dp.
 * - Mendukung penyembunyian saldo & pengurangan animasi sistem.
 */
@Composable
fun CashflowCard(
    netCashflow: Long,
    periodIncome: Long,
    periodExpense: Long,
    selectedPeriod: PeriodFilter,
    onSelectPeriodClick: () -> Unit,
    buckets: List<CashflowBucket>,
    isBalanceHidden: Boolean,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val context = LocalContext.current
    val reduceMotion = remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        DoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            innerRadius = 22.dp,
            innerColor = duit.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Judul di Kiri, Tombol Periode di Kanan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arus Kas",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = duit.textPrimary
                    )

                    // Period Selector Pill (Target sentuh >= 48dp)
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(duit.surfaceVariant)
                            .border(1.dp, duit.border, RoundedCornerShape(999.dp))
                            .clickable(role = Role.Button, onClick = onSelectPeriodClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = duit.textPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = selectedPeriod.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = duit.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Pilih periode",
                                tint = duit.textPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selisih Bersih (Net Cashflow)
                AnimatedContent(
                    targetState = isBalanceHidden,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.96f)) togetherWith
                                    (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 1.04f))
                        }
                    },
                    label = "NetCashflowPrivacyTransition"
                ) { hidden ->
                    if (hidden) {
                        Text(
                            text = "Rp ••••••••",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = if (netCashflow >= 0) duit.income else duit.expense
                        )
                    } else {
                        val netPrefix = if (netCashflow > 0) "+" else ""
                        AnimatedNumberText(
                            value = netCashflow,
                            prefix = netPrefix,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            color = if (netCashflow >= 0) duit.income else duit.expense
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Chart follows the selected calendar period.
                PeriodCashflowChart(
                    buckets = buckets,
                    period = selectedPeriod,
                    hidden = isBalanceHidden,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = duit.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // 2 Kolom: Pemasukan vs Pengeluaran
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Kolom Pemasukan
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(duit.income)
                            )
                            Text(
                                text = "Pemasukan",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = duit.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isBalanceHidden) "+••••••" else "+" + Formatters.formatRupiah(periodIncome),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = duit.income
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(duit.border)
                    )

                    // Kolom Pengeluaran
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(duit.expense)
                            )
                            Text(
                                text = "Pengeluaran",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = duit.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isBalanceHidden) "−••••••" else "−" + Formatters.formatRupiah(periodExpense),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = duit.expense
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodCashflowChart(
    buckets: List<CashflowBucket>,
    period: PeriodFilter,
    hidden: Boolean,
    modifier: Modifier = Modifier
) {
    val duit = MaterialTheme.duit
    val peak = buckets.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L
    if (hidden) {
        Text("Grafik disembunyikan saat nominal disamarkan", style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
        return
    }
    if (peak == 0L) {
        Text("Belum ada pemasukan atau pengeluaran pada periode ini.", style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
        return
    }
    val unit = when (period) {
        PeriodFilter.HARI_INI -> "Per 4 jam • label menunjukkan jam mulai"
        PeriodFilter.MINGGU_INI -> "Per hari • Senin–Minggu"
        PeriodFilter.BULAN_INI -> "Per rentang tanggal bulan ini"
        PeriodFilter.TAHUN_INI -> "Per bulan • Januari–Desember"
        PeriodFilter.SEMUA -> "Per tahun • seluruh riwayat"
    }
    Column(modifier) {
        Text(unit, style = MaterialTheme.typography.bodySmall, color = duit.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            buckets.forEach { bucket ->
                Column(Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "${bucket.label}, pemasukan ${Formatters.formatRupiah(bucket.income)}, pengeluaran ${Formatters.formatRupiah(bucket.expense)}"
                }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.width(5.dp).height((48f * bucket.income.toFloat() / peak).dp).clip(RoundedCornerShape(2.dp)).background(duit.income))
                        Box(Modifier.width(5.dp).height((48f * bucket.expense.toFloat() / peak).dp).clip(RoundedCornerShape(2.dp)).background(duit.expense))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(bucket.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = duit.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}
