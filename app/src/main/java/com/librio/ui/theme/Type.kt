@file:OptIn(ExperimentalTextApi::class)

package com.librio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.librio.R

// Plus Jakarta Sans — variable font, all weights in one file
val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.plus_jakarta_sans_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

// JetBrains Mono — monospace accent font
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val HeadingFont = JakartaSans
val BodyFont = JakartaSans
val MonoFont = JetBrainsMono

val LibrioTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp, letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = JakartaSans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
        fontSize = 9.sp, letterSpacing = 0.7.sp,
    ),
)
