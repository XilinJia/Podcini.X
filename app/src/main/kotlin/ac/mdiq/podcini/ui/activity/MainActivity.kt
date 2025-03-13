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
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.autoBackup
import ac.mdiq.podcini.receiver.MediaButtonReceiver.Companion.createIntent
import ac.mdiq.podcini.storage.database.Feeds.buildTags
import ac.mdiq.podcini.storage.database.Feeds.cancelMonitorFeeds
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.Feeds.monitorFeeds
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.screens.AudioPlayerScreen
import ac.mdiq.podcini.ui.screens.DiscoveryScreen
import ac.mdiq.podcini.ui.screens.EpisodeInfoScreen
import ac.mdiq.podcini.ui.screens.EpisodeTextScreen
import ac.mdiq.podcini.ui.screens.FacetsScreen
import ac.mdiq.podcini.ui.screens.FeedDetailsScreen
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.ui.screens.FeedSettingsScreen
import ac.mdiq.podcini.ui.screens.LogsScreen
import ac.mdiq.podcini.ui.screens.NavDrawerScreen
import ac.mdiq.podcini.ui.screens.OnlineFeedScreen
import ac.mdiq.podcini.ui.screens.OnlineSearchScreen
import ac.mdiq.podcini.ui.screens.QueuesScreen
import ac.mdiq.podcini.ui.screens.SearchResultsScreen
import ac.mdiq.podcini.ui.screens.SearchScreen
import ac.mdiq.podcini.ui.screens.StatisticsScreen
import ac.mdiq.podcini.ui.screens.SubscriptionsScreen
import ac.mdiq.podcini.ui.screens.getLastNavScreen
import ac.mdiq.podcini.ui.screens.getLastNavScreenArg
import ac.mdiq.podcini.ui.screens.saveLastNavScreen
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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StrictMode
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity() {
    private var lastTheme = 0
    private var navigationBarInsets = Insets.NONE

    val prefs: SharedPreferences by lazy { getSharedPreferences("MainActivityPrefs", MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        Logt(TAG, getString(R.string.notification_permission_text))
        if (isGranted) {
            checkAndRequestUnrestrictedBackgroundActivity(this)
            return@registerForActivityResult
        }
        commonConfirm = CommonConfirmAttrib(
            title = getString(R.string.notification_check_permission),
            message = getString(R.string.notification_permission_text),
            confirmRes = android.R.string.ok,
            cancelRes = R.string.cancel_label,
            onConfirm = { checkAndRequestUnrestrictedBackgroundActivity(this) },
            onCancel = { checkAndRequestUnrestrictedBackgroundActivity(this) })
    }

    var showUnrestrictedBackgroundPermissionDialog by mutableStateOf(false)

    private var hasFeedUpdateObserverStarted = false
    private var hasDownloadObserverStarted = false

    enum class Screens {
        Subscriptions,
        FeedDetails,
        FeedSettings,
        Facets,
        EpisodeInfo,
        EpisodeText,
        Queues,
        Search,
        OnlineSearch,
        OnlineFeed,
        OnlineEpisodes,
        Discovery,
        SearchResults,
        Logs,
        Statistics
    }

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

        lifecycleScope.launch((Dispatchers.IO)) { buildTags() }

//        if (savedInstanceState != null) ensureGeneratedViewIdGreaterThan(savedInstanceState.getInt(Extras.generated_view_id.name, 0))

//        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Logd(TAG, "handleOnBackPressed called")
                when {
                    drawerState.isOpen -> closeDrawer()
                    isBSExpanded -> isBSExpanded = false
                    mainNavController.previousBackStackEntry != null -> mainNavController.popBackStack()
                    else -> {
                        val toPage = getPref(AppPrefs.prefDefaultPage, "")
                        if (getLastNavScreen() == toPage || AppPreferences.DefaultPages.Remember.name == toPage) {
                            if (getPref(AppPrefs.prefBackButtonOpensDrawer, false)) openDrawer()
                            else {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        } else loadScreen(toPage, null)
                    }
                }
            }
        })

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(feedUpdateWorkId)
            .observe(this) { workInfos -> workInfos?.forEach { workInfo -> Logd(TAG, "FeedUpdateWork status: ${workInfo.state}") } }

        setContent { CustomTheme(this) { MainActivityUI() } }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            commonConfirm = CommonConfirmAttrib(
                title = getString(R.string.notification_check_permission),
                message = getString(R.string.notification_permission_text),
                confirmRes = android.R.string.ok,
                cancelRes = R.string.cancel_label,
                onConfirm = { requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onCancel = { checkAndRequestUnrestrictedBackgroundActivity(this@MainActivity) })
        } else checkAndRequestUnrestrictedBackgroundActivity(this)

        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
        val lastScheduledVersion = prefs.getString(Extras.lastVersion.name, "0")
        if (currentVersion != lastScheduledVersion) {
            restartUpdateAlarm(applicationContext, true)
            prefs.edit().putString(Extras.lastVersion.name, currentVersion).apply()
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
        val dynamicBottomPadding by derivedStateOf {
            when (sheetState.bottomSheetState.currentValue) {
                SheetValue.Expanded -> 300.dp
                SheetValue.PartiallyExpanded -> 110.dp
                else -> 0.dp
            }
        }
        ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
            val insets = WindowInsets.systemBars.asPaddingValues()
            val dynamicSheetHeight = insets.calculateBottomPadding()
            Logd(TAG, "effectiveBottomPadding: $dynamicSheetHeight")
            BottomSheetScaffold(scaffoldState = sheetState, sheetPeekHeight = dynamicSheetHeight + 110.dp, sheetDragHandle = {}, topBar = {},
                sheetSwipeEnabled = false, sheetShape = RectangleShape, sheetContent = { AudioPlayerScreen() }
            ) { paddingValues ->
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(
                    top = paddingValues.calculateTopPadding(),
                    start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                    end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = dynamicBottomPadding
                )) {
                    if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                    if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                    CompositionLocalProvider(LocalNavController provides navController) {
                        NavHost(navController = navController, startDestination = Screens.Subscriptions.name) {
                            composable(Screens.Subscriptions.name) { SubscriptionsScreen() }
                            composable(Screens.FeedDetails.name) { FeedDetailsScreen() }
                            composable(Screens.FeedSettings.name) { FeedSettingsScreen() }
                            composable(Screens.EpisodeInfo.name) { EpisodeInfoScreen() }
                            composable(Screens.EpisodeText.name) { EpisodeTextScreen() }
                            composable(Screens.Facets.name) { FacetsScreen() }
                            composable(Screens.Queues.name) { QueuesScreen() }
                            composable(Screens.Search.name) { SearchScreen() }
                            composable(Screens.OnlineSearch.name) { OnlineSearchScreen() }
                            composable(Screens.Discovery.name) { DiscoveryScreen() }
                            composable(Screens.OnlineFeed.name) { OnlineFeedScreen() }
                            composable(Screens.SearchResults.name) { SearchResultsScreen() }
                            composable(Screens.Logs.name) { LogsScreen() }
                            composable(Screens.Statistics.name) { StatisticsScreen() }
                            composable("DefaultPage") { SubscriptionsScreen() }
                        }
                    }
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
                    if (dontAskAgain) prefs.edit().putBoolean("dont_ask_again_unrestricted_background", true).apply()
                    val intent = Intent()
                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    this@MainActivity.startActivity(intent)
                    onDismiss()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    fun checkAndRequestUnrestrictedBackgroundActivity(context: Context) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        val dontAskAgain = prefs.getBoolean("dont_ask_again_unrestricted_background", false)
        if (!isIgnoringBatteryOptimizations && !dontAskAgain) {
            showUnrestrictedBackgroundPermissionDialog = true
//            val composeView = ComposeView(this).apply {
//                setContent { UnrestrictedBackgroundPermissionDialog(onDismiss = { (parent as? ViewGroup)?.removeView(this) }) }
//            }
//            (window.decorView as? ViewGroup)?.addView(composeView)
        }
    }

    private fun observeDownloads() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WorkManager.getInstance(this@MainActivity).pruneWork().result.get() }
            WorkManager.getInstance(this@MainActivity)
                .getWorkInfosByTagLiveData(DownloadServiceInterface.WORK_TAG)
                .observe(this@MainActivity) { workInfos: List<WorkInfo> ->
                    if (!hasDownloadObserverStarted) {
                        hasDownloadObserverStarted = true
                        return@observe
                    }
                    val updatedEpisodes: MutableMap<String, DownloadStatus> = HashMap()
                    for (workInfo in workInfos) {
                        var downloadUrl: String? = null
                        for (tag in workInfo.tags) {
                            if (tag.startsWith(DownloadServiceInterface.WORK_TAG_EPISODE_URL))
                                downloadUrl = tag.substring(DownloadServiceInterface.WORK_TAG_EPISODE_URL.length)
                        }
                        if (downloadUrl == null) continue
//                        Logd(TAG, "workInfo.state: ${workInfo.state}")
                        var status: Int
                        status = when (workInfo.state) {
                            WorkInfo.State.RUNNING -> DownloadStatus.State.RUNNING.ordinal
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.State.QUEUED.ordinal
                            WorkInfo.State.SUCCEEDED -> DownloadStatus.State.COMPLETED.ordinal
                            WorkInfo.State.FAILED -> {
                                Loge(TAG, "download failed $downloadUrl")
                                DownloadStatus.State.COMPLETED.ordinal
                            }
                            WorkInfo.State.CANCELLED -> {
                                Logt(TAG, "download cancelled $downloadUrl")
                                DownloadStatus.State.COMPLETED.ordinal
                            }
                        }
                        var progress = workInfo.progress.getInt(DownloadServiceInterface.WORK_DATA_PROGRESS, -1)
                        if (progress == -1 && status != DownloadStatus.State.COMPLETED.ordinal) {
                            status = DownloadStatus.State.QUEUED.ordinal
                            progress = 0
                        }
                        updatedEpisodes[downloadUrl] = DownloadStatus(status, progress)
                    }
                    DownloadServiceInterface.impl?.setCurrentDownloads(updatedEpisodes)
                    EventFlow.postStickyEvent(FlowEvent.EpisodeDownloadEvent(updatedEpisodes))
                }
        }
    }
    //    fun requestPostNotificationPermission() {
//        if (Build.VERSION.SDK_INT >= 33) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
//    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(Extras.generated_view_id.name, View.generateViewId())
    }

    override fun onDestroy() {
        Logd(TAG, "onDestroy")
//        WorkManager.getInstance(this).pruneWork()
//        realm.close()
//        bottomSheet.removeBottomSheetCallback(bottomSheetCallback)
//        if (drawerToggle != null) drawerLayout?.removeDrawerListener(drawerToggle!!)
//        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }

    private fun loadScreen(tag: String?, args: Bundle?) {
        var tag = tag
        var args = args
        Logd(TAG, "loadFragment(tag: $tag, args: $args)")
        when (tag) {
            Screens.Subscriptions.name, Screens.Queues.name, Screens.Logs.name, Screens.OnlineSearch.name, Screens.Facets.name, Screens.Statistics.name ->
                mainNavController.navigate(tag)
            Screens.FeedDetails.name -> {
                if (args == null) {
                    val feedId = getLastNavScreenArg().toLongOrNull()
                    if (feedId != null) {
                        val feed = getFeed(feedId)
                        if (feed != null) {
                            feedOnDisplay = feed
                            mainNavController.navigate(tag)
                        }
                    } else mainNavController.navigate(Screens.Subscriptions.name)
                } else mainNavController.navigate(Screens.Subscriptions.name)
            }
            else -> {
                tag = Screens.Subscriptions.name
                mainNavController.navigate(tag)
            }
        }
        runOnIOScope { saveLastNavScreen(tag) }
    }

//    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
//        super.onRestoreInstanceState(savedInstanceState)
////        if (bottomSheet.state == BottomSheetBehavior.STATE_EXPANDED) bottomSheetCallback.onSlide(dummyView, 1.0f)
//    }

    public override fun onStart() {
        super.onStart()
        procFlowEvents()
        RatingDialog.init(this)
        monitorFeeds(lifecycleScope)
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
//                        val snackbar = showSnackbarAbovePlayer(event.message, Snackbar.LENGTH_LONG)
//                        if (event.action != null) snackbar.setAction(event.actionText) { event.action.accept(this@MainActivity) }
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
//                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
                Logd(TAG, "handleNavIntent: feedId: $feedId")
                if (feedId > 0) {
//                    val startedFromShare = intent.getBooleanExtra(Extras.started_from_share.name, false)
//                    val addToBackStack = intent.getBooleanExtra(Extras.add_to_back_stack.name, false)
//                    Logd(TAG, "handleNavIntent: startedFromShare: $startedFromShare addToBackStack: $addToBackStack")
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
                mainNavController.navigate(Screens.SearchResults.name)
            }
//            intent.hasExtra(MainActivityStarter.Extras.fragment_tag.name) -> {
//                val tag = intent.getStringExtra(MainActivityStarter.Extras.fragment_tag.name)
//                val args = intent.getBundleExtra(MainActivityStarter.Extras.fragment_args.name)
//                if (tag != null) loadScreen(tag, args)
//                collapseBottomSheet()
////                bottomSheet.setState(BottomSheetBehavior.STATE_COLLAPSED)
//            }
            intent.getBooleanExtra(MainActivityStarter.Extras.open_player.name, false) -> {
                isBSExpanded = true
//                bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
//                bottomSheetCallback.onSlide(dummyView, 1.0f)
            }
            else -> handleDeeplink(intent.data)
        }
//        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_drawer.name, false)) drawerLayout?.open()
//        if (intent.getBooleanExtra(MainActivityStarter.Extras.open_logs.name, false)) mainNavController.navigate(Screens.Logs.name)
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

    // TODO
//    fun showSnackbarAbovePlayer(text: CharSequence, duration: Int): Snackbar {
//        val s: Snackbar
//        if (bottomSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
//            s = Snackbar.make(mainView, text, duration)
//            if (audioPlayerView.visibility == View.VISIBLE) s.anchorView = audioPlayerView
//        } else s = Snackbar.make(mainView, text, duration)
//        s.show()
//        return s
//    }

//    fun showSnackbarAbovePlayer(text: Int, duration: Int): Snackbar {
//        return showSnackbarAbovePlayer(resources.getText(text), duration)
//    }

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

    //Hardware keyboard support
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFocus = currentFocus
        if (currentFocus is EditText) return super.onKeyUp(keyCode, event)

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        var customKeyCode: Int? = null
        EventFlow.postEvent(event)

        when (keyCode) {
            KeyEvent.KEYCODE_P -> customKeyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_COMMA -> customKeyCode = KeyEvent.KEYCODE_MEDIA_REWIND
            KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_PERIOD -> customKeyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_W -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_S -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return true
            }
            KeyEvent.KEYCODE_M -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                return true
            }
        }
        if (customKeyCode != null) {
            sendBroadcast(createIntent(this, customKeyCode))
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        lastVersion,
        prefMainActivityIsFirstLaunch,
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

        lateinit var mainNavController: NavHostController
        val LocalNavController = staticCompositionLocalOf<NavController> { error("NavController not provided") }

        private val drawerState = DrawerState(initialValue = DrawerValue.Closed)
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
