package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.fallbackSpeed
import ac.mdiq.podcini.preferences.AppPreferences.fastForwardSecs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.AppPreferences.rewindSecs
import ac.mdiq.podcini.preferences.AppPreferences.speedforwardSpeed
import ac.mdiq.podcini.preferences.AppPreferences.streamingCacheSizeMB
import ac.mdiq.podcini.preferences.AppPreferences.videoPlayMode
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.PlaybackSpeedDialog
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import ac.mdiq.podcini.ui.compose.VideoModeDialog
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.round

enum class PrefHardwareForwardButton(val res: Int, val res1: Int) {
    FF(R.string.button_action_fast_forward, R.string.keycode_media_fast_forward),
    RW(R.string.button_action_rewind, R.string.keycode_media_rewind),
    SKIP(R.string.button_action_skip_episode, R.string.keycode_media_next),
    START(R.string.button_action_restart_episode, R.string.keycode_media_previous);
}

@Composable
fun PlaybackPreferencesScreen(activity: PreferenceActivity) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val scrollState = rememberScrollState()
//    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
        Text(stringResource(R.string.interruptions), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        var prefUnpauseOnHeadsetReconnect by remember { mutableStateOf(getPref(AppPrefs.prefPauseOnHeadsetDisconnect, true)) }
        TitleSummarySwitchPrefRow(R.string.pref_pauseOnHeadsetDisconnect_title, R.string.pref_pauseOnDisconnect_sum, AppPrefs.prefPauseOnHeadsetDisconnect) {
            prefUnpauseOnHeadsetReconnect = it
            putPref(AppPrefs.prefPauseOnHeadsetDisconnect, it)
        }
        if (prefUnpauseOnHeadsetReconnect) {
            TitleSummarySwitchPrefRow(R.string.pref_unpauseOnHeadsetReconnect_title, R.string.pref_unpauseOnHeadsetReconnect_sum, AppPrefs.prefUnpauseOnHeadsetReconnect)
            TitleSummarySwitchPrefRow(R.string.pref_unpauseOnBluetoothReconnect_title, R.string.pref_unpauseOnBluetoothReconnect_sum, AppPrefs.prefUnpauseOnBluetoothReconnect)
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.playback_control), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_rewind), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NumberEditor(rewindSecs, modifier = Modifier.weight(0.6f)) { rewindSecs = it }
            }
            Text(stringResource(R.string.pref_rewind_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_fast_forward), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NumberEditor(fastForwardSecs, modifier = Modifier.weight(0.6f)) { fastForwardSecs = it }
            }
            Text(stringResource(R.string.pref_fast_forward_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        var showSpeedDialog by remember { mutableStateOf(false) }
        if (showSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = prefPlaybackSpeed, maxSpeed = 3f, isGlobal = true,
            onDismiss = { showSpeedDialog = false }) { speed -> putPref(AppPrefs.prefPlaybackSpeed, speed.toString()) }
        TitleSummaryActionColumn(R.string.playback_speed, R.string.pref_playback_speed_sum) { showSpeedDialog = true }
        var showFBSpeedDialog by remember { mutableStateOf(false) }
        if (showFBSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = fallbackSpeed, maxSpeed = 3f, isGlobal = true,
            onDismiss = { showFBSpeedDialog = false }) { speed ->
            Logd("PlaybackPreferencesScreen", "speed: $speed")
            val speed_ = when {
                speed < 0.0f -> 0.0f
                speed > 3.0f -> 3.0f
                else -> speed
            }
            fallbackSpeed = round(100 * speed_) / 100f
        }
        TitleSummaryActionColumn(R.string.pref_fallback_speed, R.string.pref_fallback_speed_sum) { showFBSpeedDialog = true }
        var showFFSpeedDialog by remember { mutableStateOf(false) }
        if (showFFSpeedDialog) PlaybackSpeedDialog(listOf(), initSpeed = speedforwardSpeed, maxSpeed = 10f, isGlobal = true,
            onDismiss = { showFFSpeedDialog = false }) { speed ->
            val speed_ = when {
                speed < 0.0f -> 0.0f
                speed > 10.0f -> 10.0f
                else -> speed
            }
            speedforwardSpeed = round(10 * speed_) / 10
        }
        TitleSummaryActionColumn(R.string.pref_speed_forward, R.string.pref_speed_forward_sum) { showFFSpeedDialog = true }
        TitleSummarySwitchPrefRow(R.string.pref_skip_silence_title, R.string.pref_skip_silence_sum, AppPrefs.prefSkipSilence)
        var prefStreaming by remember { mutableStateOf(getPref(AppPrefs.prefStreamOverDownload, false)) }
        TitleSummarySwitchPrefRow(R.string.pref_stream_over_download_title, R.string.pref_stream_over_download_sum, AppPrefs.prefStreamOverDownload) {
            prefStreaming = it
            putPref(AppPrefs.prefStreamOverDownload, it)
        }
        if (prefStreaming) Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_stream_cache), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NumberEditor(streamingCacheSizeMB, label = "MD", modifier = Modifier.weight(0.6f)) {
                    streamingCacheSizeMB = it
                    forceRestart()
                }
            }
            Text(stringResource(R.string.pref_stream_cache_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
        }

        if (gearbox.supportAudioQualities()) TitleSummarySwitchPrefRow(R.string.pref_low_quality_on_mobile_title, R.string.pref_low_quality_on_mobile_sum, AppPrefs.prefLowQualityOnMobile)
        TitleSummarySwitchPrefRow(R.string.pref_use_adaptive_progress_title, R.string.pref_use_adaptive_progress_sum, AppPrefs.prefUseAdaptiveProgressUpdate)
        var showVideoModeDialog by remember { mutableStateOf(false) }
        if (showVideoModeDialog) VideoModeDialog(initMode =  VideoMode.fromCode(videoPlayMode), onDismissRequest = { showVideoModeDialog = false }) { mode -> putPref(AppPrefs.prefVideoPlaybackMode, mode.code.toString()) }
        TitleSummaryActionColumn(R.string.pref_playback_video_mode, R.string.pref_playback_video_mode_sum) { showVideoModeDialog = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.reassign_hardware_buttons), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        var showHardwareForwardButtonOptions by remember { mutableStateOf(false) }
        var tempFFSelectedOption by remember { mutableIntStateOf(R.string.keycode_media_fast_forward) }
        TitleSummaryActionColumn(R.string.pref_hardware_forward_button_title, R.string.pref_hardware_forward_button_summary) { showHardwareForwardButtonOptions = true }
        if (showHardwareForwardButtonOptions) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showHardwareForwardButtonOptions = false },
                title = { Text(stringResource(R.string.pref_hardware_forward_button_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        PrefHardwareForwardButton.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempFFSelectedOption = option.res1 }) {
                                Checkbox(checked = tempFFSelectedOption == option.res1, onCheckedChange = { tempFFSelectedOption = option.res1 })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(AppPrefs.prefHardwareForwardButton, tempFFSelectedOption.toString())
                        showHardwareForwardButtonOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showHardwareForwardButtonOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        var showHardwarePreviousButtonOptions by remember { mutableStateOf(false) }
        var tempPRSelectedOption by remember { mutableIntStateOf(R.string.keycode_media_rewind) }
        TitleSummaryActionColumn(R.string.pref_hardware_previous_button_title, R.string.pref_hardware_previous_button_summary) { showHardwarePreviousButtonOptions = true }
        if (showHardwarePreviousButtonOptions) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showHardwarePreviousButtonOptions = false },
                title = { Text(stringResource(R.string.pref_hardware_previous_button_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        PrefHardwareForwardButton.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { tempPRSelectedOption = option.res1 }) {
                                Checkbox(checked = tempPRSelectedOption == option.res1, onCheckedChange = { tempPRSelectedOption = option.res1 })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(AppPrefs.prefHardwarePreviousButton, tempPRSelectedOption.toString())
                        showHardwarePreviousButtonOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showHardwarePreviousButtonOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.queue_label) + "/" + stringResource(R.string.episodes_label), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        TitleSummarySwitchPrefRow(R.string.pref_enqueue_downloaded_title, R.string.pref_enqueue_downloaded_summary, AppPrefs.prefEnqueueDownloaded)

        TitleSummarySwitchPrefRow(R.string.pref_followQueue_title, R.string.pref_followQueue_sum, AppPrefs.prefFollowQueue)
        TitleSummarySwitchPrefRow(R.string.pref_skip_keeps_episodes_title, R.string.pref_skip_keeps_episodes_sum, AppPrefs.prefSkipKeepsEpisode)
        TitleSummarySwitchPrefRow(R.string.pref_mark_played_removes_from_queue_title, R.string.pref_mark_played_removes_from_queue_sum, AppPrefs.prefRemoveFromQueueMarkedPlayed)

        TitleSummarySwitchPrefRow(R.string.auto_delete, R.string.pref_auto_delete_sum, AppPrefs.prefAutoDelete)
        var blockAutoDeleteLocal by remember { mutableStateOf(true) }
        TitleSummarySwitchPrefRow(R.string.pref_auto_local_delete_title, R.string.pref_auto_local_delete_sum, AppPrefs.prefAutoDeleteLocal) {
            if (blockAutoDeleteLocal && it) {
                commonConfirm = CommonConfirmAttrib(
                    title = "",
                    message = activity.getString(R.string.pref_auto_local_delete_dialog_body),
                    confirmRes = R.string.yes,
                    cancelRes = R.string.cancel_label,
                    onConfirm = {
                        blockAutoDeleteLocal = false
                        putPref(AppPrefs.prefAutoDeleteLocal, it)
                        blockAutoDeleteLocal = true
                    })
            }
        }
        TitleSummarySwitchPrefRow(R.string.pref_keeps_important_episodes_title, R.string.pref_keeps_important_episodes_sum, AppPrefs.prefFavoriteKeepsEpisode)
        TitleSummarySwitchPrefRow(R.string.pref_delete_removes_from_queue_title, R.string.pref_delete_removes_from_queue_sum, AppPrefs.prefDeleteRemovesFromQueue)
    }
}
