package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.ShownotesCleaner
import ac.mdiq.podcini.util.isCallable
import ac.mdiq.podcini.util.openInBrowser
import ac.mdiq.podcini.util.shareLink
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.size
import kotlin.math.max

class ShownotesWebView : WebView, View.OnLongClickListener {
    /**
     * URL that was selected via long-press.
     */
    private var selectedUrl: String? = null
    private var timecodeSelectedListener: ((Int) -> Unit)? = null
    private var pageFinishedListener: (()->Unit)? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        setBackgroundColor(Color.TRANSPARENT)
        // Use cached resources, even if they have expired
        if (!NetworkUtils.networkAvailable()) getSettings().cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        getSettings().mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        getSettings().useWideViewPort = false
        getSettings().loadWithOverviewMode = true
        setOnLongClickListener(this)

        setWebViewClient(object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (ShownotesCleaner.isTimecodeLink(url) && timecodeSelectedListener != null) timecodeSelectedListener!!(ShownotesCleaner.getTimecodeLinkTime(url))
                else openInBrowser(context, url)
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Logd(TAG, "Page finished")
                pageFinishedListener?.invoke()
            }
        })
    }

     override fun onLongClick(v: View): Boolean {
        val r: HitTestResult = getHitTestResult()
        when (r.type) {
            HitTestResult.SRC_ANCHOR_TYPE -> {
                Logd(TAG, "Link of webview was long-pressed. Extra: " + r.extra)
                selectedUrl = r.extra
                showContextMenu()
                return true
            }
            HitTestResult.EMAIL_TYPE -> {
                Logd(TAG, "E-Mail of webview was long-pressed. Extra: " + r.extra)
                ContextCompat.getSystemService(context, ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Podcini", r.extra))
                // TODO: is checking SDK_INT <= 32 necessary?
                Logt(TAG, context.getString(R.string.copied_to_clipboard))
                return true
            }
            else -> {
                selectedUrl = null
                return false
            }
        }
    }

    private fun onContextItemSelected(item: MenuItem): Boolean {
        if (selectedUrl == null) return false
        val itemId = item.itemId
        when (itemId) {
            R.id.open_in_browser_item -> if (selectedUrl != null) openInBrowser(context, selectedUrl!!)
            R.id.share_url_item -> if (selectedUrl != null) shareLink(context, selectedUrl!!)
            R.id.copy_url_item -> {
                val clipData: ClipData = ClipData.newPlainText(selectedUrl, selectedUrl)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(clipData)
                Logt(TAG, context.getString(R.string.copied_to_clipboard))
            }
            R.id.go_to_position_item -> {
                if (ShownotesCleaner.isTimecodeLink(selectedUrl) && timecodeSelectedListener != null)
                    timecodeSelectedListener!!(ShownotesCleaner.getTimecodeLinkTime(selectedUrl))
                else Loge(TAG, "Selected go_to_position_item, but URL was not timecode link: $selectedUrl")
            }
            else -> {
                selectedUrl = null
                return false
            }
        }
        selectedUrl = null
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu) {
        super.onCreateContextMenu(menu)
        if (selectedUrl == null) return
        if (ShownotesCleaner.isTimecodeLink(selectedUrl)) {
            menu.add(Menu.NONE, R.id.go_to_position_item, Menu.NONE, R.string.go_to_position_label)
            menu.setHeaderTitle(getDurationStringLong(ShownotesCleaner.getTimecodeLinkTime(selectedUrl)))
        } else {
            val uri = selectedUrl!!.toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (isCallable(context, intent)) menu.add(Menu.NONE, R.id.open_in_browser_item, Menu.NONE, R.string.open_in_browser_label)
            menu.add(Menu.NONE, R.id.copy_url_item, Menu.NONE, R.string.copy_url_label)
            menu.add(Menu.NONE, R.id.share_url_item, Menu.NONE, R.string.share_url_label)
            menu.setHeaderTitle(selectedUrl)
        }
        setOnClickListeners(menu) { item: MenuItem -> this.onContextItemSelected(item) }
    }

    /**
     * When pressing a context menu item, Android calls onContextItemSelected
     * for ALL fragments in arbitrary order, not just for the fragment that the
     * context menu was created from. This assigns the listener to every menu item,
     * so that the correct fragment is always called first and can consume the click.
     *
     * Note that Android still calls the onContextItemSelected methods of all fragments
     * when the passed listener returns false.
     */
    private fun setOnClickListeners(menu: Menu?, listener: MenuItem.OnMenuItemClickListener?) {
        for (i in 0 until menu!!.size) {
            if (menu[i].subMenu != null) setOnClickListeners(menu[i].subMenu, listener)
            menu[i].setOnMenuItemClickListener(listener)
        }
    }

    fun setTimecodeSelectedListener(timecodeSelectedListener: ((Int) -> Unit)?) {
        this.timecodeSelectedListener = timecodeSelectedListener
    }

    fun setPageFinishedListener(pageFinishedListener: (()->Unit)?) {
        this.pageFinishedListener = pageFinishedListener
    }

    @Deprecated("Deprecated in Java")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(max(measuredWidth, minimumWidth), max(measuredHeight, minimumHeight))
    }

    companion object {
        private val TAG: String = ShownotesWebView::class.simpleName ?: "Anonymous"
    }
}
