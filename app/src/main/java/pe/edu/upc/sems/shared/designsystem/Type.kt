package pe.edu.upc.sems.shared.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto is the Android system font, so FontFamily.Default renders Roboto
// without bundling font files.
private val Roboto = FontFamily.Default

private val Base = Typography()

/**
 * SEMS type scale (report / Figma):
 * Headline 24 Bold · Title 20 Medium · Title S 16 Medium · Body 16 Regular ·
 * Body S 14 · Label 13 Medium · Caption 12.
 */
val SemsTypography = Typography(
    // Headline 24 Bold
    headlineSmall = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    // Title 20 Medium
    titleLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Title S 16 Medium
    titleMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    // Body 16 Regular
    bodyLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    // Body S 14
    bodyMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    // Caption 12
    bodySmall = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    // Label 13 Medium (buttons, chips, tabs)
    labelLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    // Remaining M3 roles keep their defaults, using Roboto.
    displayLarge = Base.displayLarge.copy(fontFamily = Roboto),
    displayMedium = Base.displayMedium.copy(fontFamily = Roboto),
    displaySmall = Base.displaySmall.copy(fontFamily = Roboto),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Roboto),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Roboto),
    titleSmall = Base.titleSmall.copy(fontFamily = Roboto),
    labelMedium = Base.labelMedium.copy(fontFamily = Roboto),
    labelSmall = Base.labelSmall.copy(fontFamily = Roboto),
)
