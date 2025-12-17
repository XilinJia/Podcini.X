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

fun distinctColorOf(color1: Color, color2: Color): Color {
    val hsv1 = FloatArray(3)
    val hsv2 = FloatArray(3)
    android.graphics.Color.colorToHSV(color1.toArgb(), hsv1)
    android.graphics.Color.colorToHSV(color2.toArgb(), hsv2)
    val hue1 = hsv1[0]
    val hue2 = hsv2[0]
    var midpointHue: Float
    if (abs(hue1 - hue2) < 180) midpointHue = (hue1 + hue2) / 2
    else {
        midpointHue = (hue1 + hue2 + 360) / 2
        if (midpointHue >= 360) midpointHue -= 180
    }
    var distinctHue = midpointHue + 180
    if (distinctHue >= 360) distinctHue -= 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(distinctHue, 1.0f, 1.0f)))
}

fun complementaryColorOf(color: Color): Color {
    val argbInt = color.toArgb()
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argbInt, hsv)
    var newHue = hsv[0] + 180f
    if (newHue >= 360f) newHue -= 360f
    hsv[0] = newHue
    return Color(android.graphics.Color.HSVToColor(hsv))
}