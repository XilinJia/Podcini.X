package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.ThemePreference
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

private const val TAG = "AppTheme"

val CustomTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    // Add other text styles as needed
)

object CustomTextStyles {
    val titleCustom = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
}

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColors = lightColorScheme().copy(
    tertiary = Color(0xFF4E3511),
    tertiaryContainer = Color(0xFFB6EEEE),
    surface = Color(0xFFFDFCF0),
    onSurface = Color(0xFF2D2E30)
)
private val DarkColors = darkColorScheme().copy(
    tertiary = Color(0xFFE9A43E),
    tertiaryContainer = Color(0xFF0D343E),
    onSurface = Color(0xFFE0D7C1),
)

@Composable
fun PodciniTheme(forceTheme: ThemePreference? = null, content: @Composable () -> Unit) {
    val themePreference: ThemePreference = if (forceTheme != null) forceTheme!! else AppPreferences.theme
    val isDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK, ThemePreference.BLACK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

//    val view = LocalView.current
//    if (!view.isInEditMode) {
//        SideEffect {
//            val window = (view.context as Activity).window
//            window.statusBarColor = Color.Transparent.toArgb()
//            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
//        }
//    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val isBlackModeEnabled: Boolean = getPref(AppPrefs.prefThemeBlack, false)
    val colorScheme = when {
        isDark && (themePreference == ThemePreference.BLACK || isBlackModeEnabled) -> DarkColors.copy(surface = Color(0xFF000000))
        isDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

fun isLightTheme(): Boolean {
    val themePreference: ThemePreference = AppPreferences.theme
    return when (themePreference) {
        ThemePreference.LIGHT -> true
        ThemePreference.DARK, ThemePreference.BLACK -> false
        ThemePreference.SYSTEM -> {
            val uiMode = getAppContext().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode != Configuration.UI_MODE_NIGHT_YES
        }
    }
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
