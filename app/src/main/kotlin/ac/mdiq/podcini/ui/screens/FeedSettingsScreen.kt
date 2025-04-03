package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.VideoModeDialog
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FeedSettingsVM(val context: Context, val lcScope: CoroutineScope) {
    internal var feed by mutableStateOf<Feed?>(feedOnDisplay)

    internal var autoUpdate by mutableStateOf(feed?.keepUpdated == true)

    internal var autoDeleteSummaryResId by mutableIntStateOf(R.string.global_default)
    internal var curPrefQueue by mutableStateOf(feed?.queueTextExt ?: "Default")
    internal var autoDeletePolicy = AutoDeleteAction.GLOBAL.name
    internal var videoModeSummaryResId by mutableIntStateOf(R.string.global_default)
    internal var videoMode = VideoMode.NONE.name
    internal var queues: List<PlayQueue>? = null

    internal var notificationPermissionDenied: Boolean = false

    private var eventSink: Job? = null
    internal fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    internal fun procFlowEvents() {
        if (eventSink == null) eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd(TAG, "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.FeedChangeEvent -> if (feed?.id == event.feed.id) {
                        feed = event.feed
                        feedOnDisplay = event.feed
                    }
                    else -> {}
                }
            }
        }
    }

//    init {
//        feed = feedOnDisplay
//    }
}

//private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
//    if (isGranted) return@registerForActivityResult
//    if (vm.notificationPermissionDenied) {
//        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
//        val uri = Uri.fromParts("package", requireContext().packageName, null)
//        intent.setData(uri)
//        startActivity(intent)
//        return@registerForActivityResult
//    }
//    Toast.makeText(context, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
//    vm.notificationPermissionDenied = true
//}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedSettingsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember(feedOnDisplay.id) { FeedSettingsVM(context, scope) }

    fun getVideoModePolicy() {
        when (vm.feed?.videoModePolicy) {
            VideoMode.NONE -> {
                vm.videoModeSummaryResId = R.string.global_default
                vm.videoMode = VideoMode.NONE.tag
            }
            VideoMode.WINDOW_VIEW -> {
                vm.videoModeSummaryResId = R.string.feed_video_mode_window
                vm.videoMode = VideoMode.WINDOW_VIEW.tag
            }
            VideoMode.FULL_SCREEN_VIEW -> {
                vm.videoModeSummaryResId = R.string.feed_video_mode_fullscreen
                vm.videoMode = VideoMode.FULL_SCREEN_VIEW.tag
            }
            VideoMode.AUDIO_ONLY -> {
                vm.videoModeSummaryResId = R.string.feed_video_mode_audioonly
                vm.videoMode = VideoMode.AUDIO_ONLY.tag
            }
            else -> {}
        }
    }
    fun getAutoDeletePolicy() {
        when (vm.feed?.autoDeleteAction) {
            AutoDeleteAction.GLOBAL -> {
                vm.autoDeleteSummaryResId = R.string.global_default
                vm.autoDeletePolicy = AutoDeleteAction.GLOBAL.tag
            }
            AutoDeleteAction.ALWAYS -> {
                vm.autoDeleteSummaryResId = R.string.feed_auto_download_always
                vm.autoDeletePolicy = AutoDeleteAction.ALWAYS.tag
            }
            AutoDeleteAction.NEVER -> {
                vm.autoDeleteSummaryResId = R.string.feed_auto_download_never
                vm.autoDeletePolicy = AutoDeleteAction.NEVER.tag
            }
            else -> {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    getVideoModePolicy()
                    getAutoDeletePolicy()
                }
                Lifecycle.Event.ON_START -> vm.procFlowEvents()
                Lifecycle.Event.ON_STOP -> vm.cancelFlowEvents()
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.feed = null
            vm.queues = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
     fun MyTopAppBar() {
        TopAppBar( title = {
            Column {
                Text(text = stringResource(R.string.feed_settings_label), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (!vm.feed?.title.isNullOrBlank()) Text(text = vm.feed!!.title!!, fontSize = 16.sp)
            }
        },
            navigationIcon = { IconButton(onClick = {
                if (mainNavController.previousBackStackEntry != null) mainNavController.popBackStack()
//            else onBackPressed()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
        )
    }

    @Composable
     fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(vm.autoDeletePolicy) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedAutoDeleteOptions.forEach { text ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (text == selectedOption),
                                onCheckedChange = {
                                    Logd(TAG, "row clicked: $text $selectedOption")
                                    if (text != selectedOption) {
                                        onOptionSelected(text)
                                        val action_ = when (text) {
                                            AutoDeleteAction.GLOBAL.tag -> AutoDeleteAction.GLOBAL
                                            AutoDeleteAction.ALWAYS.tag -> AutoDeleteAction.ALWAYS
                                            AutoDeleteAction.NEVER.tag -> AutoDeleteAction.NEVER
                                            else -> AutoDeleteAction.GLOBAL
                                        }
                                        upsertBlk(vm.feed!!) { it.autoDeleteAction = action_ }
                                        getAutoDeletePolicy()
                                        onDismissRequest()
                                    }
                                }
                            )
                            Text(text = text, style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
     fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
        val (selectedOption, onOptionSelected) = remember { mutableStateOf(vm.feed?.volumeAdaptionSetting ?: VolumeAdaptionSetting.OFF) }
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VolumeAdaptionSetting.entries.forEach { item ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = (item == selectedOption),
                                onCheckedChange = { _ ->
                                    Logd(TAG, "row clicked: $item $selectedOption")
                                    if (item != selectedOption) {
                                        onOptionSelected(item)
                                        upsertBlk(vm.feed!!) { it.volumeAdaptionSetting = item }
                                        onDismissRequest()
                                    }
                                }
                            )
                            Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
     fun SetAudioType(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feed.AudioType.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AudioType.fromTag(selected)
                                    upsertBlk(vm.feed!!) { it.audioType = type.code }
                                    onDismissRequest()
                                }
                            )
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
     fun SetAudioQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feed.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AVQuality.fromTag(selected)
                                    upsertBlk(vm.feed!!) { it.audioQuality = type.code }
                                    onDismissRequest()
                                })
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SetVideoQuality(selectedOption: String, onDismissRequest: () -> Unit) {
        var selected by remember {mutableStateOf(selectedOption)}
        Dialog(onDismissRequest = { onDismissRequest() }) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Feed.AVQuality.entries.forEach { option ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = option.tag == selected,
                                onCheckedChange = { isChecked ->
                                    selected = option.tag
                                    if (isChecked) Logd(TAG, "$option is checked")
                                    val type = Feed.AVQuality.fromTag(selected)
                                    upsertBlk(vm.feed!!) { it.videoQuality = type.code }
                                    onDismissRequest()
                                })
                            Text(option.tag)
                        }
                    }
                }
            }
        }
    }

    @Composable
     fun AuthenticationDialog(onDismiss: () -> Unit) {
        val context = LocalContext.current
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val oldName = vm.feed?.username?:""
                    var newName by remember { mutableStateOf(oldName) }
                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                    val oldPW = vm.feed?.password?:""
                    var newPW by remember { mutableStateOf(oldPW) }
                    TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                    Button(onClick = {
                        if (newName.isNotEmpty() && oldName != newName) {
                            runOnIOScope {
                                upsert(vm.feed!!) {
                                    it.username = newName
                                    it.password = newPW
                                }
                                runOnce(context, vm.feed)
                            }
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }

    @Composable
     fun AutoSkipDialog(onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var intro by remember { mutableStateOf((vm.feed?.introSkip ?: 0).toString()) }
                    TextField(value = intro, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) intro = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip first (seconds)") })
                    var ending by remember { mutableStateOf((vm.feed?.endingSkip ?: 0).toString()) }
                    TextField(value = ending, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) ending = it  },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("Skip last (seconds)") })
                    Button(onClick = {
                        if (intro.isNotEmpty() || ending.isNotEmpty()) {
                            upsertBlk(vm.feed!!) {
                                it.introSkip = intro.toIntOrNull() ?: 0
                                it.endingSkip = ending.toIntOrNull() ?: 0
                            }
                            onDismiss()
                        }
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 16.dp).verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.rounded_responsive_layout_24), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.use_wide_layout), style = CustomTextStyles.titleCustom, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    var checked by remember { mutableStateOf(vm.feed?.useWideLayout == true) }
                    Switch(checked = checked, modifier = Modifier.height(24.dp),
                        onCheckedChange = {
                            checked = it
                            upsertBlk(vm.feed!!) { f -> f.useWideLayout = checked }
                        }
                    )
                }
                Text(text = stringResource(R.string.use_wide_layout_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            Column {
                var showDialog by remember { mutableStateOf(false) }
                var selectedOption by remember { mutableStateOf(vm.feed?.audioTypeSetting?.tag ?: Feed.AudioType.SPEECH.tag) }
                if (showDialog) SetAudioType(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_audio_type), style = CustomTextStyles.titleCustom, color = textColor,
                        modifier = Modifier.clickable(onClick = {
                            selectedOption = vm.feed!!.audioTypeSetting.tag
                            showDialog = true
                        }))
                    Spacer(modifier = Modifier.width(30.dp))
                    Text(selectedOption, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if ((vm.feed?.id ?: 0) >= MAX_NATURAL_SYNTHETIC_ID && vm.feed?.hasVideoMedia == true) {
                //                    video mode
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        var showDialog by remember { mutableStateOf(false) }
                        if (showDialog) VideoModeDialog(initMode = vm.feed?.videoModePolicy, onDismissRequest = { showDialog = false }) { mode ->
                            upsertBlk(vm.feed!!) { it.videoModePolicy = mode }
                            getVideoModePolicy()
                        }
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.feed_video_mode_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(text = stringResource(vm.videoModeSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
            if (vm.feed?.type == Feed.FeedType.YOUTUBE.name) {
                //                    audio quality
                Column {
                    var showDialog by remember { mutableStateOf(false) }
                    var selectedOption by remember { mutableStateOf(vm.feed?.audioQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }
                    if (showDialog) SetAudioQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_audio_quality), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = {
                                selectedOption = vm.feed!!.audioQualitySetting.tag
                                showDialog = true
                            }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(selectedOption, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if (vm.feed?.videoModePolicy != VideoMode.AUDIO_ONLY) {
                    //                    video quality
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        var selectedOption by remember { mutableStateOf(vm.feed?.videoQualitySetting?.tag ?: Feed.AVQuality.GLOBAL.tag) }
                        if (showDialog) SetVideoQuality(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_video_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                modifier = Modifier.clickable(onClick = {
                                    selectedOption = vm.feed!!.videoQualitySetting.tag
                                    showDialog = true
                                }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(selectedOption, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_feed_video_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
            @Composable
            fun SetAssociatedQueue(selectedOption: String, onDismissRequest: () -> Unit) {
                var selected by remember {mutableStateOf(selectedOption)}
                Dialog(onDismissRequest = { onDismissRequest() }) {
                    Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            queueSettingOptions.forEach { option ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = option == selected,
                                        onCheckedChange = { isChecked ->
                                            selected = option
                                            if (isChecked) Logd(TAG, "$option is checked")
                                            when (selected) {
                                                "Default" -> {
                                                    upsertBlk(vm.feed!!) { it.queueId = 0L }
                                                    vm.curPrefQueue = selected
                                                    onDismissRequest()
                                                }
                                                "Active" -> {
                                                    upsertBlk(vm.feed!!) { it.queueId = -1L }
                                                    vm.curPrefQueue = selected
                                                    onDismissRequest()
                                                }
                                                "None" -> {
                                                    upsertBlk(vm.feed!!) {
                                                        it.queueId = -2L
                                                        it.autoEnqueue = false
                                                    }
                                                    vm.curPrefQueue = selected
                                                    onDismissRequest()
                                                }
                                                "Custom" -> {}
                                            }
                                        }
                                    )
                                    Text(option)
                                }
                            }
                            if (selected == "Custom") {
                                if (vm.queues == null) vm.queues = realm.query(PlayQueue::class).find()
                                Logd(TAG, "queues: ${vm.queues?.size}")
                                Spinner(items = vm.queues!!.map { it.name }, selectedItem = vm.feed?.queue?.name ?: "Default") { index ->
                                    Logd(TAG, "Queue selected: ${vm.queues!![index].name}")
                                    val q = vm.queues!![index]
                                    upsertBlk(vm.feed!!) { it.queue = q }
                                    vm.curPrefQueue = q.name
                                    onDismissRequest()
                                }
                            }
                        }
                    }
                }
            }
            //                    associated queue
            Column {
                vm.curPrefQueue = vm.feed?.queueTextExt ?: "Default"
                var showDialog by remember { mutableStateOf(false) }
                var selectedOption by remember { mutableStateOf(vm.feed?.queueText ?: "Default") }
                if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_associated_queue), style = CustomTextStyles.titleCustom, color = textColor,
                        modifier = Modifier.clickable(onClick = {
                            selectedOption = vm.feed?.queueText ?: "Default"
                            showDialog = true
                        })
                    )
                }
                Text(text = vm.curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    tags
            Column {
                var showDialog by remember { mutableStateOf(false) }
                if (showDialog) TagSettingDialog(feeds_ = listOf(vm.feed!!), onDismiss = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.feed_tags_label), style = CustomTextStyles.titleCustom, color = textColor,
                        modifier = Modifier.clickable(onClick = { showDialog = true }))
                }
                Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    playback speed
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value) PlaybackSpeedDialog(listOf(vm.feed!!), initSpeed = vm.feed!!.playSpeed, maxSpeed = 3f,
                        onDismiss = { showDialog.value = false }) { newSpeed ->
                        upsertBlk(vm.feed!!) { it.playSpeed = newSpeed }
                    }
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.playback_speed), style = CustomTextStyles.titleCustom, color = textColor,
                        modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    volume adaption
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value) VolumeAdaptionDialog(onDismissRequest = { showDialog.value = false })
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.feed_volume_adapdation), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    authentication
            if ((vm.feed?.id ?: 0) > 0 && vm.feed?.isLocalFeed != true) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) AuthenticationDialog(onDismiss = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.authentication_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.authentication_descr), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            var autoDownloadChecked by remember { mutableStateOf(vm.feed?.autoDownload == true) }
            var preferStreaming by remember { mutableStateOf(vm.feed?.prefStreamOverDownload == true) }
            if (vm.feed?.type != Feed.FeedType.YOUTUBE.name) {
                //                    prefer streaming
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_stream), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_stream_over_download_title), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = preferStreaming, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                preferStreaming = it
                                if (preferStreaming) {
                                    prefStreamOverDownload = true
                                    autoDownloadChecked = false
                                }
                                upsertBlk(vm.feed!!) { f ->
                                    f.prefStreamOverDownload = preferStreaming
                                    if (preferStreaming) f.autoDownload = false
                                }
                            }
                        )
                    }
                    Text(text = stringResource(R.string.pref_stream_over_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            //                    auto skip
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value) AutoSkipDialog(onDismiss = { showDialog.value = false })
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_skip_24dp), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_skip), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.pref_feed_skip_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if (vm.feed?.type != Feed.FeedType.YOUTUBE.name) {
                //                    auto delete
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) AutoDeleteDialog(onDismissRequest = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.auto_delete_episode), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.auto_delete_sum) + ": " + stringResource(vm.autoDeleteSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            if ((vm.feed?.id ?: 0) > MAX_SYNTHETIC_ID) {
                //                    refresh
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.keep_updated), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(checked = vm.autoUpdate, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                vm.autoUpdate = it
                                upsertBlk(vm.feed!!) { f -> f.keepUpdated = vm.autoUpdate }
                            }
                        )
                    }
                    Text(text = stringResource(R.string.keep_updated_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            if (vm.curPrefQueue != "None") {
                //                    auto add new to queue
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = androidx.media3.session.R.drawable.media3_icon_queue_add), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.audo_add_new_queue), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        var checked by remember { mutableStateOf(vm.feed?.autoAddNewToQueue != false) }
                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                checked = it
                                upsertBlk(vm.feed!!) { f -> f.autoAddNewToQueue = checked }
                            }
                        )
                    }
                    Text(text = stringResource(R.string.audo_add_new_queue_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            var autoEnqueueChecked by remember { mutableStateOf(vm.feed?.autoEnqueue == true) }
            Row(Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.auto_colon), style = CustomTextStyles.titleCustom, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.enqueue), style = CustomTextStyles.titleCustom, color = textColor)
                if (vm.curPrefQueue != "None") {
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(checked = autoEnqueueChecked, modifier = Modifier.height(24.dp),
                        onCheckedChange = {
                            autoEnqueueChecked = it
                            if (autoEnqueueChecked) autoDownloadChecked = false
                            upsertBlk(vm.feed!!) { f ->
                                f.autoEnqueue = autoEnqueueChecked
                                f.autoDownload = autoDownloadChecked
                            }
                        })
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.download), style = CustomTextStyles.titleCustom, color = textColor)
                if (vm.feed?.type != Feed.FeedType.YOUTUBE.name) {
                    if (isAutodownloadEnabled && !preferStreaming) {
                        //                    auto download
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = autoDownloadChecked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                autoDownloadChecked = it
                                if (autoDownloadChecked) autoEnqueueChecked = false
                                upsertBlk(vm.feed!!) { f ->
                                    f.autoDownload = autoDownloadChecked
                                    f.autoEnqueue = autoEnqueueChecked
                                }
                            })
                    }
                }
            }
            if (!autoEnqueueChecked && !autoDownloadChecked) {
                Text(text = stringResource(R.string.auto_enqueue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                if (vm.curPrefQueue == "None") Text(text = stringResource(R.string.auto_enqueue_sum1), style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(text = stringResource(R.string.auto_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                if (!isAutodownloadEnabled) Text(text = stringResource(R.string.auto_download_disabled_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if (autoDownloadChecked || autoEnqueueChecked) {
                var newCache by remember { mutableStateOf((vm.feed?.autoDLMaxEpisodes ?: 3).toString()) }
                @Composable
                fun SetAutoDLEQCacheDialog(onDismiss: () -> Unit) {
                    Dialog(onDismissRequest = onDismiss) {
                        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(value = newCache, onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newCache = it },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.max_episodes_cache)) })
                                //                    counting played
                                var countingPlayed by remember { mutableStateOf(vm.feed?.countingPlayed != false) }
                                if (autoDownloadChecked) Column {
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                    Row(Modifier.fillMaxWidth()) {
                                        Checkbox(checked = countingPlayed, modifier = Modifier.height(24.dp), onCheckedChange = { countingPlayed = it })
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(text = stringResource(R.string.pref_auto_download_counting_played_title), style = MaterialTheme.typography.bodyMedium, color = textColor)
                                    }
                                    Text(text = stringResource(R.string.pref_auto_download_counting_played_summary), style = MaterialTheme.typography.bodySmall, color = textColor)
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                }
                                Button(onClick = {
                                    if (newCache.isNotEmpty()) {
                                        upsertBlk(vm.feed!!) {
                                            it.autoDLMaxEpisodes = newCache.toIntOrNull() ?: 3
                                            if (autoDownloadChecked) it.countingPlayed = countingPlayed
                                        }
                                        onDismiss()
                                    }
                                }) { Text(stringResource(R.string.confirm_label)) }
                            }
                        }
                    }
                }
                //                    episode cache
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) SetAutoDLEQCacheDialog(onDismiss = { showDialog.value = false })
                        Text(text = stringResource(R.string.pref_episode_cache_title), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(newCache, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    include Soon
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.pref_auto_download_include_soon_title), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        var checked by remember { mutableStateOf(vm.feed?.autoDLSoon != false) }
                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                checked = it
                                upsertBlk(vm.feed!!) { f -> f.autoDLSoon = checked }
                            }
                        )
                    }
                    Text(text = stringResource(R.string.pref_auto_download_include_soon_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                val (selectedPolicy, onPolicySelected) = remember { mutableStateOf(vm.feed?.autoDLPolicy ?: AutoDownloadPolicy.ONLY_NEW) }
                @Composable
                fun AutoDLEQPolicyDialog(onDismissRequest: () -> Unit) {
                    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
                        title = { Text(stringResource(R.string.feed_automation_policy), style = CustomTextStyles.titleCustom) },
                        text = {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AutoDownloadPolicy.entries.forEach { item ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = (item == selectedPolicy), onCheckedChange = { onPolicySelected(item) })
                                        Text(text = stringResource(item.resId), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 8.dp))
                                    }
                                    if (selectedPolicy == AutoDownloadPolicy.ONLY_NEW && item == selectedPolicy)
                                        Row(Modifier.fillMaxWidth().padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                                            var replaceChecked by remember { mutableStateOf(selectedPolicy.replace) }
                                            Checkbox(checked = replaceChecked, onCheckedChange = {
                                                replaceChecked = it
                                                selectedPolicy.replace = it
                                                item.replace = it
                                            })
                                            Text(text = stringResource(R.string.replace), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.padding(start = 8.dp))
                                        }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                Logd(TAG, "autoDLPolicy: ${selectedPolicy.name} ${selectedPolicy.replace}")
                                upsertBlk(vm.feed!!) {
                                    it.autoDLPolicy = selectedPolicy
                                    if (selectedPolicy == AutoDownloadPolicy.FILTER_SORT) {
                                        it.episodeFilterADL = vm.feed!!.episodeFilter
                                        it.sortOrderADL = vm.feed!!.sortOrder
                                    }
                                }
                                onDismissRequest()
                            }) { Text(stringResource(R.string.confirm_label)) }
                        },
                        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
                    )
                }
                //                    automation policy
                Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        if (showDialog.value) AutoDLEQPolicyDialog(onDismissRequest = { showDialog.value = false })
                        Text(text = stringResource(R.string.feed_automation_policy) + ":", style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        Text(stringResource(selectedPolicy.resId), modifier = Modifier.padding(start = 20.dp))
                    }
                }
                if (selectedPolicy != AutoDownloadPolicy.FILTER_SORT) {
                    @OptIn(ExperimentalLayoutApi::class)
                    @Composable
                    fun AutoDownloadFilterDialog(filter: FeedAutoDownloadFilter, inexcl: ADLIncExc, onDismiss: () -> Unit, onConfirmed: (FeedAutoDownloadFilter) -> Unit) {
                        fun toFilterString(words: List<String>?): String {
                            val result = StringBuilder()
                            for (word in words!!) result.append("\"").append(word).append("\" ")
                            return result.toString()
                        }
                        Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
                            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                                val textColor = MaterialTheme.colorScheme.onSurface
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.episode_filters_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                    var termList = remember { if (inexcl == ADLIncExc.EXCLUDE) filter.excludeTerms.toMutableStateList() else filter.includeTerms.toMutableStateList() }
                                    var filterMinDuration by remember { mutableStateOf(filter.hasMinDurationFilter()) }
                                    var filterMaxDuration by remember { mutableStateOf(filter.hasMaxDurationFilter()) }
                                    var markPlayedChecked by remember { mutableStateOf(filter.markExcludedPlayed) }
                                    fun isFilterEnabled(): Boolean = termList.isNotEmpty() || filterMinDuration || filterMaxDuration || markPlayedChecked
                                    var filtermodifier by remember { mutableStateOf(isFilterEnabled()) }
                                    val textRes = remember { if (inexcl == ADLIncExc.EXCLUDE) R.string.exclude_terms else R.string.include_terms }
                                    Row {
                                        Checkbox(checked = filtermodifier, onCheckedChange = { isChecked ->
                                            filtermodifier = isChecked
                                            if (!filtermodifier) {
                                                termList.clear()
                                                filterMinDuration = false
                                                filterMaxDuration = false
                                                markPlayedChecked = false
                                            }
                                        })
                                        Text(text = stringResource(textRes), style = MaterialTheme.typography.bodyMedium.merge(), modifier = Modifier.weight(1f))
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        termList.forEach {
                                            FilterChip(onClick = {  }, label = { Text(it) }, selected = false, trailingIcon = {
                                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { termList.remove(it) })) })
                                        }
                                    }
                                    var text by remember { mutableStateOf("") }
                                    TextField(value = text, onValueChange = { newTerm -> text = newTerm },
                                        placeholder = { Text(stringResource(R.string.add_term)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (text.isNotBlank()) {
                                                    val newWord = text.replace("\"", "").trim { it <= ' ' }
                                                    if (newWord.isNotBlank() && newWord !in termList) {
                                                        termList.add(newWord)
                                                        text = ""
                                                    }
                                                    filtermodifier = isFilterEnabled()
                                                }
                                            }
                                        ),
                                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                    var filterMinDurationMinutes by remember { mutableStateOf((filter.minDurationFilter / 60).toString()) }
                                    var filterMaxDurationMinutes by remember { mutableStateOf((filter.maxDurationFilter / 60).toString()) }
                                    if (inexcl == ADLIncExc.EXCLUDE) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = filterMinDuration, onCheckedChange = { isChecked ->
                                                filterMinDuration = isChecked
                                                filtermodifier = isFilterEnabled()
                                            })
                                            Text(text = stringResource(R.string.exclude_shorter_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            if (filterMinDuration) {
                                                BasicTextField(value = filterMinDurationMinutes, textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                                                    modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                                                    onValueChange = { if (it.all { it.isDigit() }) filterMinDurationMinutes = it }, )
                                                Text(stringResource(R.string.time_minutes), color = textColor)
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = filterMaxDuration, onCheckedChange = { isChecked ->
                                                filtermodifier = isFilterEnabled()
                                                filterMaxDuration = isChecked
                                            })
                                            Text(text = stringResource(R.string.exclude_longer_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            if (filterMaxDuration) {
                                                BasicTextField(value = filterMaxDurationMinutes, onValueChange = { if (it.all { it.isDigit() }) filterMaxDurationMinutes = it },
                                                    textStyle = TextStyle(fontSize = 16.sp, color = textColor),
                                                    modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small))
                                                Text(stringResource(R.string.time_minutes), color = textColor)
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = markPlayedChecked, onCheckedChange = { isChecked ->
                                            filtermodifier = isFilterEnabled()
                                            markPlayedChecked = isChecked
                                        })
                                        Text(text = stringResource(R.string.mark_excluded_episodes_played), style = MaterialTheme.typography.bodyMedium.merge())
                                    }
                                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                                        Button(onClick = {
                                            if (inexcl == ADLIncExc.EXCLUDE) {
                                                if (filtermodifier) {
                                                    var minDuration = if (filterMinDuration) filterMinDurationMinutes.toInt() * 60 else -1
                                                    var maxDuration = if (filterMaxDuration) filterMaxDurationMinutes.toInt() * 60 else -1
                                                    val excludeFilter = toFilterString(termList)
                                                    onConfirmed(FeedAutoDownloadFilter(filter.includeFilterRaw, excludeFilter, minDuration, maxDuration, markPlayedChecked))
                                                } else onConfirmed(FeedAutoDownloadFilter())
                                            } else {
                                                if (filtermodifier) {
                                                    val includeFilter = toFilterString(termList)
                                                    onConfirmed(FeedAutoDownloadFilter(includeFilter, filter.excludeFilterRaw, filter.minDurationFilter, filter.maxDurationFilter, filter.markExcludedPlayed))
                                                } else onConfirmed(FeedAutoDownloadFilter())
                                            }
                                            onDismiss()
                                        }) { Text(stringResource(R.string.confirm_label)) }
                                        Spacer(Modifier.weight(1f))
                                        Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                                    }
                                }
                            }
                        }
                    }
                    //                    inclusive filter
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoDownloadFilterDialog(vm.feed?.autoDownloadFilter!!, ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter -> upsertBlk(vm.feed!!) { it.autoDownloadFilter = filter } }
                            Text(text = stringResource(R.string.episode_inclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    exclusive filter
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoDownloadFilterDialog(vm.feed?.autoDownloadFilter!!, ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter -> upsertBlk(vm.feed!!) { it.autoDownloadFilter = filter } }
                            Text(text = stringResource(R.string.episode_exclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                } else {
                    Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                        Text("Sorted by: " + stringResource(vm.feed?.sortOrderADL?.res ?: 0), modifier = Modifier.padding(start = 10.dp))
                        Text("Filtered by: ", modifier = Modifier.padding(start = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 20.dp)) {
                            vm.feed?.episodeFilterADL?.propertySet?.forEach { FilterChip(onClick = { }, label = { Text(it) }, selected = false) }
                        }
                    }
                }
            }
        }
    }
}


enum class ADLIncExc {
    INCLUDE,
    EXCLUDE
}

private const val TAG: String = "FeedSettingsScreen"
val queueSettingOptions = listOf("Default", "Active", "None", "Custom")


