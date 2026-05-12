package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext

const val githubAddress = "https://github.com/XilinJia/Podcini.X/"

const val developerEmail = "xilin.vw@gmail.com"

val USER_AGENT: String by lazy { "Mozilla/5.0 (compatible; Podcini/${BuildConfig.VERSION_NAME}; Android)" }

fun getCopyrightNoticeText(): String {
    val packageHash = getAppContext().packageName.hashCode()
    val copyrightNoticeText = when {
        packageHash != 1329568237 && packageHash != -1967311086 -> ("This application is based on Podcini." + " The Podcini team does NOT provide support for this unofficial version." + " If you can read this message, the developers of this modification violate the GNU General Public License (GPL).")
        packageHash == -1967311086 -> "This is a development version of Podcini and not meant for daily use"
        else -> ""
    }
    return copyrightNoticeText
}
