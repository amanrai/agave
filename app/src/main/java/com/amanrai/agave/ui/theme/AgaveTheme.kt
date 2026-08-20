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

val AgaveBackground = Color(0xFF282C34)
val AgaveSurface = Color(0xFF2F343D)
val AgaveSunken = Color(0xFF21252B)
val AgaveText = Color(0xFFABB2BF)
val AgaveTextHigh = Color(0xFFC8CDD4)
val AgaveMuted = Color(0xFF7F8794)
val AgaveFaint = Color(0xFF5C6370)
val AgaveAccent = Color(0xFFE5C07B)
val AgaveAccentDeep = Color(0xFFD19A66)
val AgaveBlue = Color(0xFF61AFEF)
val AgaveCyan = Color(0xFF56B6C2)
val AgaveGreen = Color(0xFF98C379)
val AgaveRed = Color(0xFFE06C75)
val AgaveButtonBlue = Color(0xFF9DB8D6)
val AgaveButtonText = Color(0xFF21252B)
val AgaveBorder = Color.White.copy(alpha = 0.06f)

private val Colors = darkColorScheme(
    primary = AgaveAccent,
    onPrimary = AgaveSunken,
    secondary = AgaveBlue,
    onSecondary = AgaveSunken,
    background = AgaveBackground,
    onBackground = AgaveText,
    surface = AgaveSurface,
    onSurface = AgaveText,
    surfaceVariant = AgaveSunken,
    onSurfaceVariant = AgaveMuted,
    error = AgaveRed,
    onError = AgaveSunken,
    outline = AgaveBorder,
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
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(3.dp),
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
