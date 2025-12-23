package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.preferences.AppPreferences.ThemePreference
import ac.mdiq.podcini.preferences.ThemeSwitcher.readThemeValue
import ac.mdiq.podcini.utils.Logd
import android.content.Context
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

private const val TAG = "AppTheme"

val CustomTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    // Add other text styles as needed
)

object CustomTextStyles {
    val titleCustom = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

val Shapes = Shapes(small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(4.dp), large = RoundedCornerShape(0.dp))

fun getColorFromAttr(context: Context, @AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    val theme = context.theme
    theme.resolveAttribute(attrColor, typedValue, true)
    Logd(TAG, "getColorFromAttr: ${typedValue.resourceId} ${typedValue.data}")
    return if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else { typedValue.data }
}

private val LightColors = lightColorScheme().copy(
    tertiary = Color(0xFF4E3511),
    tertiaryContainer = Color(0xFFB6EEEE),
)
private val DarkColors = darkColorScheme().copy(
    tertiary = Color(0xFFE9A43E),
    tertiaryContainer = Color(0xFF0D343E),
)

@Composable
fun CustomTheme(context: Context, content: @Composable () -> Unit) {
    // TODO
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor -> dynamicLightColorScheme(LocalContext.current)
        else -> lightColorScheme()
    }
    val colors = when (readThemeValue(context)) {
        ThemePreference.LIGHT -> LightColors
        ThemePreference.DARK -> DarkColors
        ThemePreference.BLACK -> DarkColors.copy(surface = Color(0xFF000000))
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colors, typography = CustomTypography, shapes = Shapes, content = content)
}

fun isLightTheme(context: Context): Boolean {
    return readThemeValue(context) == ThemePreference.LIGHT
}

fun distinctColorOf(colorA: Color, colorB: Color): Color {
    val hslA = FloatArray(3)
    val hslB = FloatArray(3)
    val argbA = colorA.toArgb()
    val argbB = colorB.toArgb()

    ColorUtils.colorToHSL(argbA, hslA)
    ColorUtils.colorToHSL(argbB, hslB)

    val avgHue = (hslA[0] + hslB[0]) / 2f
    val avgSaturation = (hslA[1] + hslB[1]) / 2f
//    val avgLightness = (hslA[2] + hslB[2]) / 2f

    val targetHue = (avgHue + 180f) % 360f
    val targetSaturation = (1.0f - avgSaturation).coerceIn(0.3f, 0.9f)

    val lumA = ColorUtils.calculateLuminance(argbA)
    val lumB = ColorUtils.calculateLuminance(argbB)
    val avgLuminance = (lumA + lumB) / 2.0

    var currentLightness = if (avgLuminance < 0.5) 0.9f else 0.1f

    val targetColorHSL = floatArrayOf(targetHue, targetSaturation, currentLightness)
    var resultColor = ColorUtils.HSLToColor(targetColorHSL)

    val minContrast = 4.5f
    var attempts = 0

    while (attempts < 10) {
        val contrastA = ColorUtils.calculateContrast(resultColor, argbA)
        val contrastB = ColorUtils.calculateContrast(resultColor, argbB)
        if (contrastA >= minContrast && contrastB >= minContrast) break

        currentLightness = if (avgLuminance < 0.5) (currentLightness + 0.05f).coerceAtMost(1.0f) else (currentLightness - 0.05f).coerceAtLeast(0.0f)

        targetColorHSL[2] = currentLightness
        resultColor = ColorUtils.HSLToColor(targetColorHSL)
        attempts++
    }
    return Color(resultColor)
}

fun complementaryColorOf(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + 180f) % 360f
    hsv[1] = if (hsv[1] < 0.5f) 0.7f else hsv[1]
    hsv[2] = if (hsv[2] > 0.5f) 0.2f else 0.9f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun contrastColorOf(color: Color): Color {
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return if (luminance > 0.5) Color.Black else Color.White
}