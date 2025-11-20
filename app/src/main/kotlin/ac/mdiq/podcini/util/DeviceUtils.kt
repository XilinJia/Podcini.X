package ac.mdiq.podcini.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.DP
import androidx.annotation.Dimension.Companion.SP



fun dpToPx(@Dimension(unit = DP) dp: Int, context: Context): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}


fun spToPx(@Dimension(unit = SP) sp: Int, context: Context): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics).toInt()
}


fun isLandscape(context: Context): Boolean {
    return context.resources.displayMetrics.heightPixels < context.resources.displayMetrics.widthPixels
}


fun getWindowHeight(windowManager: WindowManager): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics = windowManager.currentWindowMetrics
        val windowInsets = windowMetrics.windowInsets
        val insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
        return windowMetrics.bounds.height() - (insets.top + insets.bottom)
    } else {
        val point = Point()
        windowManager.defaultDisplay.getSize(point)
        return point.y
    }
}

/**
 * @return whether the device has support for WebView, see
 * [https://stackoverflow.com/a/69626735](https://stackoverflow.com/a/69626735)
 */
fun supportsWebView(): Boolean {
    try {
        CookieManager.getInstance()
        return true
    } catch (ignored: Throwable) {
        Logs("DeviceUtils", ignored, "webview not supported")
        return false
    }
}
