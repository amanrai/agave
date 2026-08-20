package com.amanrai.agave.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AgaveBackground = Color.Black
val AgaveSurface = Color(0xFF15171C)
val AgaveSunken = Color(0xFF090A0D)
val AgaveText = Color(0xFFBEC3CD)
val AgaveTextHigh = Color(0xFFE9ECF1)
val AgaveMuted = Color(0xFF858B98)
val AgaveFaint = Color(0xFF535966)
val AgaveAccent = Color(0xFF76D6B2)
val AgaveAccentDeep = Color(0xFF4CBF96)
val AgaveBlue = Color(0xFF80AFFF)
val AgaveCyan = Color(0xFF67D4E0)
val AgaveGreen = Color(0xFF76D6B2)
val AgaveRed = Color(0xFFFF7A90)
val AgaveButtonBlue = Color(0xFFA8B8FF)
val AgaveButtonText = Color(0xFF111318)
val AgaveBorder = Color.White.copy(alpha = 0.08f)

private val Colors = darkColorScheme(
    primary = AgaveAccent,
    onPrimary = AgaveSunken,
    primaryContainer = Color(0xFF17372D),
    onPrimaryContainer = AgaveTextHigh,
    secondary = AgaveButtonBlue,
    onSecondary = AgaveButtonText,
    secondaryContainer = Color(0xFF252D49),
    onSecondaryContainer = AgaveTextHigh,
    tertiary = AgaveCyan,
    onTertiary = AgaveSunken,
    tertiaryContainer = Color(0xFF15353A),
    onTertiaryContainer = AgaveTextHigh,
    background = AgaveBackground,
    onBackground = AgaveText,
    surface = AgaveSurface,
    onSurface = AgaveText,
    surfaceVariant = AgaveSunken,
    onSurfaceVariant = AgaveMuted,
    error = AgaveRed,
    onError = AgaveSunken,
    errorContainer = Color(0xFF431F29),
    onErrorContainer = Color(0xFFFFBAC6),
    outline = Color.White.copy(alpha = 0.12f),
    outlineVariant = AgaveBorder,
)

private val Type = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = AgaveText,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = AgaveText,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = AgaveText,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        color = AgaveTextHigh,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = AgaveTextHigh,
    ),
)

private val AgaveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AgaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        shapes = AgaveShapes,
        content = content,
    )
}
