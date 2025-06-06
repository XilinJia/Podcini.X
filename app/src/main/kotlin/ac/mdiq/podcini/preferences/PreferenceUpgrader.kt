package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.error.CrashReportWriter.Companion.file
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object PreferenceUpgrader {
    private const val PREF_CONFIGURED_VERSION = "version_code"
    private const val PREF_NAME = "app_version"

    private lateinit var prefs: SharedPreferences

    const val githubAddress = "https://github.com/XilinJia/Podcini.X/"

    fun checkUpgrades(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1)
        val newVersion = BuildConfig.VERSION_CODE

        if (oldVersion != newVersion) {
            file.delete()

            upgrade(oldVersion, context)
            upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply()
        }
    }

     private fun upgrade(oldVersion: Int, context: Context) {
        //New installation
        if (oldVersion == -1) return
    }

    fun getCopyrightNoticeText(context: Context): String {
        var copyrightNoticeText = ""
        val packageHash = context.packageName.hashCode()
        Logd("getCopyrightNoticeText", "packageName: ${context.packageName} ${packageHash} ${"ac.mdiq.podcini.X".hashCode()}")
        when {
            packageHash != 1329568237 && packageHash != -1967311086 -> {
                copyrightNoticeText = ("This application is based on Podcini."
                        + " The Podcini team does NOT provide support for this unofficial version."
                        + " If you can read this message, the developers of this modification violate the GNU General Public License (GPL).")
            }
            packageHash == -1967311086 -> copyrightNoticeText = "This is a development version of Podcini and not meant for daily use"
        }
        return copyrightNoticeText
    }
}
