package ac.mdiq.podcini.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs


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