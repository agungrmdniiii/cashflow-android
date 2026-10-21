package com.cashflow.app.ui.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.data.service.FinancialInsight
import com.cashflow.app.ui.theme.duit

/**
 * Banner Insight Keuangan Beranda (Tahap 2):
 * - Menampilkan maksimal 1 insight paling relevan.
 * - Dapat ditutup oleh pengguna (tombol tutup target sentuh >= 48dp).
 */
@Composable
fun HomeInsightBanner(
    insight: FinancialInsight,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    position: Int = 1,
    total: Int = 1,
    onNext: () -> Unit = {}
) {
    val duit = MaterialTheme.duit
    val badgeBg = when (insight.type.lowercase()) {
        "positive" -> duit.incomeContainer
        "warning" -> duit.warningContainer
        else -> duit.transferContainer
    }
    val badgeTint = when (insight.type.lowercase()) {
        "positive" -> duit.income
        "warning" -> duit.warning
        else -> duit.transfer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = duit.surface),
        border = BorderStroke(1.dp, duit.border)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (insight.type.lowercase()) {
                            "positive" -> Icons.Rounded.ThumbUp
                            "warning" -> Icons.Rounded.WarningAmber
                            else -> Icons.Rounded.Lightbulb
                        },
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "INSIGHT KEUANGAN",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = duit.textSecondary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Tutup insight",
                        tint = duit.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                ),
                color = duit.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = insight.message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = duit.textSecondary
            )
            if (total > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$position dari $total",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = duit.textSecondary
                    )
                    TextButton(
                        onClick = onNext,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Insight berikutnya", color = duit.textPrimary)
                    }
                }
            }
        }
    }
}
