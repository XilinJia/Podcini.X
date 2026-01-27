package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnceOrAsk
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.TTSEngine.closeTTS
import ac.mdiq.podcini.playback.cast.BaseActivity
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.autoBackup
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.cancelAppPrefs
import ac.mdiq.podcini.storage.database.cancelMonitorFeeds
import ac.mdiq.podcini.storage.database.initAppPrefs
import ac.mdiq.podcini.storage.database.monitorFeeds
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.cancelMonitorVolumes
import ac.mdiq.podcini.storage.model.monitorVolumes
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.LargePoster
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.ui.dialog.RatingDialog
import ac.mdiq.podcini.ui.screens.AudioPlayerScreen
import ac.mdiq.podcini.ui.screens.DrawerController
import ac.mdiq.podcini.ui.screens.LocalDrawerController
import ac.mdiq.podcini.ui.screens.LocalDrawerState
import ac.mdiq.podcini.ui.screens.NavDrawerScreen
import ac.mdiq.podcini.ui.screens.QuickAccess
import ac.mdiq.podcini.ui.screens.setOnlineSearchTerms
import ac.mdiq.podcini.ui.screens.setSearchTerms
import ac.mdiq.podcini.ui.compose.AppNavigator
import ac.mdiq.podcini.ui.compose.COME_BACK
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Navigate
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.defaultScreen
import ac.mdiq.podcini.ui.compose.handleBackSubScreens
import ac.mdiq.podcini.ui.activity.starter.MainActivityStarter
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.timeIt
import ac.mdiq.podcini.utils.toastMassege
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.StrictMode
import android.provider.Settings
import android.view.View
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : BaseActivity() {
    private var lastTheme = AppPreferences.theme
//    private var navigationBarInsets = Insets.NONE
    private var intentState by mutableStateOf<Intent?>(null)

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

    private var hasInitialized = mutableStateOf(false)

    private var initScreen by mutableStateOf<String?>(null)
    private var intendedScreen by mutableStateOf("")

    fun setIntentScreen(screen: String) {
        Logd(TAG, "setIntentScreen screen: $screen initScreen: $initScreen")
        if (initScreen == null) initScreen = screen
        else intendedScreen = screen
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        lastTheme = AppPreferences.theme

        window.requestFeature(Window.FEATURE_ACTION_MODE_OVERLAY)
        enableEdgeToEdge(window)

        if (BuildConfig.DEBUG) {
            val builder = StrictMode.ThreadPolicy.Builder()
                .detectAll()  // Enable all detections
                .penaltyLog()  // Log violations to the console
                .penaltyDropBox()
            StrictMode.setThreadPolicy(builder.build())
        }

        super.onCreate(savedInstanceState)
        handleNavIntent()

        timeIt("$TAG after handleNavIntent")
        intentState = intent

        if (savedInstanceState != null) hasInitialized.value = savedInstanceState.getBoolean(INIT_KEY, false)
        if (!hasInitialized.value) hasInitialized.value = true

        // TODO: how to monitor WorkManager
//        WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWorkLiveData(feedUpdateOnceWorkId)
//            .observe(this) { workInfos ->
//                Logd(TAG, "workInfos: ${workInfos?.size}")
//                workInfos?.forEach { workInfo -> Logd(TAG, "FeedUpdateWork status: ${workInfo.state}") }
//            }

//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { PodciniTheme { intentState?.let { currentIntent -> MainActivityUI(currentIntent) } } }

        timeIt("$TAG after setContent")

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) postFornotificationPermission()
        else checkAndRequestUnrestrictedBackgroundActivity()
        timeIt("$TAG after checking permission")

        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName
//        val lastScheduledVersion = getPref(AppPreferences.AppPrefs.lastVersion, "0")
//        if (currentVersion != lastScheduledVersion) {
//            WorkManager.getInstance(applicationContext).cancelUniqueWork(feedUpdateWorkId)
//            scheduleUpdateTaskOnce(applicationContext, true)
//            putPref(AppPreferences.AppPrefs.lastVersion, currentVersion)
//        }
//        else scheduleUpdateTaskOnce(applicationContext, false)

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
        timeIt("$TAG end of onCreate")
    }

    private var navStackJob: Job? = null
    fun monitorNavStack(navController: NavHostController) {
        fun NavBackStackEntry.resolvedRoute(): String {
            val template = destination.route ?: return ""
            var resolved = template
            arguments?.keySet()?.forEach { key -> resolved = resolved.replace("{$key}", arguments?.get(key)?.toString() ?: "") }
            return resolved
        }
        if (navStackJob == null) navStackJob = CoroutineScope(Dispatchers.Default).launch {
            navController.currentBackStackEntryFlow.collect { entry ->
                val resolved = entry.resolvedRoute()
                runOnIOScope { upsertBlk(appAttribs) { it.prefLastScreen = resolved } }
                Logd(TAG, "currentBackStackEntryFlow Now at: $resolved")
            }
        }
    }

    
    @Composable
    fun MainActivityUI(intent: Intent) {
        val lcScope = rememberCoroutineScope()
        val navController = rememberNavController()
        val navigator = remember { AppNavigator(navController) { route -> Logd(TAG, "Navigated to: $route") } }
        LaunchedEffect(Unit) { monitorNavStack(navController) }

        val sheetState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false))

        if (showUnrestrictedBackgroundPermissionDialog) UnrestrictedBackgroundPermissionDialog { showUnrestrictedBackgroundPermissionDialog = false }

        var firstRun by remember { mutableStateOf(true) }
        LaunchedEffect(key1 = bsState, key2 = curEpisode?.id, firstRun) {
            Logd(TAG, "LaunchedEffect(key1 = bsState, key2 = curEpisode?.id, firstRun)")
            if (firstRun) {
                firstRun = false
                return@LaunchedEffect
            }
            if ((curEpisode?.id ?: -1L) > 0) {
                when (bsState) {
                    BSState.Expanded -> sheetState.bottomSheetState.expand()
                    BSState.Partial -> sheetState.bottomSheetState.partialExpand()
                    else -> sheetState.bottomSheetState.hide()
                }
            } else sheetState.bottomSheetState.hide()
        }

        val sheetValueState = remember { mutableStateOf(sheetState.bottomSheetState.currentValue) }
        LaunchedEffect(sheetState.bottomSheetState) {
            snapshotFlow { sheetState.bottomSheetState.currentValue }.collect { newValue -> sheetValueState.value = newValue }
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
        var savedDrawerValue by rememberSaveable { mutableStateOf(DrawerValue.Closed) }

        val drawerState = rememberDrawerState(initialValue = savedDrawerValue)

        val configuration = LocalConfiguration.current
        LaunchedEffect(configuration.orientation) {
            withFrameNanos { }
            drawerState.snapTo(savedDrawerValue)
        }

        LaunchedEffect(drawerState.currentValue) { savedDrawerValue = drawerState.currentValue }
        val drawerCtrl = remember {
            object : DrawerController {
                override fun isOpen() = drawerState.isOpen
                override fun open() {
                    lcScope.launch { drawerState.open() }
                }
                override fun close() {
                    lcScope.launch { drawerState.close() }
                }
                override fun toggle() {
                    lcScope.launch {
                        if (drawerState.isOpen) drawerState.close()
                        else drawerState.open()
                    }
                }
            }
        }

        CompositionLocalProvider(LocalDrawerController provides drawerCtrl, LocalDrawerState provides drawerState) {
            //        Logd(TAG, "dynamicBottomPadding: $dynamicBottomPadding sheetValue: ${sheetValueState.value}")
            ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen(navigator) }) {
                BottomSheetScaffold(sheetContent = { AudioPlayerScreen(navigator) }, scaffoldState = sheetState, sheetPeekHeight = bottomInsetPadding + 100.dp, sheetDragHandle = {}, sheetSwipeEnabled = false, sheetShape = RectangleShape, topBar = {}) { paddingValues ->
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(top = paddingValues.calculateTopPadding(), start = paddingValues.calculateStartPadding(LocalLayoutDirection.current), end = paddingValues.calculateEndPadding(LocalLayoutDirection.current), bottom = dynamicBottomPadding)) {
                        if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                        if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                        if (commonMessage != null) LargePoster(commonMessage!!)
                        CompositionLocalProvider(LocalNavController provides navigator) {
                            Navigate(navController, initScreen ?: "")
                        }
                    }
                }
                LaunchedEffect(intendedScreen) {
                    Logd(TAG, "LaunchedEffect intendedScreen: $intendedScreen")
                    if (intendedScreen.isNotBlank()) {
                        navigator.navigate(intendedScreen) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                        intendedScreen = ""
                    }
                }
            }
        }

        BackHandler(enabled = handleBackSubScreens.isEmpty()) {
            fun haveCommonPrefix(a: String, b: String): Boolean {
                val min = minOf(a.length, b.length)
                var count = 0
                for (i in 0 until min) {
                    if (a[i] != b[i]) break
                    count++
                }
                return count > 0
            }
            Logd(TAG, "BackHandler isBSExpanded: $bsState")
            val openDrawer = getPref(AppPrefs.prefBackButtonOpensDrawer, false)
            val defPage = defaultScreen
            val currentDestination = navigator.currentDestination
            var curruntRoute = currentDestination?.route ?: ""
            Logd(TAG, "BackHandler curruntRoute0: $curruntRoute defPage: $defPage")
            when {
                drawerState.isOpen -> drawerCtrl.close()
                bsState == BSState.Expanded -> bsState = BSState.Partial
                navigator.previousBackStackEntry != null -> {
                    Logd(TAG, "nav to back")
                    navigator.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                    navigator.popBackStack()
                    curruntRoute = currentDestination?.route ?: ""
                    Logd(TAG, "BackHandler curruntRoute: [$curruntRoute]")
                }
                !isRemember && defPage.isNotBlank() && curruntRoute.isNotBlank() && !haveCommonPrefix(curruntRoute, defPage) -> {
                    Logd(TAG, "nav to defPage: $defPage")
                    navigator.navigate(defPage) { popUpTo(0) { inclusive = true } }
                    curruntRoute = currentDestination?.route ?: ""
                    Logd(TAG, "BackHandler curruntRoute1: [$curruntRoute]")
                }
                openDrawer -> drawerCtrl.open()
                else -> Logt(TAG, getString(R.string.no_more_screens_back))
            }
        }
    }

    @Composable
    fun UnrestrictedBackgroundPermissionDialog(onDismiss: () -> Unit) {
        var dontAskAgain by remember { mutableStateOf(false) }
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = onDismiss, title = { Text("Permission Required") },
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
                    if (dontAskAgain) putPref(AppPrefs.dont_ask_again_unrestricted_background, true)
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = "package:$packageName".toUri() }
//                    val intent = Intent()
//                    intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
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
        val dontAskAgain = getPref(AppPrefs.dont_ask_again_unrestricted_background, false)
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
                        Logd(TAG, "workInfo.state: ${workInfo.state} isFinished: ${workInfo.state.isFinished}")
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
                        Logd(TAG, "downloadStates[$downloadUrl]: ${downloadStates[downloadUrl]?.state}")
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
        closeTTS()
//        cancelQueuesJob()
        cancelAppPrefs()
        navStackJob?.cancel()
        navStackJob = null
        super.onDestroy()
    }

    public override fun onStart() {
        super.onStart()
        initAppPrefs()
        procFlowEvents()
        RatingDialog.init(this)
        monitorFeeds(lifecycleScope)
        monitorVolumes(lifecycleScope)
        timeIt("$TAG end of onStart")
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
        cancelMonitorFeeds()
        cancelMonitorVolumes()
    }

    override fun onResume() {
        super.onResume()
        autoBackup(this)
        RatingDialog.check()
        if (lastTheme != AppPreferences.theme) {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }
        timeIt("$TAG end of onStart")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        lastTheme = getNoTitleTheme(this) // Don't recreate activity when a result is pending
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
        when {
            intent.hasExtra(Extras.feed_id.name) -> {
                val feedId = intent.getLongExtra(Extras.feed_id.name, 0)
                Logd(TAG, "handleNavIntent: feedId: $feedId")
                if (feedId > 0) setIntentScreen("${Screens.FeedDetails.name}?feedId=${feedId}")
                bsState = BSState.Partial
            }
            intent.hasExtra(Extras.queue_id.name) -> {
                val queueId = intent.getLongExtra(Extras.queue_id.name, 0)
                Logd(TAG, "handleNavIntent: queueId: $queueId")
                if (queueId >= 0) setIntentScreen("${Screens.Queues.name}?index=${queueId}")
                bsState = BSState.Partial
            }
            intent.hasExtra(Extras.facet_name.name) -> {
                val facetName = intent.getStringExtra(Extras.facet_name.name)
                Logd(TAG, "handleNavIntent: facetName: $facetName")
                if (!facetName.isNullOrEmpty()) QuickAccess.entries.find { it.name == facetName }?.let { setIntentScreen("${Screens.Facets.name}?modeName=${it.name}") }
                bsState = BSState.Partial
            }
            intent.hasExtra(Extras.fragment_feed_url.name) -> {
                val feedurl = intent.getStringExtra(Extras.fragment_feed_url.name)
                val isShared = intent.getBooleanExtra(Extras.isShared.name, false)
                if (feedurl != null) setIntentScreen("${Screens.OnlineFeed.name}?url=${URLEncoder.encode(feedurl, StandardCharsets.UTF_8.name())}&shared=${isShared}")
            }
            intent.hasExtra(Extras.search_string.name) -> {
                setOnlineSearchTerms(query = intent.getStringExtra(Extras.search_string.name))
                setIntentScreen(Screens.FindFeeds.name)
            }
            intent.getBooleanExtra(MainActivityStarter.Extras.open_player.name, false) -> bsState = BSState.Expanded
            intent.hasExtra("shortcut_route") -> {
                val route = intent.getStringExtra("shortcut_route")
                Logd(TAG, "intent.hasExtra(shortcut_route) route $route")
                val screen = when (route) {
                    "Queues" -> Screens.Queues.name
                    "Facets" -> Screens.Facets.name
                    "library" -> Screens.Library.name
                    "FindFeeds" -> Screens.FindFeeds.name
                    "Statistics" -> Screens.Statistics.name
                    else -> Screens.Library.name
                }
                setIntentScreen(screen)
            }
            else -> {
                // deeplink
                val uri = intent.data
                if (uri?.path == null) return
                Logd(TAG, "Handling deeplink: $uri")
                when (uri.path) {
                    "/deeplink/search" -> {
                        val query = uri.getQueryParameter("query") ?: return
                        setSearchTerms(query)
                        setIntentScreen(Screens.Search.name)
                    }
                    "/deeplink/main" -> {
                        val feature = uri.getQueryParameter("page") ?: return
                        when (feature) {
                            "FACETS" -> setIntentScreen(Screens.Facets.name)
                            "QUEUES" -> setIntentScreen(Screens.Queues.name)
                            "LIBRARY" -> setIntentScreen(Screens.Library.name)
                            "STATISTCS" -> setIntentScreen(Screens.Statistics.name)
                            else -> Logt(TAG, getString(R.string.app_action_not_found) + feature)
                        }
                    }
                    else -> {}
                }
            }
        }
        if (intent.getBooleanExtra(Extras.refresh_on_start.name, false)) runOnceOrAsk()

        // to avoid handling the intent twice when the configuration changes    TODO: this is not a good way
//        setIntent(Intent(this@MainActivity, MainActivity::class.java))
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent()
    }

    @Suppress("EnumEntryName")
    enum class Extras {
        lastVersion,
        queue_id,
        facet_name,
        feed_id,
        fragment_feed_url,
        refresh_on_start,
        generated_view_id,
        search_string,
        isShared
    }

    enum class BSState {
        Hidden, Partial, Expanded
    }

    companion object {
        private val TAG: String = MainActivity::class.simpleName ?: "Anonymous"  // have to keep, otherwise release build may fail?!

        private const val INIT_KEY = "app_init_state"

        val downloadStates = mutableStateMapOf<String, DownloadStatus>()
        var bsState by mutableStateOf(BSState.Partial)

        val isRemember: Boolean
            get() = getPref(AppPrefs.prefDefaultPage, "") == AppPreferences.DefaultPages.Remember.name

        fun getIntentToOpenFeed(feedId: Long): Intent {
            val intent = Intent(getAppContext(), MainActivity::class.java)
            intent.putExtra(Extras.feed_id.name, feedId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        fun showOnlineFeed(feedUrl: String, isShared: Boolean = false): Intent {
            val intent = Intent(getAppContext(), MainActivity::class.java)
            intent.putExtra(Extras.fragment_feed_url.name, feedUrl)
            intent.putExtra(Extras.isShared.name, isShared)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }

        fun showOnlineSearch(query: String): Intent {
            val intent = Intent(getAppContext(), MainActivity::class.java)
            intent.putExtra(Extras.search_string.name, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }
}
