package vn.son.shieldflix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.son.shieldflix.R

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

/** Inter: gần với Netflix Sans, đủ dấu tiếng Việt. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black),
)

private val Typo = Typography(
    displaySmall = TextStyle(fontFamily = Inter, fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
    titleMedium = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, color = Palette.Gray),
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
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
