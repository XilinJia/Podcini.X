package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.net.toUri
import java.net.URLDecoder

class ShareReceiverActivity : ComponentActivity() {
    private var sharedText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logd(TAG, "intent: $intent")
        when {
            intent.hasExtra(ARG_FEEDURL) -> sharedText = intent.getStringExtra(ARG_FEEDURL)
            intent.action == Intent.ACTION_SEND -> sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_VIEW -> sharedText = intent.dataString
        }
        if (sharedText.isNullOrBlank()) {
            Loge(TAG, "feedUrl is empty or null.\n" + getString(R.string.null_value_podcast_error))
            return
        }
        val regex = Regex("""https?://[^\s'"<>]+""")
        val rawUrl = regex.find(sharedText!!)?.value
        val text = rawUrl?.toUri()?.getQueryParameter("url")?.let { URLDecoder.decode(it, "UTF-8") } ?: rawUrl ?: sharedText!!
        Logd(TAG, "feedUrl: $sharedText")
        val log = ShareLog(text)
        upsertBlk(log) {}

        receiveShared(text,this, true) {
            setContent {
                val showDialog = remember { mutableStateOf(true) }
                CustomTheme(this) {
                    gearbox.ConfirmAddEpisode(listOf(text), showDialog.value, onDismissRequest = {
                        showDialog.value = false
                        finish()
                    })
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private val TAG: String = ShareReceiverActivity::class.simpleName ?: "Anonymous"

        const val ARG_FEEDURL: String = "arg.feedurl"
        private const val RESULT_ERROR = 2

        fun receiveShared(sharedText: String, activity: ComponentActivity, finish: Boolean, mediaCB: ()->Unit) {
            val log = realm.query(ShareLog::class).query("url == $0", sharedText).first().find()
            when {
//            plain text
//                sharedUrl.matches(Regex("^[^\\s<>/]+\$")) -> {
                sharedText.matches(Regex("^[^<>/]+$")) -> {  // include spaces
                    if (log != null)  upsertBlk(log) {it.type = ShareLog.Type.Text.name }
                    Logt(TAG, "Activity is started with text $sharedText")
                    val intent = MainActivity.showOnlineSearch(activity, sharedText)
                    activity.startActivity(intent)
                    if (finish) activity.finish()
                }
                // extension media
                gearbox.canHandleShared(sharedText) -> gearbox.handleShared(log, mediaCB)
//              podcast or other?
                else -> {
                    if (log != null) upsertBlk(log) { it.type = ShareLog.Type.Podcast.name }
                    Logt(TAG, "Activity is started with url $sharedText")
                    val intent = MainActivity.showOnlineFeed(activity, sharedText, true)
                    activity.startActivity(intent)
                    if (finish) activity.finish()
                }
            }
        }
    }
}
