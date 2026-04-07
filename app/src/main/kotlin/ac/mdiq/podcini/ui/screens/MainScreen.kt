package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.LargePoster
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.toastMassege
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityUI() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context by rememberUpdatedState(LocalContext.current)
    val lcScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    if (appAttribs.restoreLastScreen) {
                        val restored: List<NavKey> = Json.decodeFromString(appAttribs.backstack)
                        if (restored.isNotEmpty()) backStack.addAll(restored.take(10))
                    }
                }
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {}
    }

    val sheetState = rememberBottomSheetScaffoldState(bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false))

    LaunchedEffect(sheetState.bottomSheetState) { snapshotFlow { sheetState.bottomSheetState.currentValue }.collect { state -> psState = PSState.fromSheet(state) } }

    var firstRun by remember { mutableStateOf(true) }
    LaunchedEffect(key1 = psState, key2 = curEpisode?.id, firstRun) {
        Logd(TAG, "LaunchedEffect(key1 = bsState, key2 = curEpisode?.id, firstRun)")
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        if ((curEpisode?.id ?: -1L) > 0) {
            when (psState) {
                PSState.Expanded -> sheetState.bottomSheetState.expand()
                PSState.PartiallyExpanded -> sheetState.bottomSheetState.partialExpand()
                else -> sheetState.bottomSheetState.hide()
            }
        } else sheetState.bottomSheetState.hide()
        //            if ((curEpisode?.id ?: -1L) <= 0) sheetState.bottomSheetState.hide()
    }

    val bottomInsets = WindowInsets.ime.union(WindowInsets.navigationBars)
    val bottomInsetPadding = bottomInsets.asPaddingValues().calculateBottomPadding()
    val dynamicBottomPadding by remember {
        derivedStateOf {
            when (sheetState.bottomSheetState.currentValue) {
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
        Logd(TAG, "LaunchedEffect(configuration.orientation)")
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

    if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
    if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
    if (commonMessage != null) LargePoster(commonMessage!!)

    var lastLogTime by remember { mutableLongStateOf(0L) }
    if (appPrefs.customFolderUnavailable) {
        val currentTime = nowInMillis()
        if (currentTime - lastLogTime > 60000L) {
            Loge(TAG, stringResource(R.string.custum_folder_warning))
            lastLogTime = currentTime
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { backStack.toList() }.debounce(200).collect { stack ->
            val json = Json.encodeToString(stack)
            withContext(Dispatchers.IO) { upsert(appAttribs) { it.backstack = json } }
        }
    }

    val windowInfo = LocalWindowInfo.current
    val screenWidth = windowInfo.containerSize.width.dp
//    Logd(TAG, "before CompositionLocalProvider")
    CompositionLocalProvider(LocalDrawerController provides drawerCtrl, LocalDrawerState provides drawerState) {
        ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
            BottomSheetScaffold(sheetContent = { AVPlayerScreen() }, scaffoldState = sheetState, sheetMaxWidth = screenWidth, sheetPeekHeight = bottomInsetPadding + 100.dp, sheetDragHandle = {}, sheetShape = RectangleShape, topBar = {}) { paddingValues ->
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(top = paddingValues.calculateTopPadding(), bottom = dynamicBottomPadding)) {
                    NavDisplay(backStack = backStack, onBack = { navBack() }, entryProvider = anyEntryProvider)
                }
            }
        }
    }

    BackHandler(enabled = handleBackSubScreens.isEmpty()) {
        Logd(TAG, "BackHandler isBSExpanded: $psState")
        val openDrawer = appPrefs.backButtonOpensDrawer
        val defPage = defaultNavKey
        Logd(TAG, "BackHandler curruntRoute0: defPage: $defPage")
        when {
            drawerState.isOpen -> drawerCtrl.close()
            psState == PSState.Expanded -> psState = PSState.PartiallyExpanded
            backStack.size > 1 -> {
                Logd(TAG, "nav to back")
                navBack()
                Logd(TAG, "BackHandler curruntRoute: ")
            }
            backStack.size == 1 && defPage != backStack[0] -> {
                Logd(TAG, "nav to defPage: $defPage")
                navTo(defPage)
                Logd(TAG, "BackHandler curruntRoute1: ")
            }
            openDrawer -> drawerCtrl.open()
            else -> Logt(TAG, context.getString(R.string.no_more_screens_back))
        }
    }
}
