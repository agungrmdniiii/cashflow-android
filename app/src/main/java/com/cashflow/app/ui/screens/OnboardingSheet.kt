package com.cashflow.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cashflow.app.ui.theme.*

data class OnboardingSlide(
    val titleFirst: String,
    val titleSecond: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * Fullscreen Fintech Onboarding Screen (Matching Reference Screen 1!)
 * Deep forest green gradient, 01/03 progress segments, bold headline, white pill CTA.
 */
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit
) {
    var currentSlide by remember { mutableStateOf(0) }

    val slides = remember {
        listOf(
            OnboardingSlide(
                titleFirst = "Catat Cepat,",
                titleSecond = "Kelola Nyaman",
                subtitle = "Catat pengeluaran & pemasukan dalam hitungan detik — lewat formulir kilat atau perintah suara pintar.",
                icon = Icons.Rounded.AccountBalanceWallet
            ),
            OnboardingSlide(
                titleFirst = "Pantau Anggaran,",
                titleSecond = "Disiplin Finansial",
                subtitle = "Kendalikan batas belanja bulanan lewat analitik cerdas & donut chart intuitif.",
                icon = Icons.Rounded.DonutLarge
            ),
            OnboardingSlide(
                titleFirst = "100% Offline,",
                titleSecond = "Privasi Terjaga",
                subtitle = "Seluruh data keuangan tersimpan lokal di HP Anda tanpa server eksternal dan tanpa perlu internet.",
                icon = Icons.Rounded.Shield
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F382A), // Deep Forest Green Gradient
                            Color(0xFF0A261C),
                            Color(0xFF061A13)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: 01 / 03 + 3-Segment Progress Bar + Skip (Matching Reference!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "0${currentSlide + 1} / 03",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White
                    )

                    // 3-Segment horizontal bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                    ) {
                        repeat(3) { idx ->
                            val isCur = idx == currentSlide
                            val isPassed = idx < currentSlide
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        when {
                                            isCur -> Color.White
                                            isPassed -> Color.White.copy(alpha = 0.7f)
                                            else -> Color.White.copy(alpha = 0.22f)
                                        }
                                    )
                            )
                        }
                    }

                    Text(
                        text = "Lewati",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Middle: Headline & Illustration Pedestal
                val slide = slides[currentSlide]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = slide.titleFirst,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            letterSpacing = (-1.0).sp,
                            lineHeight = 46.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = slide.titleSecond,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            letterSpacing = (-1.0).sp,
                            lineHeight = 46.sp
                        ),
                        color = FintechAccentMint // Mint Accent from Reference!
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Motion Presentation: Flowing Cash (Slide 0), Revolving Donut (Slide 1), Security Vault (Slide 2)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = currentSlide,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.88f) togetherWith
                                        fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.08f)
                            },
                            label = "onboardingMotionTransition"
                        ) { targetIdx ->
                            when (targetIdx) {
                                0 -> com.cashflow.app.ui.components.FlowingCashMotion(size = 230.dp)
                                1 -> com.cashflow.app.ui.components.RevolvingDonutMotion(size = 230.dp)
                                else -> com.cashflow.app.ui.components.SecurityVaultMotion(size = 230.dp)
                            }
                        }
                    }
                }

                // Bottom Action: White Full-Width Pill Button (Matching Reference!)
                val isLast = currentSlide == 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .clickable(role = Role.Button) {
                            if (isLast) onDismiss() else currentSlide++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLast) "Mulai Sekarang" else "Lanjut",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = Color(0xFF121A16)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF121A16),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
