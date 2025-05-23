package ac.mdiq.podcini.util

import ac.mdiq.podcini.R
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri


object IntentUtils {
    private val TAG: String = IntentUtils::class.simpleName ?: "Anonymous"

    /*
     *  Checks if there is at least one exported activity that can be performed for the intent
     */
    @JvmStatic
    fun isCallable(context: Context, intent: Intent?): Boolean {
        val list = context.packageManager.queryIntentActivities(intent!!, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in list) if (info.activityInfo.exported) return true
        return false
    }

    @JvmStatic
    fun sendLocalBroadcast(context: Context, action: String?) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    @JvmStatic
    fun openInBrowser(context: Context, url: String) {
        Logd(TAG, "url: $url")
        try {
            val myIntent = Intent(Intent.ACTION_VIEW, url.toUri())
            myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(myIntent)
        } catch (e: ActivityNotFoundException) { Logs(TAG, e, context.getString(R.string.pref_no_browser_found)) }
    }
}
