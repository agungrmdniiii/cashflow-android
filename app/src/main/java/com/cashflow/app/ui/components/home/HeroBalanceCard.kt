package com.cashflow.app.ui.components.home

import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashflow.app.ui.components.AnimatedNumberText
import com.cashflow.app.ui.components.DarkDoubleBezelCard
import com.cashflow.app.ui.theme.*

/**
 * Hero Card "Total Kekayaan" untuk Beranda (Tahap 2).
 * Hierarki jelas, angka besar dengan ribuan/desimal pudar (dimThousands),
 * tombol mata dengan target sentuh >= 48dp, badge delta bulan lalu, dan sparkline ringkas.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun HeroBalanceCard(
    totalWealth: Long,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    monthDeltaPct: Int?,
    onNavigateToAssets: () -> Unit,
    assetCount: Int = 0,
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

    val eyeRotation by animateFloatAsState(
        targetValue = if (isBalanceHidden) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 240, easing = DuitAingMotion.SubtleEasing),
        label = "EyeRotation"
    )

    DarkDoubleBezelCard(
        modifier = modifier.fillMaxWidth(),
        outerRadius = 20.dp,
        innerRadius = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Row 1: Header - Label & Icon di Kiri, Tombol Mata di Kanan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(duit.heroAccent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = duit.heroAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "Total Saldo Rekening",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = duit.onHeroSecondary
                    )
                }

                // Tactile Eye Toggle Button (target sentuh ringkas & mudah ditekan)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(duit.onHero.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClick = onToggleBalanceVisibility),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isBalanceHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isBalanceHidden) "Tampilkan saldo" else "Sembunyikan saldo",
                        tint = duit.onHeroSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer { rotationZ = eyeRotation }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Balance Display dengan AnimatedNumberText (dimThousands = true)
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
                label = "WealthPrivacyTransition"
            ) { hidden ->
                if (hidden) {
                    Text(
                        text = "Rp ••••••••",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = 2.sp
                        ),
                        color = duit.onHero,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    AnimatedNumberText(
                        value = totalWealth,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = duit.onHero,
                        dimThousands = true,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Perubahan vs Bulan Lalu / Status Dompet + Navigasi Aset
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (monthDeltaPct != null && !isBalanceHidden) {
                        val isLower = monthDeltaPct <= 0
                        val deltaText = if (isLower) "Belanja ${monthDeltaPct}% vs bulan lalu" else "Belanja +${monthDeltaPct}% vs bulan lalu"
                        val badgeBg = if (isLower) duit.heroAccent.copy(alpha = 0.22f) else duit.expense.copy(alpha = 0.22f)
                        val badgeColor = if (isLower) duit.heroAccent else duit.expense

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppRadius.pill))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 2.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLower) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = deltaText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = badgeColor
                                )
                            }
                        }
                    } else if (assetCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(duit.heroAccent)
                            )
                            Text(
                                text = "$assetCount Dompet/Aset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = duit.onHeroSecondary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Pintasan "Lihat Aset"
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.pill))
                        .clickable(role = Role.Button, onClick = onNavigateToAssets)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Lihat Aset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = duit.heroAccent
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = duit.heroAccent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
