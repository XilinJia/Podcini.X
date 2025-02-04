package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.ui.utils.episodeOnDisplay
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logt
import android.content.Context
import android.speech.tts.TextToSpeech
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import java.io.File
import java.util.*

class EpisodeTextVM(val context: Context, val lcScope: CoroutineScope) {

    var episode: Episode? = null    // unmanged

    internal var startIndex = 0
    internal var ttsSpeed = 1.0f

    internal var readerText: String? = null
    internal var cleanedNotes by mutableStateOf<String?>(null)
    internal var readerhtml: String? = null
    internal var readMode by mutableStateOf(true)

    internal var ttsPlaying by mutableStateOf(false)
    internal var jsEnabled by mutableStateOf(false)
    internal var webUrl by mutableStateOf("")

    internal var tts by mutableStateOf<TextToSpeech?>(null)

    init {
        episode = episodeOnDisplay
    }

    internal fun prepareContent() {
        when {
            readMode -> {
                runOnIOScope {
                    if (!episode?.link.isNullOrEmpty()) {
                        if (cleanedNotes == null) {
                            if (episode?.transcript == null) {
                                val url = episode!!.link!!
                                val htmlSource = fetchHtmlSource(url)
                                val article = Readability4JExtended(episode?.link!!, htmlSource).parse()
                                readerText = article.textContent
                                readerhtml = article.contentWithDocumentsCharsetOrUtf8
                            } else {
                                readerhtml = episode!!.transcript
                                readerText = HtmlCompat.fromHtml(readerhtml!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            }
                            if (!readerhtml.isNullOrEmpty()) {
                                val shownotesCleaner = ShownotesCleaner(context)
                                cleanedNotes = shownotesCleaner.processShownotes(readerhtml!!, 0)
                                episode = upsertBlk(episode!!) { it.setTranscriptIfLonger(readerhtml) }
                            }
                        }
                    }
                    if (!cleanedNotes.isNullOrEmpty()) {
                        val file = File(context.filesDir, "test_content.html")
                        file.writeText(cleanedNotes ?: "No content")
                        if (tts == null) {
                            tts = TextToSpeech(context) { status: Int ->
                                if (status == TextToSpeech.SUCCESS) {
                                    if (episode?.feed?.language != null) {
                                        val result = tts?.setLanguage(Locale(episode!!.feed!!.language!!))
                                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                            Logt(TAG, context.getString(R.string.language_not_supported_by_tts) + "${episode?.feed?.language}")
                                        }
                                    }
                                    Logt(TAG, "TTS init success")
                                } else Logt(TAG, context.getString(R.string.tts_init_failed))
                            }
                        }
                        withContext(Dispatchers.Main) {
                            readMode = true
                            Logd(TAG, "cleanedNotes: $cleanedNotes")
                        }
                    } else Logt(TAG, context.getString(R.string.web_content_not_available))
                }
            }
            !episode?.link.isNullOrEmpty() -> {
                webUrl = episode!!.link!!
                readMode = false
            }
            else -> Logt(TAG, context.getString(R.string.web_content_not_available))
        }
    }
}

@Composable
fun EpisodeTextScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(episodeOnDisplay.id) { EpisodeTextVM(context, scope) }

//        val displayUpArrow by remember { derivedStateOf { navController.backQueue.size > 1 } }
//        var upArrowVisible by rememberSaveable { mutableStateOf(displayUpArrow) }
//        LaunchedEffect(navController.backQueue) { upArrowVisible = displayUpArrow }

    var displayUpArrow by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (!vm.episode?.link.isNullOrEmpty()) vm.prepareContent()
                    else Logt(TAG, context.getString(R.string.web_content_not_available))
                }
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.tts?.stop()
            vm.tts?.shutdown()
            vm.tts = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val context = LocalContext.current
        TopAppBar(title = { Text("") }, 
            navigationIcon = { IconButton(onClick = { if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
            }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
            actions = {
                if (vm.readMode && vm.tts != null) {
                    val iconRes = if (vm.ttsPlaying) R.drawable.ic_pause else R.drawable.ic_play_24dp
                    IconButton(onClick = {
                        if (vm.tts!!.isSpeaking) vm.tts?.stop()
                        if (!vm.ttsPlaying) {
                            vm.ttsPlaying = true
                            if (!vm.readerText.isNullOrEmpty()) {
                                vm.ttsSpeed = vm.episode?.feed?.playSpeed ?: 1.0f
                                vm.tts?.setSpeechRate(vm.ttsSpeed)
                                while (vm.startIndex < vm.readerText!!.length) {
                                    val endIndex = minOf(vm.startIndex + MAX_CHUNK_LENGTH, vm.readerText!!.length)
                                    val chunk = vm.readerText!!.substring(vm.startIndex, endIndex)
                                    vm.tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                    vm.startIndex += MAX_CHUNK_LENGTH
                                }
                            }
                        } else vm.ttsPlaying = false
                    }) { Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "home") }
                }
                var showJSIconRes = if (vm.readMode) R.drawable.outline_eyeglasses_24 else R.drawable.javascript_icon_245402
                IconButton(onClick = { vm.jsEnabled = !vm.jsEnabled }) { Icon(imageVector = ImageVector.vectorResource(showJSIconRes), contentDescription = "JS") }
                var homeIconRes = if (vm.readMode) R.drawable.baseline_home_24 else R.drawable.outline_home_24
                IconButton(onClick = {
                    vm.readMode = !vm.readMode
                    vm.jsEnabled = false
                    vm.prepareContent()
                }) { Icon(imageVector = ImageVector.vectorResource(homeIconRes), contentDescription = "switch home") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.readMode && !vm.readerhtml.isNullOrEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = vm.readerhtml!!
                        val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                        val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                        context.startActivity(intent)
                        expanded = false
                    })
                }
            }
        )
    }

    fun Color.toHex(): String {
        val red = (red * 255).toInt().toString(16).padStart(2, '0')
        val green = (green * 255).toInt().toString(16).padStart(2, '0')
        val blue = (blue * 255).toInt().toString(16).padStart(2, '0')
        return "#$red$green$blue"
    }
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        if (vm.readMode) {
            val backgroundColor = MaterialTheme.colorScheme.background.toHex()
            val textColor = MaterialTheme.colorScheme.onBackground.toHex()
            val primaryColor = MaterialTheme.colorScheme.primary.toHex()
            AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = vm.jsEnabled
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                            if (isEmpty) Logd(TAG, "content is empty")
                            view?.evaluateJavascript("document.querySelectorAll('[hidden]').forEach(el => el.removeAttribute('hidden'));", null)
                        }
                    }
                }
            }, update = { webView ->
                webView.settings.javaScriptEnabled = vm.jsEnabled
                val htmlContent = """
                            <html>
                                <style>
                                    body {
                                        background-color: $backgroundColor;
                                        color: $textColor;
                                    }
                                    a {
                                        color: ${primaryColor};
                                    }
                                </style>
                                <body>${vm.cleanedNotes ?: "No notes"}</body>
                            </html>
                        """.trimIndent()
                webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "utf-8", null)
            })
        } else
            AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = vm.jsEnabled
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val isEmpty = view?.title.isNullOrEmpty() && view?.contentDescription.isNullOrEmpty()
                            if (isEmpty) Logd(TAG, "content is empty")
                        }
                    }
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.setSupportZoom(true)
                }
            }, update = {
                it.settings.javaScriptEnabled = vm.jsEnabled
                it.loadUrl(vm.webUrl)
            })
    }
}

private const val TAG: String = "EpisodeTextScreen"
private const val MAX_CHUNK_LENGTH = 2000
