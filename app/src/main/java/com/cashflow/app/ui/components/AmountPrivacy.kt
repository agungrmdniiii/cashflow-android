package com.cashflow.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAmountsHidden = staticCompositionLocalOf { false }

@Composable
fun Formatters.displayRupiah(amount: Long, includePrefix: Boolean = true): String =
    if (LocalAmountsHidden.current) if (includePrefix) "Rp ••••••" else "••••••" else formatRupiah(amount, includePrefix)

@Composable
fun Formatters.displayCompact(amount: Long): String =
    if (LocalAmountsHidden.current) "••••••" else formatCompact(amount)
