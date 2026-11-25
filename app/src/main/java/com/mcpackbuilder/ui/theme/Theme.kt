package com.mcpackbuilder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF2E5E3A)
private val GreenLight = Color(0xFF4E8A5C)
private val Lime = Color(0xFF8BC34A)
private val Amber = Color(0xFFC97A12)
private val Surface = Color(0xFFF6F7F2)
private val SurfaceVariant = Color(0xFFE6EAE0)

private val Colors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8D9),
    onPrimaryContainer = Color(0xFF16321D),
    secondary = GreenLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDDC),
    onSecondaryContainer = Color(0xFF20301F),
    tertiary = Amber,
    onTertiary = Color.White,
    background = Surface,
    onBackground = Color(0xFF1A1C18),
    surface = Color.White,
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFF44483F),
    outline = Color(0xFF9AA093),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MCPackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
