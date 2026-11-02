package pe.edu.upc.sems.shared.designsystem

import androidx.compose.ui.graphics.Color

// SEMS color tokens (Material Design 3).
// Light: official Figma palette. Dark: surface/onSurface fixed by the report,
// remaining roles derived from the M3 tonal palette.
// Roles marked "derived" were not defined in Figma; they follow the same tonal palettes.

// region Light
val PrimaryLight = Color(0xFF0B57D0)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD9E2FF)
val OnPrimaryContainerLight = Color(0xFF001A41)

val SecondaryLight = Color(0xFF00639B)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCFE5FF) // derived
val OnSecondaryContainerLight = Color(0xFF001D33) // derived

/** Success: use only for savings and achieved goals. */
val TertiaryLight = Color(0xFF146C2E)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFC4EED0)
val OnTertiaryContainerLight = Color(0xFF002106)

/** Use only for exceeded contracted power, meter failures or penalties. */
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

val SurfaceLight = Color(0xFFFDFCFF)
val OnSurfaceLight = Color(0xFF1A1C1E)
val SurfaceVariantLight = Color(0xFFE1E2E8) // from the report
val OnSurfaceVariantLight = Color(0xFF43474E)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF) // derived
val SurfaceContainerLowLight = Color(0xFFF6F8FC) // derived
val SurfaceContainerLight = Color(0xFFF0F4FA)
val SurfaceContainerHighLight = Color(0xFFE9EEF6)
val SurfaceContainerHighestLight = Color(0xFFE2E7EF) // derived
val SurfaceDimLight = Color(0xFFD9DBE0) // derived
val SurfaceBrightLight = Color(0xFFFDFCFF) // derived

val OutlineLight = Color(0xFF73777F)
val OutlineVariantLight = Color(0xFFC3C7CF)

val InverseSurfaceLight = Color(0xFF2F3033) // derived
val InverseOnSurfaceLight = Color(0xFFF1F0F4) // derived
val InversePrimaryLight = Color(0xFFADC6FF) // derived (= dark primary)
// endregion

// region Dark
val PrimaryDark = Color(0xFFADC6FF)
val OnPrimaryDark = Color(0xFF002E69)
val PrimaryContainerDark = Color(0xFF00429B)
val OnPrimaryContainerDark = Color(0xFFD9E2FF)

val SecondaryDark = Color(0xFF9CCAFF)
val OnSecondaryDark = Color(0xFF003256)
val SecondaryContainerDark = Color(0xFF004A77) // derived
val OnSecondaryContainerDark = Color(0xFFCFE5FF) // derived

val TertiaryDark = Color(0xFF8BD89A)
val OnTertiaryDark = Color(0xFF00391A)
val TertiaryContainerDark = Color(0xFF005226)
val OnTertiaryContainerDark = Color(0xFFA8F5B4)

val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

val SurfaceDark = Color(0xFF111318)
val OnSurfaceDark = Color(0xFFE2E2E6)
val SurfaceVariantDark = Color(0xFF44474E) // derived
val OnSurfaceVariantDark = Color(0xFFC4C6D0)
val SurfaceContainerLowestDark = Color(0xFF0C0E13) // derived
val SurfaceContainerLowDark = Color(0xFF191C20) // derived
val SurfaceContainerDark = Color(0xFF1D2024)
val SurfaceContainerHighDark = Color(0xFF282A2F)
val SurfaceContainerHighestDark = Color(0xFF33353A) // derived
val SurfaceDimDark = Color(0xFF111318) // derived
val SurfaceBrightDark = Color(0xFF37393E) // derived

val OutlineDark = Color(0xFF8E9099)
val OutlineVariantDark = Color(0xFF44474E)

val InverseSurfaceDark = Color(0xFFE2E2E6) // derived
val InverseOnSurfaceDark = Color(0xFF2F3033) // derived
val InversePrimaryDark = Color(0xFF0B57D0) // derived (= light primary)
// endregion
