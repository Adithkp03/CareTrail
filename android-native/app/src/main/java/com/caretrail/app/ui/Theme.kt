package com.caretrail.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.caretrail.app.R

// Same palette as the web app (tailwind.config.ts / globals.css).
object CT {
    val Brand = Color(0xFF15365A)
    val BrandSoft = Color(0xFFEAF0FB)
    val Navy = Color(0xFF10283F)
    val NavyEnd = Color(0xFF183A51)
    val Ink = Color(0xFF203044)
    val Bg = Color(0xFFF4F6F8)
    val CardBorder = Color(0xFFE8EDF2)
    val FieldBorder = Color(0xFFDFE6EC)
    val Accent = Color(0xFFA5C5FF)
    val Amber50 = Color(0xFFFFFBEB)
    val Amber100 = Color(0xFFFEF3C7)
    val Amber300 = Color(0xFFFCD34D)
    val Amber900 = Color(0xFF78350F)
    val Red50 = Color(0xFFFEF2F2)
    val Red200 = Color(0xFFFECACA)
    val Red700 = Color(0xFFB91C1C)
    val Red800 = Color(0xFF991B1B)
    val Green50 = Color(0xFFF0FDF4)
    val Green300 = Color(0xFF86EFAC)
    val Green100 = Color(0xFFDCFCE7)
    val Green800 = Color(0xFF166534)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue800 = Color(0xFF1E40AF)
    val Peach = Color(0xFFF4E9DE)
    val Cream = Color(0xFFF4EFE5)
    val SoftBlue = Color(0xFFE3E9F5)
    val Muted = Color(0xFF203044).copy(alpha = 0.65f)
}

val Poppins = FontFamily(
    Font(R.font.poppins_400, FontWeight.Normal),
    Font(R.font.poppins_500, FontWeight.Medium),
    Font(R.font.poppins_600, FontWeight.SemiBold),
    Font(R.font.poppins_700, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.5).toInt()) =
    TextStyle(fontFamily = Poppins, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, color = CT.Ink)

val CareTypography = Typography(
    displayLarge = style(28, FontWeight.SemiBold),
    headlineMedium = style(22, FontWeight.SemiBold),
    headlineSmall = style(20, FontWeight.SemiBold),
    titleLarge = style(18, FontWeight.SemiBold),
    titleMedium = style(16, FontWeight.SemiBold),
    titleSmall = style(14, FontWeight.SemiBold),
    bodyLarge = style(15),
    bodyMedium = style(14, line = 21),
    bodySmall = style(12, line = 18),
    labelLarge = style(14, FontWeight.SemiBold),
    labelMedium = style(12, FontWeight.Medium),
    labelSmall = style(11, FontWeight.Medium),
)

@Composable
fun CareTrailTheme(content: @Composable () -> Unit) {
    // Light only on purpose: the web design has a single navy-on-light look.
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CT.Brand, onPrimary = Color.White,
            primaryContainer = CT.BrandSoft, onPrimaryContainer = CT.Brand,
            secondary = CT.Navy, background = CT.Bg, surface = Color.White,
            onSurface = CT.Ink, onBackground = CT.Ink, outline = CT.FieldBorder, error = CT.Red700,
        ),
        typography = CareTypography,
        content = content,
    )
}
