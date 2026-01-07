package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.AutoDeleteAction
import ac.mdiq.podcini.storage.model.Feed.AutoDownloadPolicy
import ac.mdiq.podcini.storage.model.Feed.Companion.DEFAULT_INTERVALS
import ac.mdiq.podcini.storage.model.Feed.Companion.FeedAutoDeleteOptions
import ac.mdiq.podcini.storage.model.Feed.Companion.INTERVAL_UNITS
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.volumes
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.actions.ButtonTypes
import ac.mdiq.podcini.ui.actions.playActions
import ac.mdiq.podcini.ui.actions.streamActions
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeSortDialog
import ac.mdiq.podcini.ui.compose.EpisodesFilterDialog
import ac.mdiq.podcini.ui.compose.FilterChipBorder
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.RenameOrCreateSyntheticFeed
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.VideoModeDialog
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xilinjia.krdb.ext.query
import io.github.xilinjia.krdb.ext.toRealmList
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "FeedSettingsScreen"

var feedsToSet: List<Feed> = listOf()

@Composable
fun FeedsSettingsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    var feedFlow by remember { mutableStateOf<Flow<SingleQueryChange<Feed>>>(emptyFlow()) }
    var feedToSet by remember { mutableStateOf(if (feedsToSet.size == 1) feedsToSet[0] else Feed()) }

    var audioType by remember { mutableStateOf(feedToSet.audioTypeSetting.tag) }

    var audioQuality by remember { mutableStateOf(feedToSet.audioQualitySetting.tag) }
    var videoQuality by remember { mutableStateOf(feedToSet.videoQualitySetting.tag) }

    var autoUpdate by remember { mutableStateOf(feedToSet.keepUpdated) }

    var autoDeleteSummaryResId by remember { mutableIntStateOf(R.string.global_default) }
    var curPrefQueue by remember { mutableStateOf(feedToSet.queueTextExt) }
    var autoDeletePolicy by remember { mutableStateOf(AutoDeleteAction.GLOBAL.name) }
    var videoModeSummaryResId by remember { mutableIntStateOf(R.string.global_default) }

    fun refresh() {
        audioType = feedToSet.audioTypeSetting.tag
        audioQuality = feedToSet.audioQualitySetting.tag
        videoQuality = feedToSet.videoQualitySetting.tag
        videoModeSummaryResId = when (feedToSet.videoModePolicy) {
            VideoMode.NONE -> R.string.global_default
            VideoMode.WINDOW_VIEW -> R.string.feed_video_mode_window
            VideoMode.FULL_SCREEN_VIEW -> R.string.feed_video_mode_fullscreen
            VideoMode.AUDIO_ONLY -> R.string.feed_video_mode_audioonly
        }
        when (feedToSet.autoDeleteAction) {
            AutoDeleteAction.GLOBAL -> {
                autoDeleteSummaryResId = R.string.global_default
                autoDeletePolicy = AutoDeleteAction.GLOBAL.tag
            }
            AutoDeleteAction.ALWAYS -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_always
                autoDeletePolicy = AutoDeleteAction.ALWAYS.tag
            }
            AutoDeleteAction.NEVER -> {
                autoDeleteSummaryResId = R.string.feed_auto_download_never
                autoDeletePolicy = AutoDeleteAction.NEVER.tag
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            Logd(TAG, "LifecycleEventObserver event: $event")
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (feedsToSet.size == 1) feedFlow = realm.query<Feed>("id == $0", feedsToSet[0].id).first().asFlow()
                Lifecycle.Event.ON_START -> {}
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

    LaunchedEffect(feedToSet) {
        Logd(TAG, "LaunchedEffect feed")
        refresh()
    }

    val feedChange by feedFlow.collectAsStateWithLifecycle(initialValue = null)
    if (feedChange?.obj != null) feedToSet = feedChange?.obj!!

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = { Text(text = stringResource(R.string.feed_settings_label), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }, navigationIcon = { IconButton(onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.previousBackStackEntry?.savedStateHandle?.set(COME_BACK, true)
                    navController.popBackStack()
                } else drawerController.open()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    @Composable
    fun TitleSummarySwitch(titleRes: Int, summaryRes: Int, iconRes: Int, initVal: Boolean, cb: ((Boolean)->Unit)) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                if (iconRes > 0) Icon(ImageVector.vectorResource(id = iconRes), "", tint = textColor)
                Spacer(modifier = Modifier.width(20.dp))
                Text(text = stringResource(titleRes), style = CustomTextStyles.titleCustom, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                var checked by remember { mutableStateOf(initVal) }
                Switch(checked = checked, modifier = Modifier.height(24.dp), onCheckedChange = {
                    checked = it
                    cb.invoke(it)
                })
            }
            Text(text = stringResource(summaryRes), style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(start = 20.dp, end = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (feedsToSet.size == 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // edit title
                    Text(text = stringResource(R.string.title), style = CustomTextStyles.titleCustom)
                    Spacer(Modifier.weight(1f))
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) RenameOrCreateSyntheticFeed(feedToSet) { showDialog = false }
                    IconButton(onClick = { showDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit title") }
                }
                Text(text = feedToSet.title!!, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 15.dp))
            } else Text(text = stringResource(R.string.multiple_podcasts), style = MaterialTheme.typography.titleMedium,  maxLines=1)
            //                    parent volume
            Column {
                val none = "None"
                var curVolumeName by remember { mutableStateOf(if (feedToSet.volumeId == -1L) none else volumes.find { it.id == feedToSet.volumeId }?.name ?: none ) }
                @Composable
                fun SetVolume(selectedOption: String, onDismissRequest: () -> Unit) {
                    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val custom = "Custom"
                            var selected by remember {mutableStateOf(if (selectedOption == none) none else custom)}
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = none == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = none
                                        runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.volumeId = -1L } } }
                                        curVolumeName = selected
                                        onDismissRequest()
                                    })
                                Text(none)
                                Spacer(Modifier.width(50.dp))
                                Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                                Text(custom)
                            }
                            if (selected == custom) {
                                Logd(TAG, "volumes: ${volumes.size}")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (i in volumes.indices) {
                                        FilterChip(onClick = {
                                            val v = volumes[i]
                                            runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.volumeId = v.id } } }
                                            curVolumeName = v.name
                                            onDismissRequest()
                                        }, label = { Text(volumes[i].name) }, selected = false, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary))
                                    }
                                }
                            }
                        }
                    }
                }
                var showDialog by remember { mutableStateOf(false) }
                //                    var selectedOption by remember { mutableStateOf(feed.queueText ?: "None") }
                if (showDialog) SetVolume(selectedOption = curVolumeName, onDismissRequest = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.rounded_books_movies_and_music_24), "", tint = textColor, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_parent_volume), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                }
                Text(text = curVolumeName + " : " + stringResource(R.string.pref_parent_volume_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }

            var useWideLayout by remember { mutableStateOf(feedToSet.useWideLayout) }
            TitleSummarySwitch(R.string.use_wide_layout, R.string.use_wide_layout_summary, R.drawable.rounded_responsive_layout_24, useWideLayout) {
                useWideLayout = it
                runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.useWideLayout = it } } }
            }
            var useEpisodeImage by remember { mutableStateOf(feedToSet.useEpisodeImage) }
            TitleSummarySwitch(R.string.use_episode_image, R.string.use_episode_image_summary, R.drawable.outline_broken_image_24, useEpisodeImage) {
                useEpisodeImage = it
                runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.useEpisodeImage = it } } }
            }
            Column {
                var showDialog by remember { mutableStateOf(false) }
                @Composable
                fun SetAudioType(selectedOption: String, onDismissRequest: () -> Unit) {
                    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                        var selected by remember {mutableStateOf(selectedOption)}
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Feed.AudioType.entries.forEach { option ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = option.tag == selected,
                                        onCheckedChange = { isChecked ->
                                            selected = option.tag
                                            if (isChecked) Logd(TAG, "$option is checked")
                                            val type = Feed.AudioType.fromTag(selected)
                                            audioType = type.tag
                                            runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.audioType = type.code } } }
                                            onDismissRequest()
                                        }
                                    )
                                    Text(option.tag)
                                }
                            }
                        }
                    }
                }
                if (showDialog) SetAudioType(selectedOption = audioType, onDismissRequest = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_audio_type), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                    Spacer(modifier = Modifier.width(30.dp))
                    Text(audioType, style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                Text(text = stringResource(R.string.pref_feed_audio_type_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if (feedToSet.langSet.size > 1 || feedsToSet.size > 1) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                    Text(stringResource(R.string.preferred_languages), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    var showIcon by remember { mutableStateOf(false) }
                    var newName by remember { mutableStateOf(feedToSet.preferredLnaguages.ifEmpty { feedToSet.langSet }.joinToString(", ")) }
                    TextField(value = newName,
                        onValueChange = {
                            newName = it
                            showIcon =  true
                        },
                        trailingIcon = {
                            if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(
                                onClick = {
                                    runOnIOScope {
                                        realm.write { for (f in feedsToSet) { findLatest(f)?.let { att ->
                                            att.preferredLnaguages.clear()
                                            att.preferredLnaguages.addAll(newName.split(',').map { it.trim() }.filter { it.isNotEmpty() })
                                        } } }
                                    }
                                    showIcon =  false
                                }))
                        })
                    Text("", color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
            if ((feedToSet.id >= MAX_NATURAL_SYNTHETIC_ID && feedToSet.hasVideoMedia) || feedsToSet.size > 1) {
                //                    video mode
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        var showDialog by remember { mutableStateOf(false) }
                        if (showDialog) VideoModeDialog(initMode = feedToSet.videoModePolicy, onDismissRequest = { showDialog = false }) { mode ->
                            videoModeSummaryResId = when (mode) {
                                VideoMode.NONE -> R.string.global_default
                                VideoMode.WINDOW_VIEW -> R.string.feed_video_mode_window
                                VideoMode.FULL_SCREEN_VIEW -> R.string.feed_video_mode_fullscreen
                                VideoMode.AUDIO_ONLY -> R.string.feed_video_mode_audioonly
                            }
                            runOnIOScope { realm.write { for (f in feedsToSet) { if (f.hasVideoMedia) findLatest(f)?.videoModePolicy = mode } } }
                        }
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.feed_video_mode_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog = true }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(text = stringResource(videoModeSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
            if (feedToSet.type == Feed.FeedType.YOUTUBE.name || (feedsToSet.size > 1 && gearbox.supportAudioQualities())) {
                //                    audio quality
                Column {
                    var showDialog by remember { mutableStateOf(false) }
                    @Composable
                    fun SetAudioQuality(selectedOption: String, onDismissRequest: () -> Unit) {
                        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                            var selected by remember {mutableStateOf(selectedOption)}
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Feed.AVQuality.entries.forEach { option ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = option.tag == selected,
                                            onCheckedChange = { isChecked ->
                                                selected = option.tag
                                                if (isChecked) Logd(TAG, "$option is checked")
                                                val type = Feed.AVQuality.fromTag(selected)
                                                audioQuality = type.tag
                                                runOnIOScope { realm.write { for (f in feedsToSet) { if (f.type == Feed.FeedType.YOUTUBE.name) findLatest(f)?.audioQuality = type.code } } }
                                                onDismissRequest()
                                            })
                                        Text(option.tag)
                                    }
                                }
                            }
                        }
                    }
                    if (showDialog) SetAudioQuality(selectedOption = audioQuality, onDismissRequest = { showDialog = false })
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.baseline_audiotrack_24), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.pref_feed_audio_quality), style = CustomTextStyles.titleCustom, color = textColor,
                            modifier = Modifier.clickable(onClick = {
//                                audioQuality = feed.audioQualitySetting.tag
                                showDialog = true
                            }))
                        Spacer(modifier = Modifier.width(30.dp))
                        Text(audioQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_feed_audio_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                if (feedToSet.videoModePolicy != VideoMode.AUDIO_ONLY || (feedsToSet.size > 1 && gearbox.supportAudioQualities())) {
                    //                    video quality
                    Column {
                        var showDialog by remember { mutableStateOf(false) }
                        @Composable
                        fun SetVideoQuality(selectedOption: String, onDismissRequest: () -> Unit) {
                            CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                                var selected by remember {mutableStateOf(selectedOption)}
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Feed.AVQuality.entries.forEach { option ->
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = option.tag == selected,
                                                onCheckedChange = { isChecked ->
                                                    selected = option.tag
                                                    if (isChecked) Logd(TAG, "$option is checked")
                                                    val type = Feed.AVQuality.fromTag(selected)
                                                    videoQuality = type.tag
                                                    runOnIOScope { realm.write { for (f in feedsToSet) { if (f.type == Feed.FeedType.YOUTUBE.name && f.videoModePolicy != VideoMode.AUDIO_ONLY) findLatest(f)?.videoQuality = type.code } } }
                                                    onDismissRequest()
                                                })
                                            Text(option.tag)
                                        }
                                    }
                                }
                            }
                        }
                        if (showDialog) SetVideoQuality(selectedOption = videoQuality, onDismissRequest = { showDialog = false })
                        Row(Modifier.fillMaxWidth()) {
                            Icon(ImageVector.vectorResource(id = R.drawable.ic_videocam), "", tint = textColor)
                            Spacer(modifier = Modifier.width(20.dp))
                            Text(text = stringResource(R.string.pref_feed_video_quality), style = CustomTextStyles.titleCustom, color = textColor,
                                modifier = Modifier.clickable(onClick = {
//                                    videoQuality = feed.videoQualitySetting.tag
                                    showDialog = true
                                }))
                            Spacer(modifier = Modifier.width(30.dp))
                            Text(videoQuality, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        Text(text = stringResource(R.string.pref_feed_video_quality_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                }
            }
            //                    associated queue
            Column {
                curPrefQueue = feedToSet.queueTextExt
                @Composable
                fun SetAssociatedQueue(selectedOption: String, onDismissRequest: () -> Unit) {
                    CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val custom = "Custom"
                            val none = "None"
                            var selected by remember {mutableStateOf(if (selectedOption == none) none else custom)}
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = none == selected,
                                    onCheckedChange = { isChecked ->
                                        selected = none
                                        runOnIOScope {
                                            realm.write { for (f in feedsToSet) { findLatest(f)?.let {
                                                it.queueId = -2L
                                                it.autoDownload = false
                                                it.autoEnqueue = false
                                            } } }
                                        }
                                        curPrefQueue = selected
                                        onDismissRequest()
                                    })
                                Text(none)
                                Spacer(Modifier.width(50.dp))
                                Checkbox(checked = custom == selected, onCheckedChange = { isChecked -> selected = custom })
                                Text(custom)
                            }
                            if (selected == custom) {
                                Logd(TAG, "queues: ${queuesLive.size}")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    for (i in queuesLive.indices) {
                                        FilterChip(onClick = {
                                            val q = queuesLive[i]
                                            runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.queue = q } } }
                                            curPrefQueue = q.name
                                            onDismissRequest()
                                        }, label = { Text(queuesLive[i].name) }, selected = false, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary))
                                    }
                                }
                            }
                        }
                    }
                }
                var showDialog by remember { mutableStateOf(false) }
                var selectedOption by remember { mutableStateOf(feedToSet.queueText) }
                if (showDialog) SetAssociatedQueue(selectedOption = selectedOption, onDismissRequest = { showDialog = false })
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playlist_play), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_associated_queue), style = CustomTextStyles.titleCustom, color = textColor,
                        modifier = Modifier.clickable(onClick = {
                            selectedOption = feedToSet.queueText
                            showDialog = true
                        })
                    )
                }
                Text(text = curPrefQueue + " : " + stringResource(R.string.pref_feed_associated_queue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    tags
            var showTagsSettingDialog by remember { mutableStateOf(false) }
            if (showTagsSettingDialog) TagSettingDialog(TagType.Feed, feedToSet.tags, onDismiss = { showTagsSettingDialog = false }) { tags ->
                runOnIOScope {
                    realm.write { for (f in feedsToSet) { findLatest(f)?.let {
                        it.tags.clear()
                        it.tags.addAll(tags)
                    } } }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_tag), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.tags_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showTagsSettingDialog = true }))
                }
                Text(text = stringResource(R.string.feed_tags_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    playback speed
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    if (showDialog.value) PlaybackSpeedDialog(feedsToSet, initSpeed = feedToSet.playSpeed, maxSpeed = 3f,
                        onDismiss = { showDialog.value = false }) { newSpeed -> runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.playSpeed = newSpeed } } } }
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_playback_speed), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.playback_speed), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //              skip silence
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_skip_silence_title), style = CustomTextStyles.titleCustom, color = textColor)
                }
                Row(Modifier.fillMaxWidth()) {
                    var glChecked by remember { mutableStateOf(feedToSet.skipSilence == null) }
                    Checkbox(checked = glChecked, modifier = Modifier.height(24.dp), onCheckedChange = {
                        glChecked = it
                        runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.let { f ->
                            if (glChecked) f.skipSilence = null
                            else f.skipSilence = f.skipSilence ?: false
                        } } } }
                    })
                    Text(text = stringResource(R.string.global), style = CustomTextStyles.titleCustom, color = textColor)
                    if (!glChecked) {
                        Spacer(modifier = Modifier.width(20.dp))
                        var checked by remember { mutableStateOf(feedToSet.skipSilence ?: false) }
                        Switch(checked = checked, modifier = Modifier.height(24.dp), onCheckedChange = {
                            checked = it
                            runOnIOScope {
                                realm.write {
                                    for (f in feedsToSet) {
                                        findLatest(f)?.let { f -> f.skipSilence = checked }
                                    }
                                }
                            }
                        })
                    }
                }
                Text(text = stringResource(R.string.pref_feed_playback_speed_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    volume adaption
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    @Composable
                    fun VolumeAdaptionDialog(onDismissRequest: () -> Unit) {
                        CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                            val (selectedOption, onOptionSelected) = remember { mutableStateOf(feedToSet.volumeAdaptionSetting ) }
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                VolumeAdaptionSetting.entries.forEach { item ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = (item == selectedOption),
                                            onCheckedChange = { _ ->
                                                Logd(TAG, "row clicked: $item $selectedOption")
                                                if (item != selectedOption) {
                                                    onOptionSelected(item)
                                                    runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.volumeAdaptionSetting = item } } }
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
                    if (showDialog.value) VolumeAdaptionDialog(onDismissRequest = { showDialog.value = false })
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_volume_adaption), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.feed_volume_adapdation), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.feed_volume_adaptation_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    authentication
            if ((feedToSet.id > 0 && !feedToSet.isLocalFeed) || feedsToSet.size > 1) {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        @Composable
                        fun AuthenticationDialog(onDismiss: () -> Unit) {
                            CommonPopupCard(onDismissRequest = onDismiss) {
                                val context = LocalContext.current
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val oldName = feedToSet.username?:""
                                    var newName by remember { mutableStateOf(oldName) }
                                    TextField(value = newName, onValueChange = { newName = it }, label = { Text("Username") })
                                    val oldPW = feedToSet.password?:""
                                    var newPW by remember { mutableStateOf(oldPW) }
                                    TextField(value = newPW, onValueChange = { newPW = it }, label = { Text("Password") })
                                    Button(onClick = {
                                        if (newName.isNotEmpty() && oldName != newName) {
                                            runOnIOScope {
                                                realm.write { for (f in feedsToSet) { if (!f.isLocalFeed) findLatest(f)?.let {
                                                    it.username = newName
                                                    it.password = newPW
                                                } } }
                                                gearbox.feedUpdater(feedsToSet).startRefresh(context)
                                            }
                                            onDismiss()
                                        }
                                    }) { Text(stringResource(R.string.confirm_label)) }
                                }
                            }
                        }
                        if (showDialog.value) AuthenticationDialog(onDismiss = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_key), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.authentication_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.authentication_descr), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }

            var autoDownloadChecked by remember { mutableStateOf(feedToSet.autoDownload) }
            var preferStreaming by remember { mutableStateOf(feedToSet.prefStreamOverDownload) }
            if (feedToSet.type != Feed.FeedType.YOUTUBE.name || !preferStreaming || feedsToSet.size > 1) {
                //                    prefer streaming
                TitleSummarySwitch(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, R.drawable.ic_stream, preferStreaming) {
                    preferStreaming = it
                    if (preferStreaming) {
                        prefStreamOverDownload = true
                        autoDownloadChecked = false
                    }
                    runOnIOScope {
                        realm.write { for (f in feedsToSet) { if (f.type != Feed.FeedType.YOUTUBE.name) findLatest(f)?.let { f ->
                            f.prefStreamOverDownload = preferStreaming
                            if (preferStreaming) f.autoDownload = false
                        } } }
                    }
                }
                //                    preferred action
                val actions = remember { listOf("Auto", ButtonTypes.PLAY_LOCAL.name) + playActions.map { it.name } + streamActions.map { it.name } + listOf(ButtonTypes.TTS_NOW.name, ButtonTypes.TTS.name, ButtonTypes.WEBSITE.name) }
                val curAction by remember { mutableStateOf(feedToSet.prefActionType ?: "Auto") }
                var showChooseAction by remember { mutableStateOf(false) }
                if (showChooseAction) Popup(onDismissRequest = { showChooseAction = false }, alignment = Alignment.TopStart, offset = IntOffset(100, 100), properties = PopupProperties(focusable = true)) {
                    Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                            for (action in actions) {
                                FilterChip(onClick = {
                                    if (action == "Auto") runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.let { it.prefActionType = null } } } }
                                    else {
                                        if (action in streamActions.map { it.name }) preferStreaming = true else if (action in playActions.map { it.name }) preferStreaming = false
                                        if (preferStreaming) {
                                            prefStreamOverDownload = true
                                            autoDownloadChecked = false
                                        }
                                        runOnIOScope {
                                            realm.write {
                                                for (f in feedsToSet) {
                                                    findLatest(f)?.let {
                                                        it.prefActionType = action
                                                        it.prefStreamOverDownload = preferStreaming
                                                        if (preferStreaming) it.autoDownload = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    showChooseAction = false
                                }, label = { Text(action) }, selected = curAction == action, border = FilterChipBorder(curAction == action))
                            }
                        }
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.play_stream_svgrepo_com), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.preferred_action), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showChooseAction = true }))
                    }
                    Text(text = stringResource(R.string.preferred_action_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            //                    auto skip
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    @Composable
                    fun AutoSkipDialog(onDismiss: () -> Unit) {
                        CommonPopupCard(onDismissRequest = onDismiss) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                var intro by remember { mutableIntStateOf((feedToSet.introSkip)) }
                                NumberEditor(intro, label = stringResource(R.string.skip_first_hint), nz = false, instant = true, modifier = Modifier) { intro = it }
                                var ending by remember { mutableIntStateOf((feedToSet.endingSkip)) }
                                NumberEditor(ending, label = stringResource(R.string.skip_last_hint), nz = false, instant = true, modifier = Modifier) { ending = it }
                                Button(onClick = {
                                    runOnIOScope {
                                        realm.write { for (f in feedsToSet) { findLatest(f)?.let {
                                            it.introSkip = intro
                                            it.endingSkip = ending
                                        } } }
                                    }
                                    onDismiss()
                                }) { Text(stringResource(R.string.confirm_label)) }
                            }
                        }
                    }
                    if (showDialog.value) AutoSkipDialog(onDismiss = { showDialog.value = false })
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_skip_24dp), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_skip), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.pref_feed_skip_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            //                    repeat intervals
            Column {
                Row(Modifier.fillMaxWidth()) {
                    val showDialog = remember { mutableStateOf(false) }
                    @Composable
                    fun RepeatIntervalsDialog(onDismiss: () -> Unit) {
                        CommonPopupCard(onDismissRequest = onDismiss) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                var intervals = remember { feedToSet.repeatIntervals.toMutableList() }
                                if (intervals.isEmpty()) intervals = DEFAULT_INTERVALS.toMutableList()
                                val units = INTERVAL_UNITS.map { stringResource(it) }
                                for (i in intervals.indices) {
                                    NumberEditor(intervals[i], label = "in " + units[i], nz = false, instant = true, modifier = Modifier) { intervals[i] = it }
                                }
                                Button(onClick = {
                                    runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.repeatIntervals = intervals.toRealmList() } } }
                                    onDismiss()
                                }) { Text(stringResource(R.string.confirm_label)) }
                            }
                        }
                    }
                    if (showDialog.value) RepeatIntervalsDialog(onDismiss = { showDialog.value = false })
                    Icon(ImageVector.vectorResource(id = R.drawable.baseline_replay_24), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.pref_feed_intervals), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                }
                Text(text = stringResource(R.string.pref_feed_intervals_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if (feedToSet.type != Feed.FeedType.YOUTUBE.name || feedsToSet.size > 1) {
                //                    auto delete
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        val showDialog = remember { mutableStateOf(false) }
                        @Composable
                        fun AutoDeleteDialog(onDismissRequest: () -> Unit) {
                            CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                                val (selectedOption, onOptionSelected) = remember { mutableStateOf(autoDeletePolicy) }
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
                                                        runOnIOScope { realm.write { for (f in feedsToSet) { if (f.type != Feed.FeedType.YOUTUBE.name) findLatest(f)?.autoDeleteAction = action_ } } }
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
                        if (showDialog.value) AutoDeleteDialog(onDismissRequest = { showDialog.value = false })
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.auto_delete_episode), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                    }
                    Text(text = stringResource(R.string.auto_delete_sum) + ": " + stringResource(autoDeleteSummaryResId), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            if (feedToSet.id > MAX_SYNTHETIC_ID || feedsToSet.size > 1) {
                //                    max episodes
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(ImageVector.vectorResource(id = R.drawable.ic_refresh), "", tint = textColor)
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(text = stringResource(R.string.limit_episodes_to), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        NumberEditor(feedToSet.limitEpisodesCount, label = "0 = unlimited", nz = false, modifier = Modifier.width(150.dp)) {
                            runOnIOScope { realm.write { for (f in feedsToSet) if (f.id > MAX_SYNTHETIC_ID) findLatest(f)?.limitEpisodesCount = it } }
                        }
                    }
                    Text(text = stringResource(R.string.limit_episodes_to_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
            }
            if (feedToSet.id > MAX_SYNTHETIC_ID) {
                // edit feed url
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Podcast URL", style = CustomTextStyles.titleCustom)
                    Spacer(Modifier.weight(1f))
                    @Composable
                    fun EditUrlSettingsDialog(onDismiss: () -> Unit) {
                        var url by remember { mutableStateOf(feedToSet.downloadUrl ?: "") }
                        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_url_menu)) },
                            text = { Column {
                                Text(stringResource(R.string.edit_url_confirmation_msg))
                                TextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth())
                            } },
                            confirmButton = {
                                TextButton(onClick = {
                                    runOnIOScope {
                                        feedToSet = upsert(feedToSet) { it.downloadUrl = url }
                                        gearbox.feedUpdater(listOf(feedToSet)).startRefresh(context)
                                    }
                                    onDismiss()
                                }) { Text("OK") }
                            },
                            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_label)) } }
                        )
                    }
                    var showDialog by remember { mutableStateOf(false) }
                    if (showDialog) EditUrlSettingsDialog { showDialog = false }
                    IconButton(onClick = { showDialog = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit url") }
                }
                Text(text = feedToSet.downloadUrl ?: "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 15.dp))
            }
            if (feedToSet.id > MAX_SYNTHETIC_ID || feedsToSet.size > 1) {
                //                    refresh
                TitleSummarySwitch(R.string.keep_updated, R.string.keep_updated_summary, R.drawable.ic_refresh, autoUpdate) {
                    autoUpdate = it
                    runOnIOScope { realm.write { for (f in feedsToSet) { if (f.id > MAX_SYNTHETIC_ID) findLatest(f)?.keepUpdated = autoUpdate } } }
                }
            }
            if (curPrefQueue != "None" || feedsToSet.size > 1) {
                //                    auto add new to queue
                TitleSummarySwitch(R.string.audo_add_new_queue, R.string.audo_add_new_queue_summary, R.drawable.outline_playlist_add_24, feedToSet.autoAddNewToQueue) {
                    runOnIOScope { realm.write { for (f in feedsToSet) { if (f.queueTextExt != "None") findLatest(f)?.autoAddNewToQueue = it } } }
                }
            }
            var showSortDialog by remember { mutableStateOf(false) }
            if (showSortDialog) {
                EpisodeSortDialog(initOrder = feedToSet.episodeSortOrder, onDismissRequest = { showSortDialog = false }) { order ->
                    runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.let { f -> f.episodeSortOrder = order ?: EpisodeSortOrder.DATE_DESC } } } }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.arrows_sort), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.sort), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showSortDialog = true }))
                }
                Text(text = stringResource(R.string.sort_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            var showFilterDialog by remember {  mutableStateOf(false) }
            if (showFilterDialog) {
                EpisodesFilterDialog(filter_ = feedToSet.episodeFilter, onDismissRequest = { showFilterDialog = false }) { filter ->
                    runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.let { f -> f.episodeFilter = filter } } } }
                }
            }
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Icon(ImageVector.vectorResource(id = R.drawable.ic_filter), "", tint = textColor)
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(text = stringResource(R.string.filter), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showFilterDialog = true }))
                }
                Text(text = stringResource(R.string.filter_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            var autoEnqueueChecked by remember { mutableStateOf(feedToSet.autoEnqueue) }
            Row(Modifier.fillMaxWidth().padding(top=5.dp)) {
                Text(text = stringResource(R.string.auto_colon), style = CustomTextStyles.titleCustom, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.enqueue), style = CustomTextStyles.titleCustom, color = textColor)
                if (curPrefQueue != "None") {
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(checked = autoEnqueueChecked, modifier = Modifier.height(24.dp),
                        onCheckedChange = {
                            autoEnqueueChecked = it
                            if (autoEnqueueChecked) autoDownloadChecked = false
                            runOnIOScope {
                                realm.write { for (f in feedsToSet) { findLatest(f)?.let { f ->
                                    f.autoEnqueue = autoEnqueueChecked
                                    f.autoDownload = autoDownloadChecked
                                } } }
                            }
                        })
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = stringResource(R.string.download), style = CustomTextStyles.titleCustom, color = textColor)
                if (feedToSet.type != Feed.FeedType.YOUTUBE.name || feedsToSet.size > 1) {
                    if (isAutodownloadEnabled && !preferStreaming) {
                        //                    auto download
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(checked = autoDownloadChecked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                autoDownloadChecked = it
                                if (autoDownloadChecked) autoEnqueueChecked = false
                                runOnIOScope {
                                    realm.write { for (f in feedsToSet) { if (f.type != Feed.FeedType.YOUTUBE.name) findLatest(f)?.let { f ->
                                        f.autoDownload = autoDownloadChecked
                                        f.autoEnqueue = autoEnqueueChecked
                                    } } }
                                }
                            })
                    }
                }
            }
            if (!autoEnqueueChecked && !autoDownloadChecked) {
                Text(text = stringResource(R.string.auto_enqueue_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                if (curPrefQueue == "None") Text(text = stringResource(R.string.auto_enqueue_sum1), style = MaterialTheme.typography.bodyMedium, color = textColor)
                Text(text = stringResource(R.string.auto_download_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
                if (!isAutodownloadEnabled) Text(text = stringResource(R.string.auto_download_disabled_sum), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
            if (autoDownloadChecked || autoEnqueueChecked) {
                var newCache by remember { mutableIntStateOf((feedToSet.autoDLMaxEpisodes)) }
                @Composable
                fun SetAutoDLEQCacheDialog(onDismiss: () -> Unit) {
                    CommonPopupCard(onDismissRequest = onDismiss) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberEditor(newCache, label = stringResource(R.string.max_episodes_cache), nz = false, instant = true, modifier = Modifier) { newCache = it }
                            //                    counting played
                            var countingPlayed by remember { mutableStateOf(feedToSet.countingPlayed) }
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
                                if (newCache > 0) {
                                    runOnIOScope {
                                        realm.write { for (f in feedsToSet) { findLatest(f)?.let {
                                            it.autoDLMaxEpisodes = newCache
                                            if (autoDownloadChecked) it.countingPlayed = countingPlayed
                                        } } }
                                    }
                                    onDismiss()
                                }
                            }) { Text(stringResource(R.string.confirm_label)) }
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
                        Text(newCache.toString(), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    Text(text = stringResource(R.string.pref_episode_cache_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                //                    include Soon
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.pref_auto_download_include_soon_title), style = CustomTextStyles.titleCustom, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        var checked by remember { mutableStateOf(feedToSet.autoDLSoon) }
                        Switch(checked = checked, modifier = Modifier.height(24.dp),
                            onCheckedChange = {
                                checked = it
                                runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.autoDLSoon = checked } } }
                            }
                        )
                    }
                    Text(text = stringResource(R.string.pref_auto_download_include_soon_summary), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                val (selectedPolicy, onPolicySelected) = remember { mutableStateOf(feedToSet.autoDLPolicy) }
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
                                runOnIOScope {
                                    realm.write { for (f in feedsToSet) { findLatest(f)?.let {
                                        it.autoDLPolicy = selectedPolicy
                                        if (selectedPolicy == AutoDownloadPolicy.FILTER_SORT) {
                                            it.episodeFilterADL = it.episodeFilter
                                            it.episodesSortOrderADL = it.episodeSortOrder
                                        }
                                    } } }
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
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.episode_filters_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                    val termList = remember { if (inexcl == ADLIncExc.EXCLUDE) filter.excludeTerms.toMutableStateList() else filter.includeTerms.toMutableStateList() }
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
                                    fun setText() {
                                        if (text.isNotBlank()) {
                                            val newWord = text.replace("\"", "").trim { it <= ' ' }
                                            if (newWord.isNotBlank() && newWord !in termList) {
                                                termList.add(newWord)
                                                text = ""
                                            }
                                            filtermodifier = isFilterEnabled()
                                        }
                                    }
                                    TextField(value = text, onValueChange = { newTerm -> text = newTerm },
                                        placeholder = { Text(stringResource(R.string.add_term_hint)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { setText() }),
                                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add term", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                            setText()
                                        })) },
                                        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth()
                                    )
                                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
                                    var filterMinDurationMinutes by remember { mutableIntStateOf((filter.minDurationFilter / 60)) }
                                    var filterMaxDurationMinutes by remember { mutableIntStateOf((filter.maxDurationFilter / 60)) }
                                    if (inexcl == ADLIncExc.EXCLUDE) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = filterMinDuration, onCheckedChange = { isChecked ->
                                                filterMinDuration = isChecked
                                                filtermodifier = isFilterEnabled()
                                            })
                                            Text(text = stringResource(R.string.exclude_shorter_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            if (filterMinDuration) {
                                                NumberEditor(filterMinDurationMinutes, stringResource(R.string.time_minutes), nz = true, instant = true, modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)) {
                                                    filterMinDurationMinutes = it
                                                }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = filterMaxDuration, onCheckedChange = { isChecked ->
                                                filtermodifier = isFilterEnabled()
                                                filterMaxDuration = isChecked
                                            })
                                            Text(text = stringResource(R.string.exclude_longer_than), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            if (filterMaxDuration) {
                                                NumberEditor(filterMaxDurationMinutes, stringResource(R.string.time_minutes), nz = true, instant = true, modifier = Modifier.width(50.dp).height(30.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)) {
                                                    filterMaxDurationMinutes = it
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
                                    }
                                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                                        Button(onClick = {
                                            if (inexcl == ADLIncExc.EXCLUDE) {
                                                if (filtermodifier) {
                                                    val minDuration = if (filterMinDuration) filterMinDurationMinutes * 60 else -1
                                                    val maxDuration = if (filterMaxDuration) filterMaxDurationMinutes * 60 else -1
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
                            if (showDialog.value) AutoDownloadFilterDialog(feedToSet.autoDownloadFilter!!, ADLIncExc.INCLUDE, onDismiss = { showDialog.value = false }) { filter -> runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.autoDownloadFilter = filter } } } }
                            Text(text = stringResource(R.string.episode_inclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    //                    exclusive filter
                    Column(modifier = Modifier.padding(start = 20.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            val showDialog = remember { mutableStateOf(false) }
                            if (showDialog.value) AutoDownloadFilterDialog(feedToSet.autoDownloadFilter!!, ADLIncExc.EXCLUDE, onDismiss = { showDialog.value = false }) { filter -> runOnIOScope { realm.write { for (f in feedsToSet) { findLatest(f)?.autoDownloadFilter = filter } } } }
                            Text(text = stringResource(R.string.episode_exclusive_filters_label), style = CustomTextStyles.titleCustom, color = textColor, modifier = Modifier.clickable(onClick = { showDialog.value = true }))
                        }
                        Text(text = stringResource(R.string.episode_filters_description), style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                } else {
                    Column(modifier = Modifier.padding(start = 20.dp, bottom = 5.dp)) {
                        Text("Sorted by: " + stringResource(feedToSet.episodesSortOrderADL?.res ?: 0), modifier = Modifier.padding(start = 10.dp))
                        Text("Filtered by: ", modifier = Modifier.padding(start = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 20.dp)) {
                            feedToSet.episodeFilterADL.propertySet.forEach { FilterChip(onClick = { }, label = { Text(it) }, selected = false) }
                        }
                    }
                }
            }
        }
    }
}
