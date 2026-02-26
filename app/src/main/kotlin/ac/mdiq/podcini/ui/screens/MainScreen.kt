package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.LargePoster
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.Logd
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainScreen"

private var initScreen by mutableStateOf<String?>(null)
private var intendedScreen by mutableStateOf("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityUI() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context by rememberUpdatedState(LocalContext.current)
    val lcScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navigator = remember { AppNavigator(navController) { route ->
        Logd(TAG, "Navigated to: $route")
        if (psState == PSState.Expanded) psState =  PSState.PartiallyExpanded
    } }

     var navStackJob: Job? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "DisposableEffect LifecycleEventObserver: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            navStackJob?.cancel()
            navStackJob = null
        }
    }

    LaunchedEffect(navController) {
        fun NavBackStackEntry.resolvedRoute(): String {
            val template = destination.route ?: return ""
            var resolved = template
            arguments?.keySet()?.forEach { key -> resolved = resolved.replace("{$key}", arguments?.get(key)?.toString() ?: "") }
            return resolved
        }
        navController.currentBackStackEntryFlow.map { it.resolvedRoute() }.distinctUntilChanged().collect { resolved ->
            withContext(Dispatchers.IO) { upsert(appAttribs) { it.prefLastScreen = resolved } }
            Logd(TAG, "currentBackStackEntryFlow Now at: $resolved")
        }
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

    //        val sheetValueState = remember { mutableStateOf(sheetState.bottomSheetState.currentValue) }
    //        LaunchedEffect(Unit) {
    //            Logd(TAG, "LaunchedEffect(sheetState.bottomSheetState)")
    //            snapshotFlow { sheetState.bottomSheetState.currentValue }.distinctUntilChanged().collect { newValue ->
    //                Logd(TAG, "sheetState.bottomSheetState.currentValue collect")
    //                sheetValueState.value = newValue
    //            }
    //        }
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

    //    val screenWidth = configuration.screenWidthDp.dp
    val windowInfo = LocalWindowInfo.current
    val screenWidth = windowInfo.containerSize.width.dp
    Logd(TAG, "before CompositionLocalProvider")
    CompositionLocalProvider(LocalDrawerController provides drawerCtrl, LocalDrawerState provides drawerState, LocalNavController provides navigator) {
        //            Logd(TAG, "dynamicBottomPadding: $dynamicBottomPadding sheetValue: ${sheetValueState.value}")
        ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
            BottomSheetScaffold(sheetContent = { AVPlayerScreen() }, scaffoldState = sheetState, sheetMaxWidth = screenWidth, sheetPeekHeight = bottomInsetPadding + 100.dp, sheetDragHandle = {}, sheetShape = RectangleShape, topBar = {}) { paddingValues ->
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(top = paddingValues.calculateTopPadding(), bottom = dynamicBottomPadding)) {
                    Navigate(navController, initScreen ?: "")
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

    //        CompositionLocalProvider(LocalDrawerController provides drawerCtrl, LocalDrawerState provides drawerState, LocalNavController provides navigator) {
    //            ModalNavigationDrawer(drawerState = drawerState, modifier = Modifier.fillMaxHeight(), drawerContent = { NavDrawerScreen() }) {
    //                Scaffold(bottomBar = { AudioPlayerUIScreen(modifier = Modifier.padding(bottom = bottomInsetPadding), navigator) }) { innerPadding ->
    //                    val playerHeightOnly = (innerPadding.calculateBottomPadding() - bottomInsetPadding).coerceAtLeast(0.dp)
    //                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().padding(bottom = playerHeightOnly)) {
    //                        if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
    //                        if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
    //                        if (commonMessage != null) LargePoster(commonMessage!!)
    //                        Navigate(navController, initScreen ?: "")
    //                    }
    //                }
    //                LaunchedEffect(intendedScreen) {
    //                    Logd(TAG, "LaunchedEffect intendedScreen: $intendedScreen")
    //                    if (intendedScreen.isNotBlank()) {
    //                        navigator.navigate(intendedScreen) {
    //                            popUpTo(0) { inclusive = true }
    //                            launchSingleTop = true
    //                        }
    //                        intendedScreen = ""
    //                    }
    //                }
    //            }
    //        }

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
        Logd(TAG, "BackHandler isBSExpanded: $psState")
        val openDrawer = appPrefs.backButtonOpensDrawer
        val defPage = defaultScreen
        val currentDestination = navigator.currentDestination
        var curruntRoute = currentDestination?.route ?: ""
        Logd(TAG, "BackHandler curruntRoute0: $curruntRoute defPage: $defPage")
        when {
            drawerState.isOpen -> drawerCtrl.close()
            psState == PSState.Expanded -> psState = PSState.PartiallyExpanded
            navigator.previousBackStackEntry != null -> {
                Logd(TAG, "nav to back")
                navigator.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                navigator.popBackStack()
                curruntRoute = currentDestination?.route ?: ""
                Logd(TAG, "BackHandler curruntRoute: [$curruntRoute]")
            }
            appPrefs.defaultPage != DefaultPages.Remember.name && defPage.isNotBlank() && curruntRoute.isNotBlank() && !haveCommonPrefix(curruntRoute, defPage) -> {
                Logd(TAG, "nav to defPage: $defPage")
                navigator.navigate(defPage) { popUpTo(0) { inclusive = true } }
                curruntRoute = currentDestination?.route ?: ""
                Logd(TAG, "BackHandler curruntRoute1: [$curruntRoute]")
            }
            openDrawer -> drawerCtrl.open()
            else -> Logt(TAG, context.getString(R.string.no_more_screens_back))
        }
    }
}

fun setIntentScreen(screen: String) {
    Logd(TAG, "setIntentScreen screen: $screen initScreen: $initScreen")
    if (initScreen == null) initScreen = screen
    else intendedScreen = screen
}
