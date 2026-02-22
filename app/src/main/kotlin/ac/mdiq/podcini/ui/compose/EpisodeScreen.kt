package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.net.utils.NetworkUtils.isImageDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Timer
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.Combo
import ac.mdiq.podcini.ui.actions.ActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.bsState
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.ShownotesCleaner
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.openInBrowser
import android.speech.tts.TextToSpeech
import android.text.format.Formatter.formatShortFileSize
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import java.io.File
import java.util.Date
import java.util.Locale

private const val TAG: String = "EpisodeScreen"

private const val MAX_CHUNK_LENGTH = 2000

private val notesCache = LruCache<Long, String>(10)

var episodeForInfo by mutableStateOf<Episode?>(null)

@ExperimentalMaterial3Api
@Composable
fun EpisodeScreen(episode_: Episode, listFlow: StateFlow<List<Episode>> = MutableStateFlow(emptyList()), allowOpenFeed: Boolean = false) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface

    val eOfFlow by listFlow.map { list -> list.firstOrNull { it.id == episode_.id } }.collectAsStateWithLifecycle(initialValue = null)
    val episode = if (eOfFlow != null) eOfFlow!! else episode_

    val episodeFeed = episode.feed
    var showHomeScreen by remember { mutableStateOf(false) }

    val comboAction = remember { Combo() }
    comboAction.ActionOptions()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Logd(TAG, "ON_CREATE episode downloaded: ${episode.downloaded}")
                    Logd(TAG, "ON_CREATE episode downloadurl: ${episode.downloadUrl}")
                    Logd(TAG, "ON_CREATE episode fileurl: ${episode.fileUrl}")
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
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(showHomeScreen) {
        if (showHomeScreen) handleBackSubScreens.add(TAG)
        else handleBackSubScreens.remove(TAG)
        onDispose { handleBackSubScreens.remove(TAG) }
    }

    BackHandler(enabled = handleBackSubScreens.contains(TAG)) {
        Logd(TAG, "BackHandler")
        showHomeScreen = false
    }

    var showShareDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showAddTimerDialog by remember { mutableStateOf(false) }
    var showTimetableDialog by remember { mutableStateOf(false) }
    var onTimer by remember { mutableStateOf(Timer()) }
    var showEditTimerDialog by remember { mutableStateOf(false) }

    @Composable
    fun OpenDialogs() {
        if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.FOREVER, EpisodeState.LATER)) FutureStateDialog(listOf(episode), futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
        if (showShareDialog) ShareDialog(episode) { showShareDialog = false }

        if (showEditTimerDialog) EditTimerDialog(onTimer) { showEditTimerDialog = false }

        if (showAddTimerDialog) AddTimerDialog(episode) { showAddTimerDialog = false }

        if (showTimetableDialog) EpisodeTimetableDialog(episode, { showTimetableDialog = false }) {
            onTimer = it
            showEditTimerDialog = true
        }
    }

    @Composable
    fun EpisodeTextScreen() {
        val lifecycleOwner = LocalLifecycleOwner.current

        var startIndex by remember { mutableIntStateOf(0) }
        var ttsSpeed by remember { mutableFloatStateOf(1.0f) }

        var readerText: String? by remember { mutableStateOf(null) }
        var cleanedNotes by remember { mutableStateOf<String?>(null) }
        var readerhtml: String? by remember { mutableStateOf(null) }
        var readMode by remember { mutableStateOf(true) }

        var ttsPlaying by remember {  mutableStateOf(false) }
        var jsEnabled by remember { mutableStateOf(false) }
        var webUrl by remember { mutableStateOf("") }

        var tts by remember { mutableStateOf<TextToSpeech?>(null) }

        fun prepareContent() {
            when {
                readMode -> {
                    runOnIOScope {
                        if (!episode.link.isNullOrEmpty()) {
                            cleanedNotes = notesCache[episode.id]
                            if (cleanedNotes == null) {
                                if (episode.transcript == null) {
                                    val url = episode.link!!
                                    val htmlSource = fetchHtmlSource(url)
                                    val article = Readability4JExtended(episode.link!!, htmlSource).parse()
                                    readerText = article.textContent
                                    readerhtml = article.contentWithDocumentsCharsetOrUtf8
                                } else {
                                    readerhtml = episode.transcript
                                    readerText = HtmlCompat.fromHtml(readerhtml!!, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                                }
                                if (!readerhtml.isNullOrEmpty()) {
                                    val shownotesCleaner = ShownotesCleaner()
                                    cleanedNotes = shownotesCleaner.processShownotes(readerhtml!!, 0)
                                    runOnIOScope { upsert(episode) { it.setTranscriptIfLonger(readerhtml) } }
                                    notesCache.put(episode.id, cleanedNotes!!)
                                }
                            }
                        }
                        if (!cleanedNotes.isNullOrEmpty()) {
                            val file = File(context.filesDir, "test_content.html")
                            file.writeText(cleanedNotes ?: "No content")
                            if (tts == null) {
                                tts = TextToSpeech(context) { status: Int ->
                                    if (status == TextToSpeech.SUCCESS) {
                                        if (!episodeFeed?.langSet.isNullOrEmpty()) {
                                            val lang = episodeFeed.langSet.first()
                                            val result = tts?.setLanguage(Locale(lang))
                                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
                                                Loge(TAG, context.getString(R.string.language_not_supported_by_tts) + lang)
                                        }
                                        Logt(TAG, "TTS init success")
                                    } else Loge(TAG, context.getString(R.string.tts_init_failed))
                                }
                            }
                            withContext(Dispatchers.Main) {
                                readMode = true
                                Logd(TAG, "cleanedNotes: $cleanedNotes")
                            }
                        } else Loge(TAG, context.getString(R.string.web_content_not_available))
                    }
                }
                !episode.link.isNullOrEmpty() -> {
                    webUrl = episode.link!!
                    readMode = false
                }
                else -> Loge(TAG, context.getString(R.string.web_content_not_available))
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        if (!episode.link.isNullOrEmpty()) prepareContent()
                        else Loge(TAG, context.getString(R.string.web_content_not_available))
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
                tts?.stop()
                tts?.shutdown()
                tts = null
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        
        @Composable
        fun MyTopAppBar() {
            var expanded by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val buttonColor = Color(0xDDFFD700)
            Box {
                TopAppBar(title = { Text("") }, navigationIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "", modifier = Modifier.padding(7.dp).clickable(onClick = { showHomeScreen = false })) },
                    actions = {
                        if (readMode && tts != null) {
                            val iconRes = if (ttsPlaying) R.drawable.ic_pause else R.drawable.ic_play_24dp
                            IconButton(onClick = {
                                if (tts!!.isSpeaking) tts?.stop()
                                if (!ttsPlaying) {
                                    ttsPlaying = true
                                    if (!readerText.isNullOrEmpty()) {
                                        ttsSpeed = episodeFeed?.playSpeed ?: 1.0f
                                        tts?.setSpeechRate(ttsSpeed)
                                        while (startIndex < readerText!!.length) {
                                            val endIndex = minOf(startIndex + MAX_CHUNK_LENGTH, readerText!!.length)
                                            val chunk = readerText!!.substring(startIndex, endIndex)
                                            tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
                                            startIndex += MAX_CHUNK_LENGTH
                                        }
                                    }
                                } else ttsPlaying = false
                            }) { Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "home") }
                        }
                    val showJSIconRes = if (readMode) R.drawable.outline_eyeglasses_24 else R.drawable.javascript_icon_245402
                    IconButton(onClick = { jsEnabled = !jsEnabled }) { Icon(imageVector = ImageVector.vectorResource(showJSIconRes), contentDescription = "JS") }
                    val homeIconRes = if (readMode) R.drawable.baseline_home_24 else R.drawable.outline_home_24
                    IconButton(onClick = {
                        readMode = !readMode
                        jsEnabled = false
                        prepareContent()
                    }) { Icon(imageVector = ImageVector.vectorResource(homeIconRes), contentDescription = "switch home") }
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                        if (readMode && !readerhtml.isNullOrEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                            val notes = readerhtml!!
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                            context.startActivity(intent)
                            expanded = false
                        })
                    }
                })
                HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        fun Color.toHex(): String {
            val red = (red * 255).toInt().toString(16).padStart(2, '0')
            val green = (green * 255).toInt().toString(16).padStart(2, '0')
            val blue = (blue * 255).toInt().toString(16).padStart(2, '0')
            return "#$red$green$blue"
        }

//        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(3.dp), border = BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
                MyTopAppBar()
                if (readMode) {
                    val backgroundColor = MaterialTheme.colorScheme.background.toHex()
                    val textColor = MaterialTheme.colorScheme.onBackground.toHex()
                    val primaryColor = MaterialTheme.colorScheme.primary.toHex()
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = jsEnabled
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
                        webView.settings.javaScriptEnabled = jsEnabled
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
                                <body>${cleanedNotes ?: "No notes"}</body>
                            </html>
                        """.trimIndent()
                        webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "utf-8", null)
                    })
                } else AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                    WebView(it).apply {
                        settings.javaScriptEnabled = jsEnabled
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
                    it.settings.javaScriptEnabled = jsEnabled
                    it.loadUrl(webUrl)
                })
            }
        }
    }

    OpenDialogs()

    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        TopAppBar(title = { },
            actions = {
                if (allowOpenFeed) IconButton(onClick = {
                    if (episodeFeed != null) {
                        navController.navigate("${Screens.FeedDetails.name}?feedId=${episodeFeed.id}")
                        bsState = MainActivity.BSState.Partial
                    }
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_feed), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Open podcast", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                IconButton(onClick = { comboAction.performAction(episode) }) { Icon(imageVector = ImageVector.vectorResource(comboAction.iconRes), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "Combo", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                if (!episode.link.isNullOrEmpty()) IconButton(onClick = { showHomeScreen = true }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_article_shortcut_24), contentDescription = "home") }
                IconButton(onClick = {
                    val url = episode.getLinkWithFallback()
                    if (url != null) openInBrowser(context, url)
                }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                        val notes = episode.description
                        if (!notes.isNullOrEmpty()) {
                            val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                            val context = context
                            val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                            context.startActivity(intent)
                        }
                        expanded = false
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                        showShareDialog = true
                        expanded = false
                    })
                }
            })
    }

    if (showHomeScreen) EpisodeTextScreen()
    else {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(3.dp), border = BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)) {
            val buttonColor = MaterialTheme.colorScheme.tertiary
            val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
            var showAltActionsDialog by remember { mutableStateOf(false) }
            var actionButton by remember { mutableStateOf<ActionButton?>(null) }
            if (showAltActionsDialog) actionButton?.AltActionsDialog(context, onDismiss = { showAltActionsDialog = false })
            LaunchedEffect(key1 = status, episode) {
                actionButton = ActionButton(episode)
                actionButton?.type = when {
                    InTheatre.isCurrentlyPlaying(episode) -> ButtonTypes.PAUSE
                    episodeFeed != null && episodeFeed.isLocalFeed -> ButtonTypes.PLAY_LOCAL
                    episode.downloaded -> ButtonTypes.PLAY
                    !episode.downloadUrl.isNullOrBlank() -> ButtonTypes.STREAM
                    else -> ButtonTypes.NULL
                }
            }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
                MyTopAppBar()
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        SelectionContainer { Text(episode.title?:"", color = textColor, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth()) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (episode.downloadUrl.isNullOrBlank()) Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_error), tint = Color.Red, contentDescription = "error")
                            val playState = remember(episode.playState) { EpisodeState.fromCode(episode.playState) }
                            Icon(imageVector = ImageVector.vectorResource(playState.res), tint = playState.color ?: MaterialTheme.colorScheme.tertiary, contentDescription = "playState", modifier = Modifier.background(if (episode.playState >= EpisodeState.SKIPPED.code) Color.Green.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface).width(16.dp).height(16.dp))
                            if (episode.rating != Rating.UNRATED.code) Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode.rating).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer).width(16.dp).height(16.dp))
                            val pubTimeText by remember(episode.id) { mutableStateOf(formatDateTimeFlex(Date(episode.pubDate))) }
                            val txtvDuration by remember(episode.id) { mutableStateOf(if (episode.duration > 0) durationStringFull(episode.duration) else "") }
                            var txtvSize by remember(episode.id) { mutableStateOf("") }
                            LaunchedEffect(episode.id) {
                                txtvSize = when {
                                    episode.size > 0 -> formatShortFileSize(context, episode.size)
                                    isImageDownloadAllowed && gearbox.canCheckMediaSize(episode) && !episode.isSizeSetUnknown() -> {
                                        withContext(Dispatchers.IO) {
                                            val sizeValue = if (episodeFeed?.prefStreamOverDownload == false) episode.fetchMediaSize() else 0L
                                            if (sizeValue <= 0) "" else formatShortFileSize(context, sizeValue)
                                        }
                                    }
                                    else -> ""
                                }
                            }
                            Text("$pubTimeText · $txtvDuration · $txtvSize", color = textColor, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            val timers = remember(appAttribs.timetable) { appAttribs.timetable.filter { it.episodeId == episode.id }.toMutableStateList() }
                            //                        Logd(TAG, "timers: ${timers.size} ${appAttribs.timetable.size}")
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_timer_24), tint = if (timers.isEmpty()) buttonColor else buttonAltColor, contentDescription = "timer", modifier = Modifier.width(28.dp).height(32.dp).combinedClickable(
                                onClick = {
                                    if (timers.isEmpty()) showAddTimerDialog = true
                                    else showTimetableDialog = true
                                },
                                onLongClick = { showAddTimerDialog = true  }))
                            Spacer(Modifier.weight(0.5f))
                            if (actionButton != null) {
                                val dlStats = downloadStates[episode.downloadUrl]
                                if (dlStats != null) {
                                    actionButton!!.processing = dlStats.progress
                                    if (dlStats.state == DownloadStatus.State.COMPLETED.ordinal) actionButton!!.type = ButtonTypes.PLAY
                                }
                                Icon(imageVector = ImageVector.vectorResource(actionButton!!.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp).combinedClickable(onClick = { actionButton?.onClick(context) }, onLongClick = { showAltActionsDialog = true }))
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    val txtvPodcast by remember(episode) { mutableStateOf( if (episodeFeed != null) (if (episodeFeed.isSynthetic() && episode.origFeedTitle != null) episode.origFeedTitle!! else episodeFeed.title ?: "") else "") }
                    SelectionContainer { Text(txtvPodcast, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 50.dp)) {
                    EpisodeDetails(episode)
                    AsyncImage(ImageRequest.Builder(context).data(episode.imageUrl ?: episodeFeed?.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.drawable.ic_launcher_foreground).error(R.drawable.ic_launcher_foreground).build(), contentDescription = "imgvCover", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(10.dp).clickable(onClick = {}))
                    Text(episode.link ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 15.dp).clickable(onClick = {
                        if (!episode.link.isNullOrBlank()) openInBrowser(context, episode.link!!)
                    }))
                    Row {
                        Text("Time spent: " + getDurationStringShort(episode.timeSpent, true))
                        Spacer(Modifier.width(50.dp))
                        Text("Played duration: " + getDurationStringShort(episode.playedDuration.toLong(), true))
                    }
                }
            }
        }
    }
}
