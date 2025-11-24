package ac.mdiq.podcini.util

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File


private const val TAG: String = "IntentUtils"

/*
 *  Checks if there is at least one exported activity that can be performed for the intent
 */

fun isCallable(context: Context, intent: Intent?): Boolean {
    val list = context.packageManager.queryIntentActivities(intent!!, PackageManager.MATCH_DEFAULT_ONLY)
    for (info in list) if (info.activityInfo.exported) return true
    return false
}


fun sendLocalBroadcast(context: Context, action: String?) {
    context.sendBroadcast(Intent(action).setPackage(context.packageName))
}


fun openInBrowser(context: Context, url: String) {
    Logd(TAG, "url: $url")
    try {
        val myIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(myIntent)
    } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.pref_no_browser_found)) }
}

fun shareLink(context: Context, text: String) {
    val intent = IntentBuilder(context)
        .setType("text/plain")
        .setText(text)
        .setChooserTitle(R.string.share_url_label)
        .createChooserIntent()
    context.startActivity(intent)
}


fun shareFeedItemLinkWithDownloadLink(context: Context, item: Episode, withPosition: Boolean) {
    var text: String? = item.feed?.title + ": " + item.title
    var pos = 0
    if (withPosition) {
        text += """
                
                ${context.resources.getString(R.string.share_starting_position_label)}: 
                """.trimIndent()
        pos = item.position
        text += getDurationStringLong(pos)
    }

    if (item.getLinkWithFallback() != null) {
        text += """
                
                
                ${context.resources.getString(R.string.share_dialog_episode_website_label)}: 
                """.trimIndent()
        text += item.getLinkWithFallback()
    }

    if (item.downloadUrl != null) {
        text += """
                
                
                ${context.resources.getString(R.string.share_dialog_media_file_label)}: 
                """.trimIndent()
        text += item.downloadUrl
        if (withPosition) {
            text += "#t=" + pos / 1000
        }
    }
    shareLink(context, text!!)
}


fun shareFeedItemFile(context: Context, media: Episode) {
    val lurl = media.fileUrl
    if (lurl.isNullOrEmpty()) return

    val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority), File(lurl))

    IntentBuilder(context)
        .setType(media.mimeType)
        .addStream(fileUri)
        .setChooserTitle(R.string.share_file_label)
        .startChooser()

    Logd(TAG, "shareFeedItemFile called")
}
