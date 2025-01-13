package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StyleRes

object ThemeSwitcher {
    @JvmStatic
    @StyleRes
    fun getTheme(context: Context): Int {
        val dynamic = AppPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            AppPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_Podcini_Dynamic_Dark else R.style.Theme_Podcini_Dark
            AppPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_Podcini_Dynamic_TrueBlack else R.style.Theme_Podcini_TrueBlack
            AppPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light else R.style.Theme_Podcini_Light
            else -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light else R.style.Theme_Podcini_Light
        }
    }

    @JvmStatic
    @StyleRes
    fun getNoTitleTheme(context: Context): Int {
        val dynamic = AppPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            AppPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_Podcini_Dynamic_Dark_NoTitle else R.style.Theme_Podcini_Dark_NoTitle
            AppPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_Podcini_Dynamic_TrueBlack_NoTitle else R.style.Theme_Podcini_TrueBlack_NoTitle
            AppPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light_NoTitle else R.style.Theme_Podcini_Light_NoTitle
            else -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light_NoTitle else R.style.Theme_Podcini_Light_NoTitle
        }
    }

    @JvmStatic
    @StyleRes
    fun getTranslucentTheme(context: Context): Int {
        val dynamic = AppPreferences.isThemeColorTinted
        return when (readThemeValue(context)) {
            AppPreferences.ThemePreference.DARK -> if (dynamic) R.style.Theme_Podcini_Dynamic_Dark_Translucent else R.style.Theme_Podcini_Dark_Translucent
            AppPreferences.ThemePreference.BLACK -> if (dynamic) R.style.Theme_Podcini_Dynamic_TrueBlack_Translucent else R.style.Theme_Podcini_TrueBlack_Translucent
            AppPreferences.ThemePreference.LIGHT -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light_Translucent else R.style.Theme_Podcini_Light_Translucent
            else -> if (dynamic) R.style.Theme_Podcini_Dynamic_Light_Translucent else R.style.Theme_Podcini_Light_Translucent
        }
    }

    fun readThemeValue(context: Context): AppPreferences.ThemePreference {
        var theme = AppPreferences.theme
        if (theme == AppPreferences.ThemePreference.SYSTEM) {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            theme = if (nightMode == Configuration.UI_MODE_NIGHT_YES) AppPreferences.ThemePreference.DARK else AppPreferences.ThemePreference.LIGHT
        }
        if (theme == AppPreferences.ThemePreference.DARK && getPref(AppPrefs.prefThemeBlack, false)) theme = AppPreferences.ThemePreference.BLACK
        return theme
    }
}
