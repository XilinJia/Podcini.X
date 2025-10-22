package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.FeedUpdateManager.feedUpdateWorkId
import ac.mdiq.podcini.net.feed.FeedUpdateManager.restartUpdateAlarm
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.feed.searcher.CombinedSearcher
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.curMediaId
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.autoBackup
import ac.mdiq.podcini.storage.database.Feeds.cancelMonitorFeeds
import ac.mdiq.podcini.storage.database.Feeds.compileTags
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.monitorFeedList
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.screens.AudioPlayerScreen
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.NavDrawerScreen
import ac.mdiq.podcini.ui.screens.Navigate
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.ui.utils.feedScreenMode
import ac.mdiq.podcini.ui.utils.setOnlineFeedUrl
import ac.mdiq.podcini.ui.utils.setOnlineSearchTerms
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.ui.utils.starter.MainActivityStarter
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.toastMassege
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StrictMode
import android.provider.Settings
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {
    private var lastTheme = 0
//    private var navigationBarInsets = Insets.NONE

    val prefs: SharedPreferences by lazy { getSharedPreferences("MainActivityPrefs", MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        Logt(TAG, getString(R.string.notification_permission_text))
        if (isGranted) {
            checkAndRequestUnrestrictedBackgroundActivity()
            return@registerForActivityResult
        }
        commonConfirm = CommonConfirmAttrib(
            title = getString(R.string.notification_check_permission),
            message = getString(R.string.notification_permission_text),
            confirmRes = android.R.string.ok,
            cancelRes = R.string.cancel_label,
            onConfirm = { checkAndRequestUnrestrictedBackgroundActivity() },
            onCancel = { checkAndRequestUnrestrictedBackgroundActivity() })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun postFornotificationPermission() {
        commonConfirm = CommonConfirmAttrib(
            title = getString(R.string.notification_check_permission),
            message = getString(R.string.notification_permission_text),
            confirmRes = android.R.string.ok,
            cancelRes = R.string.cancel_label,
            onConfirm = { requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onCancel = { checkAndRequestUnrestrictedBackgroundActivity() })
    }

    private var showUnrestrictedBackgroundPermissionDialog by mutableStateOf(false)

    private var hasFeedUpdateObserverStarted = false
    private var hasDownloadObserverStarted = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        lastTheme = getNoTitleTheme(this)
        setTheme(lastTheme)

        if (BuildConfig.DEBUG) {
            val builder = StrictMode.ThreadPolicy.Builder()
                .detectAll()  // Enable all detections
                .penaltyLog()  // Log violations to the console
                .penaltyDropBox()
            StrictMode.setThreadPolicy(builder.build())
        }

        lifecycleScope.launch((Dispatchers.IO)) { compileTags() }

        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) hasInitialized.value = savedInstanceState.getBoolean(INIT_KEY, false)
        if (!hasInitialized.value) hasInitialized.value = true

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(feedUpdateWorkId)
            .observe(this) { workInfos -> workInfos?.forEach { workInfo -> Logd(TAG, "FeedUpdateWork status: ${workInfo.state}") } }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { CustomTheme(this) { MainActivityUI() } }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            postFornotificationPermission()
        else checkAndRequestUnrestrictedBackgroundActivity()

        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
        val lastScheduledVersion = prefs.getString(Extras.lastVersion.name, "0")
        if (currentVersion != lastScheduledVersion) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(feedUpdateWorkId)
            restartUpdateAlarm(applicationContext, true)
            prefs.edit { putString(Extras.lastVersion.name, currentVersion) }
        } else restartUpdateAlarm(applicationContext, false)

        runOnIOScope {  SynchronizationQueueSink.syncNowIfNotSyncedRecently() }

        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData(FeedUpdateManager.WORK_TAG_FEED_UPDATE)
            .observe(this) { workInfos: List<WorkInfo> ->
                if (!hasFeedUpdateObserverStarted) {
                    hasFeedUpdateObserverStarted = true
                    return@observe
                }
                var isRefreshingFeeds = false
                for (workInfo in workInfos) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> isRefreshingFeeds = true
                        else -> {}
                    }
                }
                EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(isRefreshingFeeds))
            }
        observeDownloads()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainActivityUI() {
        lcScope = rememberCoroutineScope()
        val navController = rememberNavController()
        mainNavController = navController
        val sheetState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false))

        if (showUnrestrictedBackgroundPermissionDialog) UnrestrictedBackgroundPermissionDialog { showUnrestrictedBackgroundPermissionDialog = false }

        LaunchedEffect(key1 = isBSExpanded, key2 = curMediaId) {
            if (curMediaId > 0) {
                if (isBSExpanded) lcScope?.launch { sheetState.bottomSheetState.expand() }
                else lcScope?.launch { sheetState.bottomSheetState.partialExpand() }
            } else lcScope?.launch { sheetState.bottomSheetState.hide() }
        }

        val sheetValueState = remember { mutableStateOf(sheetState.bottomSheetState.currentValue) }
        LaunchedEffect(sheetState.bottomSheetState) {
            snapshotFlow { sheetState.bottomSheetState.currentValue }
                .collect { newValue -> sheetValueState.value = newValue }
        }
        val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
        val bottomInsetPadding = bottomInsets.asPaddingValues().calculateBottomPadding()
        val dynamicBottomPadding by remember {
            derivedStateOf {
                when (sheetValueState.value) {
                    SheetValue.Expanded -> bottomInsetPadding + 300.dp
                    SheetValue.PartiallyExpanded -> bottomInsetPadding + 100.dp
                    else -> bottomInsetPadding
                }
            }
        }
//        Logd(TAG, "dynamicBottomPadding: $dynamicBottomPadding sheetValue: ${sheetValueState.value}")
        ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
            BottomSheetScaffold(sheetContent = { AudioPlayerScreen() },
                scaffoldState = sheetState, sheetPeekHeight = bottomInsetPadding + 100.dp,
                sheetDragHandle = {}, sheetSwipeEnabled = false, sheetShape = RectangleShape, topBar = {}
            ) { paddingValues ->
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding(),
                        start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                        end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = dynamicBottomPadding
                    )) {
                    if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                    if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                    CompositionLocalProvider(LocalNavController provides navController) { Navigate(navController) }
                }
            }
        }
    }

    @Composable
    fun UnrestrictedBackgroundPermissionDialog(onDismiss: () -> Unit) {
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text("Permission Required") },
            text = {
                Column {
                    Text(stringResource(R.string.unrestricted_background_permission_text))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontAskAgain, onCheckedChange = { dontAskAgain = it })
                        Text(stringResource(R.string.checkbox_do_not_show_again))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAskAgain) prefs.edit { putBoolean("dont_ask_again_unrestricted_background", true) }
                    val intent = Intent()
                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    this@MainActivity.startActivity(intent)
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    private fun checkAndRequestUnrestrictedBackgroundActivity() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        val dontAskAgain = prefs.getBoolean("dont_ask_again_unrestricted_background", false)
        if (!isIgnoringBatteryOptimizations && !dontAskAgain) showUnrestrictedBackgroundPermissionDialog = true
    }

    private fun observeDownloads() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WorkManager.getInstance(this@MainActivity).pruneWork().await() }
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG)
                .observe(this@MainActivity) { workInfos: List<WorkInfo> ->
                    if (!hasDownloadObserverStarted) {
                        hasDownloadObserverStarted = true
                        return@observe
                    }
                    Logd(TAG, "workInfos: ${workInfos.size}")
                    downloadStates.clear()
                    var hasFinished = false
                    for (workInfo in workInfos) {
                        var downloadUrl: String? = null
                        for (tag in workInfo.tags) {
                            if (tag.startsWith(DownloadServiceInterface.WORK_TAG_EPISODE_URL)) downloadUrl = tag.substring(DownloadServiceInterface.WORK_TAG_EPISODE_URL.length)
                        }
                        if (downloadUrl == null) continue
                        Logd(TAG, "workInfo.state: ${workInfo.state} ${workInfo.state.isFinished}")
                        var status: Int = when (workInfo.state) {
                            WorkInfo.State.RUNNING -> DownloadStatus.State.RUNNING.ordinal
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.State.QUEUED.ordinal
                            WorkInfo.State.SUCCEEDED -> DownloadStatus.State.COMPLETED.ordinal
                            WorkInfo.State.FAILED -> {
                                Loge(TAG, "download failed $downloadUrl")
                                DownloadStatus.State.INCOMPLETE.ordinal
                            }
                            WorkInfo.State.CANCELLED -> {
                                Logt(TAG, "download cancelled $downloadUrl")
                                DownloadStatus.State.INCOMPLETE.ordinal
                            }
                        }
                        var progress = workInfo.progress.getInt(DownloadServiceInterface.WORK_DATA_PROGRESS, -1)
                        if (progress == -1 && status < DownloadStatus.State.COMPLETED.ordinal) {
                            status = DownloadStatus.State.QUEUED.ordinal
                            progress = 0
                        }
                        downloadStates[downloadUrl] = DownloadStatus(status, progress)
                        Logd(TAG, "downloadStates: ${downloadStates.size}")
                        if (workInfo.state.isFinished) hasFinished = true
                    }
                    DownloadServiceInterface.impl?.setCurrentDownloads(downloadStates)
                    if (hasFinished) lifecycleScope.launch(Dispatchers.IO) {
                        delay(2000)
                        WorkManager.getInstance(this@MainActivity).pruneWork().await()
                    }
                }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(Extras.generated_view_id.name, View.generateViewId())
        outState.putBoolean(INIT_KEY, hasInitialized.value)
    }

    override fun onDestroy() {
        Logd(TAG, "onDestroy")
        WorkManager.getInstance(this).pruneWork()
        WorkManager.getInstance(applicationContext).pruneWork()
        super.onDestroy()
    }

    public override fun onStart() {
        super.onStart()
        procFlowEvents()
        RatingDialog.init(this)
        monitorFeedList(lifecycleScope)
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        cancelMonitorFeeds()
    }

    override fun onResume() {
        super.onResume()
        autoBackup(this)
        handleNavIntent()
        RatingDialog.check()
        if (lastTheme != getNoTitleTheme(this)) {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lastTheme = getNoTitleTheme(this) // Don't recreate activity when a result is pending
    }

    private var eventSink: Job?     = null
    private var eventStickySink: Job? = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
        eventStickySink?.cancel()
        eventStickySink = null
    }
    private fun procFlowEvents() {
        if (eventSink == null) eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.MessageEvent -> {
                        if (event.action != null)
                            commonConfirm = CommonConfirmAttrib(
                                title = event.message,
                                message = event.actionText ?: "",
                                confirmRes = R.string.confirm_label,
                                cancelRes = R.string.no,
                                onConfirm = { event.action(this@MainActivity) })
                        else Logt(TAG, event.message)
                    }
                    else -> {}
                }
            }
        }
        if (eventStickySink == null) eventStickySink = lifecycleScope.launch {
            EventFlow.stickyEvents.collectLatest { event -> Logd(TAG, "Received sticky event: ${event.TAG}") }
        }
    }

    private fun handleNavIntent() {
        Logd(TAG, "handleNavIntent()")
        val intent = intent
        when {
            intent.hasExtra(Extras.fragment_feed_id.name) -> {
                val feedId = intent.getLongExtra(Extras.fragment_feed_id.name, 0)
                Logd(TAG, "handleNavIntent: feedId: $feedId")
                if (feedId > 0) {
                    feedOnDisplay = getFeed(feedId) ?: Feed()
                    feedScreenMode = FeedScreenMode.List
                    mainNavController.navigate(Screens.FeedDetails.name)
                }
                isBSExpanded = false
            }
            intent.hasExtra(Extras.fragment_feed_url.name) -> {
                val feedurl = intent.getStringExtra(Extras.fragment_feed_url.name)
                val isShared = intent.getBooleanExtra(Extras.isShared.name, false)
                if (feedurl != null) {
                    setOnlineFeedUrl(feedurl, shared = isShared)
                    mainNavController.navigate(Screens.OnlineFeed.name)
                }
            }
            intent.hasExtra(Extras.search_string.name) -> {
                val query = intent.getStringExtra(Extras.search_string.name)
                setOnlineSearchTerms(CombinedSearcher::class.java, query)
                mainNavController.navigate(Screens.OnlineResults.name)
            }
            intent.getBooleanExtra(MainActivityStarter.Extras.open_player.name, false) -> {
                isBSExpanded = true
            }
            else -> handleDeeplink(intent.data)
        }
        if (intent.getBooleanExtra(Extras.refresh_on_start.name, false)) runOnceOrAsk(this)

        // to avoid handling the intent twice when the configuration changes
        setIntent(Intent(this@MainActivity, MainActivity::class.java))
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    /**
     * Handles the deep link incoming via App Actions.
     * Performs an in-app search or opens the relevant feature of the app depending on the query
     * @param uri incoming deep link
     */
    private fun handleDeeplink(uri: Uri?) {
        if (uri?.path == null) return
        Logd(TAG, "Handling deeplink: $uri")
        when (uri.path) {
            "/deeplink/search" -> {
                val query = uri.getQueryParameter("query") ?: return
                setSearchTerms(query)
                mainNavController.navigate(Screens.Search.name)
            }
            "/deeplink/main" -> {
                val feature = uri.getQueryParameter("page") ?: return
                when (feature) {
                    "EPISODES" -> mainNavController.navigate(Screens.Facets.name)
                    "QUEUE" -> mainNavController.navigate(Screens.Queues.name)
                    "SUBSCRIPTIONS" -> mainNavController.navigate(Screens.Subscriptions.name)
                    "STATISTCS" -> mainNavController.navigate(Screens.Statistics.name)
                    else -> {
                        Logt(TAG, getString(R.string.app_action_not_found) + feature)
                        return
                    }
                }
            }
            else -> {}
        }
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        lastVersion,
        fragment_feed_id,
        fragment_feed_url,
        refresh_on_start,
        started_from_share, // TODO: seems not needed
        add_to_back_stack,
        generated_view_id,
        search_string,
        isShared
    }

    companion object {
        private val TAG: String = MainActivity::class.simpleName ?: "Anonymous"
        private const val INIT_KEY = "app_init_state"

        var hasInitialized = mutableStateOf(false)
        val downloadStates = mutableStateMapOf<String, DownloadStatus>()

        lateinit var mainNavController: NavHostController
        val LocalNavController = staticCompositionLocalOf<NavController> { error("NavController not provided") }

        val drawerState = DrawerState(initialValue = DrawerValue.Closed)
        var lcScope: CoroutineScope? = null

        fun openDrawer() {
            lcScope?.launch { drawerState.open() }
        }

        fun closeDrawer() {
            lcScope?.launch { drawerState.close() }
        }

        var isBSExpanded by mutableStateOf(false)

        @JvmStatic
        fun getIntentToOpenFeed(context: Context, feedId: Long): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.fragment_feed_id.name, feedId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun showOnlineFeed(context: Context, feedUrl: String, isShared: Boolean = false): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.fragment_feed_url.name, feedUrl)
            intent.putExtra(Extras.isShared.name, isShared)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        @JvmStatic
        fun showOnlineSearch(context: Context, query: String): Intent {
            val intent = Intent(context.applicationContext, MainActivity::class.java)
            intent.putExtra(Extras.search_string.name, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }
}
