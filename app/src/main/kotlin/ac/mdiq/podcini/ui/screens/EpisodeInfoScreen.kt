package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.utils.NetworkUtils.fetchHtmlSource
import ac.mdiq.podcini.net.utils.NetworkUtils.isImageDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.storage.database.addToAssOrActQueue
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.removeFromQueue
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.getDurationStringLong
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.EpisodeActionButton
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.downloadStates
import ac.mdiq.podcini.ui.compose.ChaptersColumn
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommentEditingDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeClips
import ac.mdiq.podcini.ui.compose.EpisodeMarks
import ac.mdiq.podcini.ui.compose.FutureStateDialog
import ac.mdiq.podcini.ui.compose.IgnoreEpisodesDialog
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.compose.RelatedEpisodesDialog
import ac.mdiq.podcini.ui.compose.ShareDialog
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.utils.ShownotesWebView
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.ShownotesCleaner
import ac.mdiq.podcini.utils.UsageStatistics
import ac.mdiq.podcini.utils.formatDateTimeFlex
import ac.mdiq.podcini.utils.openInBrowser
import android.content.ContextWrapper
import android.speech.tts.TextToSpeech
import android.text.format.Formatter.formatShortFileSize
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ShareCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import java.io.File
import java.util.Date
import java.util.Locale

private const val TAG: String = "EpisodeInfoScreen"

private const val MAX_CHUNK_LENGTH = 2000

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeInfoScreen(episodeId: Long = 0L) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val textColor = MaterialTheme.colorScheme.onSurface

    var episodeFlow by remember { mutableStateOf<Flow<SingleQueryChange<Episode>>>(emptyFlow()) }

    val actMain: MainActivity? = remember {  generateSequence(context) { if (it is ContextWrapper) it.baseContext else null }.filterIsInstance<MainActivity>().firstOrNull() }

    lateinit var shownotesCleaner: ShownotesCleaner

    var playerLocal: ExoPlayer? by remember { mutableStateOf(null) }

    var episode by remember { mutableStateOf<Episode?>(null) }   // managed

    var txtvSize by remember { mutableStateOf("") }
    var webviewData by remember { mutableStateOf("") }
    var showHomeScreen by remember { mutableStateOf(false) }
    var actionButton by remember { mutableStateOf<EpisodeActionButton?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> episodeFlow = realm.query<Episode>("id == $0", episodeId).first().asFlow()
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            episode = null
            playerLocal?.release()
            playerLocal = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val episodeChange by episodeFlow.collectAsState(initial = null)
    LaunchedEffect(episodeChange, episodeId) {
        episode = episodeChange?.obj ?: return@LaunchedEffect

        actionButton = EpisodeActionButton(episode!!)
        shownotesCleaner = ShownotesCleaner(context)

        when {
            episode == null -> txtvSize = ""
            episode!!.size > 0 -> txtvSize = formatShortFileSize(context, episode!!.size)
            isImageDownloadAllowed && !episode!!.isSizeSetUnknown() -> {
                scope.launch {
                    val sizeValue = if (episode?.feed?.prefStreamOverDownload == false) episode?.fetchMediaSize() ?: 0L else 0L
                    txtvSize = if (sizeValue <= 0) "" else formatShortFileSize(context, sizeValue)
                }
            }
            else -> txtvSize = ""
        }

        if (episode != null && webviewData.isBlank()) {
            Logd(TAG, "description: ${episode?.description}")
            scope.launch(Dispatchers.IO) {
                val webDataPair = gearbox.buildWebviewData(episode!!, shownotesCleaner)
                withContext(Dispatchers.Main) {
                    webviewData = webDataPair?.second ?: shownotesCleaner.processShownotes(episode!!.description ?: "", episode!!.duration)
                }
            }
        }
        if (!episode?.clips.isNullOrEmpty()) playerLocal = ExoPlayer.Builder(context).build()
    }

    var offerStreaming by remember { mutableStateOf(false) }

    var showShareDialog by remember { mutableStateOf(false) }
    var showOnDemandConfigDialog by remember { mutableStateOf(false) }
    var showEditComment by remember { mutableStateOf(false) }
    var showChooseRatingDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var showIgnoreDialog by remember { mutableStateOf(false) }
    var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
    var showPlayStateDialog by remember { mutableStateOf(false) }
    var showTagsSettingDialog by remember { mutableStateOf(false) }

    @Composable
    fun OpenDialogs() {
        // TODO: this is not fired ??
        @Composable
        fun OnDemandConfigDialog(onDismiss: () -> Unit) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { },
                text = { Text(stringResource(if (offerStreaming) R.string.on_demand_config_stream_text else R.string.on_demand_config_download_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (offerStreaming) AppPreferences.prefStreamOverDownload = true
                        if (episode?.feed != null) episode!!.feed = upsertBlk(episode!!.feed!!) { it.prefStreamOverDownload = offerStreaming }
                        // Update all visible lists to reflect new streaming action button
                        //                        load()
                        //                        if (episode != null && webviewData.isBlank()) {
                        //                            Logd(TAG, "description: ${episode?.description}")
                        //                            val webDataPair = gearbox.buildWebviewData(episode!!, shownotesCleaner)
                        //                            webviewData = webDataPair?.second ?: shownotesCleaner.processShownotes(episode!!.description ?: "", episode!!.duration)
                        //                        }
                        Logt(TAG, context.getString(R.string.on_demand_config_setting_changed))
                        onDismiss()
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = {
                    UsageStatistics.doNotAskAgain(UsageStatistics.ACTION_STREAM)
                    onDismiss()
                }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }

        if (showChooseRatingDialog) ChooseRatingDialog(listOf(episode!!)) { showChooseRatingDialog = false }
        if (showIgnoreDialog) IgnoreEpisodesDialog(listOf(episode!!), onDismissRequest = { showIgnoreDialog = false })
        if (showPlayStateDialog) PlayStateDialog(listOf(episode!!), onDismissRequest = { showPlayStateDialog = false }, { futureState = it },{ showIgnoreDialog = true })
        if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(listOf(episode!!), futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
        if (showShareDialog && episode != null && actMain != null) ShareDialog(episode!!, actMain) { showShareDialog = false }
//        if (showChaptersDialog && episode != null) ChaptersDialog(media = episode!!, onDismissRequest = {showChaptersDialog = false})
        if (showOnDemandConfigDialog) OnDemandConfigDialog { showOnDemandConfigDialog = false }
        if (showEditComment && episode != null) {
            var commentText by remember { mutableStateOf(TextFieldValue(episode?.compileCommentText() ?: "")) }
            CommentEditingDialog(textState = commentText, onTextChange = { commentText = it }, onDismissRequest = { showEditComment = false},
                onSave = { upsertBlk(episode!!) { it.addComment(commentText.text, false) } })
        }
        if (showTagsSettingDialog) TagSettingDialog(TagType.Episode, episode!!.tags, onDismiss = { showTagsSettingDialog = false }) { tags ->
            upsertBlk(episode!!) { it.tags.addAll(tags) }
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
                                        if (!episode?.feed?.languages.isNullOrEmpty()) {
                                            val lang = episode!!.feed!!.languages.first()
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
                !episode?.link.isNullOrEmpty() -> {
                    webUrl = episode!!.link!!
                    readMode = false
                }
                else -> Loge(TAG, context.getString(R.string.web_content_not_available))
            }
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> {
                        if (!episode?.link.isNullOrEmpty()) prepareContent()
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

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun MyTopAppBar() {
            var expanded by remember { mutableStateOf(false) }
            val context = LocalContext.current
            val buttonColor = Color(0xDDFFD700)
            Box {
                TopAppBar(title = { Text("") }, navigationIcon = {
                    IconButton(onClick = { showHomeScreen = false }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") }
                }, actions = {
                    if (readMode && tts != null) {
                        val iconRes = if (ttsPlaying) R.drawable.ic_pause else R.drawable.ic_play_24dp
                        IconButton(onClick = {
                            if (tts!!.isSpeaking) tts?.stop()
                            if (!ttsPlaying) {
                                ttsPlaying = true
                                if (!readerText.isNullOrEmpty()) {
                                    ttsSpeed = episode?.feed?.playSpeed ?: 1.0f
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

        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            if (readMode) {
                val backgroundColor = MaterialTheme.colorScheme.background.toHex()
                val textColor = MaterialTheme.colorScheme.onBackground.toHex()
                val primaryColor = MaterialTheme.colorScheme.primary.toHex()
                AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = { context ->
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
            } else
                AndroidView(modifier = Modifier.padding(innerPadding).fillMaxSize(), factory = {
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

    OpenDialogs()

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.previousBackStackEntry?.savedStateHandle?.set("returned", true)
                    navController.popBackStack()
                } else openDrawer()
            }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } },
                actions = {
                    IconButton(onClick = { showPlayStateDialog = true }) { Icon(imageVector = ImageVector.vectorResource(EpisodeState.fromCode(episode?.playState ?: EpisodeState.UNSPECIFIED.code).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "isPlayed", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                    if (episode != null) {
                        val inQueue by remember(episode) { mutableStateOf(if (episode == null) false else (episode!!.feed?.queue ?: actQueue).contains(episode!!)) }
                        if (!inQueue) IconButton(onClick = { runOnIOScope { addToAssOrActQueue(episode!!) } }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_format_list_bulleted_add_24), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                        else IconButton(onClick = { runOnIOScope { removeFromQueue(episode!!.feed?.queue ?: actQueue, listOf(episode!!), EpisodeState.UNPLAYED) } }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_playlist_remove), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "inQueue", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                    }
                    IconButton(onClick = { showChooseRatingDialog = true }) { Icon(imageVector = ImageVector.vectorResource(Rating.fromCode(episode?.rating ?: Rating.UNRATED.code).res), tint = MaterialTheme.colorScheme.tertiary, contentDescription = "rating", modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer)) }
                    if (!episode?.link.isNullOrEmpty()) IconButton(onClick = { showHomeScreen = true
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.outline_article_shortcut_24), contentDescription = "home") }
                    IconButton(onClick = {
                        val url = episode?.getLinkWithFallback()
                        if (url != null) openInBrowser(context, url)
                    }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_web), contentDescription = "web") }
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                        if (episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_notes_label)) }, onClick = {
                            val notes = episode!!.description
                            if (!notes.isNullOrEmpty()) {
                                val shareText = HtmlCompat.fromHtml(notes, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
                                val context = context
                                val intent = ShareCompat.IntentBuilder(context).setType("text/plain").setText(shareText).setChooserTitle(R.string.share_notes_label).createChooserIntent()
                                context.startActivity(intent)
                            }
                            expanded = false
                        })
                        if (episode != null) DropdownMenuItem(text = { Text(stringResource(R.string.share_label)) }, onClick = {
                            showShareDialog = true
                            expanded = false
                        })
                    }
                })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    fun openPodcast() {
        if (episode?.feedId == null) return
        navController.navigate("${Screens.FeedDetails.name}?feedId=${episode?.feedId}")
    }

    if (showHomeScreen) EpisodeTextScreen()
    else {
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            val buttonColor = MaterialTheme.colorScheme.tertiary
            var showAltActionsDialog by remember { mutableStateOf(false) }
            if (showAltActionsDialog) actionButton?.AltActionsDialog(context, onDismiss = { showAltActionsDialog = false })
            LaunchedEffect(key1 = status, episode) {
                actionButton?.type = when {
                    InTheatre.isCurrentlyPlaying(episode) -> ButtonTypes.PAUSE
                    episode?.feed != null && episode!!.feed!!.isLocalFeed -> ButtonTypes.PLAYLOCAL
                    episode?.downloaded == true -> ButtonTypes.PLAY
                    !episode?.downloadUrl.isNullOrBlank() -> ButtonTypes.STREAM
                    else -> ButtonTypes.NULL
                }
            }
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    val txtvPodcast by remember(episode) { mutableStateOf( if (episode?.feed != null) (if (episode!!.feed!!.isSynthetic() && episode!!.origFeedTitle != null) episode!!.origFeedTitle!! else episode!!.feed!!.title ?: "") else "") }
                    SelectionContainer { Text(txtvPodcast, color = textColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { openPodcast() }) }
                }
                val img = remember(episode?.imageUrl) { ImageRequest.Builder(context).data(episode?.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = img, contentDescription = "imgvCover",  modifier = Modifier.width(80.dp).height(80.dp).clickable(onClick = { openPodcast() }))
                    Box(Modifier.weight(1f).padding(start = 10.dp).height(80.dp)) {
                        Column {
                            SelectionContainer { Text(episode?.title?:"", color = textColor, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            val pubTimeText by remember(episode) { mutableStateOf( if (episode?.pubDate != null) formatDateTimeFlex(Date(episode!!.pubDate)) else "" ) }
                            val txtvDuration by remember(episode) { mutableStateOf(if ((episode?.duration ?: 0) > 0) getDurationStringLong(episode!!.duration) else "") }
                            Text("$pubTimeText · $txtvDuration · $txtvSize", color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (actionButton != null && episode != null) {
                            val dlStats = downloadStates[episode!!.downloadUrl]
                            if (dlStats != null) {
                                actionButton!!.processing = dlStats.progress
                                if (dlStats.state == DownloadStatus.State.COMPLETED.ordinal) actionButton!!.type = ButtonTypes.PLAY
                            }
                            Icon(imageVector = ImageVector.vectorResource(actionButton!!.drawable), tint = buttonColor, contentDescription = null, modifier = Modifier.width(28.dp).height(32.dp).align(Alignment.BottomEnd).combinedClickable(onClick = { actionButton?.onClick(context) }, onLongClick = { showAltActionsDialog = true }))
                        }
                        if (episode?.downloadUrl.isNullOrBlank()) Text("noMediaLabel", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("Tags: ${episode?.tagsAsString?:""}", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showTagsSettingDialog = true })
                    Text(stringResource(R.string.my_opinion_label) + if (episode?.comment.isNullOrBlank()) " (Add)" else "", color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable { showEditComment = true })
                    Text(episode?.comment ?: "", color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, bottom = 10.dp))
                    EpisodeMarks(episode)
                    EpisodeClips(episode, playerLocal)
//                    if (!episode?.chapters.isNullOrEmpty()) Text(stringResource(id = R.string.chapters_label), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 5.dp).clickable(onClick = { showChaptersDialog = true }))
                    if (!episode?.chapters.isNullOrEmpty()) ChaptersColumn(episode!!)
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                        ShownotesWebView(context).apply {
                            setTimecodeSelectedListener { time: Int -> mPlayer?.seekTo(time) }
                            setPageFinishedListener { postDelayed({ }, 50) }    // Restoring the scroll position might not always work
                        }
                    }, update = { it.loadDataWithBaseURL("https://127.0.0.1", webviewData, "text/html", "utf-8", "about:blank") })
                    if (!episode?.related.isNullOrEmpty()) {
                        var showTodayStats by remember { mutableStateOf(false) }
                        if (showTodayStats) RelatedEpisodesDialog(episode!!) { showTodayStats = false }
                        Text(stringResource(R.string.related), color = MaterialTheme.colorScheme.primary, style = CustomTextStyles.titleCustom, modifier = Modifier.padding(start = 15.dp, top = 10.dp, bottom = 10.dp).clickable(onClick = { showTodayStats = true }))
                    }
                    AsyncImage(img, contentDescription = "imgvCover", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 10.dp).clickable(onClick = {}))
                    Text(episode?.link ?: "", color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 15.dp).clickable(onClick = {
                        if (!episode?.link.isNullOrBlank()) openInBrowser(context, episode!!.link!!)
                    }))
                    Row {
                        Text("Time spent: " + getDurationStringShort(episode?.timeSpent ?: 0L, true))
                        Spacer(Modifier.width(50.dp))
                        Text("Played duration: " + getDurationStringShort(episode?.playedDuration?.toLong() ?: 0L, true))
                    }
                }
            }
        }
    }
}
