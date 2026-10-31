package vn.son.shieldflix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF141414)
    val Red = Color(0xFFE50914)
    val Gray = Color(0xFFB3B3B3)
    val DimGray = Color(0xFF808080)
    val Card = Color(0xFF232323)
    val CardFocused = Color(0xFF3A3A3A)
    val Green = Color(0xFF46D369)
    val Overlay = Color(0xE6141414)
}

private val Typo = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    bodyLarge = TextStyle(fontSize = 16.sp, color = Color.White),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Palette.Gray),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Red,
            onPrimary = Color.White,
            background = Palette.Bg,
            onBackground = Color.White,
            surface = Palette.Bg,
            onSurface = Color.White,
        ),
        typography = Typo,
        content = content,
    )
}
