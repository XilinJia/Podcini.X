package ac.mdiq.podcini.utils

import android.content.Context

const val githubAddress = "https://github.com/XilinJia/Podcini.X/"

fun getCopyrightNoticeText(context: Context): String {
    var copyrightNoticeText = ""
    val packageHash = context.packageName.hashCode()
    when {
        packageHash != 1329568237 && packageHash != -1967311086 -> {
            copyrightNoticeText = ("This application is based on Podcini." + " The Podcini team does NOT provide support for this unofficial version." + " If you can read this message, the developers of this modification violate the GNU General Public License (GPL).")
        }
        packageHash == -1967311086 -> copyrightNoticeText = "This is a development version of Podcini and not meant for daily use"
    }
    return copyrightNoticeText
}
