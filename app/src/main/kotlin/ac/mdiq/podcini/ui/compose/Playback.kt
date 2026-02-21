package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.SleepTimer.autoEnableFrom
import ac.mdiq.podcini.config.settings.SleepTimer.autoEnableTo
import ac.mdiq.podcini.config.settings.SleepTimer.lastTimerValue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.tempSkipSilence
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curPBSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isFallbackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isSpeedForward
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefPlaybackSpeed
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.shouldRepeat
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.fallbackSpeed
import ac.mdiq.podcini.storage.database.fastForwardSecs
import ac.mdiq.podcini.storage.database.isSkipSilence
import ac.mdiq.podcini.storage.database.rewindSecs
import ac.mdiq.podcini.storage.database.skipforwardSpeed
import ac.mdiq.podcini.storage.database.sleepPrefs
import ac.mdiq.podcini.storage.database.speedforwardSpeed
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.view.Gravity
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.round

private fun speed2Slider(speed: Float, maxSpeed: Float): Float {
    return if (speed < 1) (speed - 0.1f) / 1.8f else (speed - 2f + maxSpeed) / 2 / (maxSpeed - 1f)
}
private fun slider2Speed(slider: Float, maxSpeed: Float): Float {
    return if (slider < 0.5) 1.8f * slider + 0.1f else 2 * (maxSpeed - 1f) * slider + 2f - maxSpeed
}
@Composable
private fun SpeedSetter(initSpeed: Float, maxSpeed: Float, speedCB: (Float) -> Unit) {
    //    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    //        Text(text = String.format(Locale.getDefault(), "%.2fx", speed))
    //    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        var speed by remember { mutableFloatStateOf(initSpeed) }
        var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
        val stepSize = 0.05f
        Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = {
                val speed_ = round(speed / stepSize) * stepSize - stepSize
                if (speed_ >= 0.1f) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            }))
        Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(5.dp).padding(start = 20.dp, end = 20.dp),
            onValueChange = {
                sliderPosition = it
                speed = slider2Speed(sliderPosition, maxSpeed)
                speedCB(speed)
            })
        Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = {
                val speed_ = round(speed / stepSize) * stepSize + stepSize
                if (speed_ <= maxSpeed) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            }))
        Spacer(Modifier.width(40.dp))
        Text(text = String.format(Locale.getDefault(), "%.2fx", speed))
    }
}


@Composable
fun PlaybackSpeedDialog(feeds: List<Feed>, initSpeed: Float, maxSpeed: Float, isGlobal: Boolean = false, onDismiss: () -> Unit, speedCB: (Float) -> Unit) {
    // min speed set to 0.1 and max speed at 10
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column {
                Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                var speed by remember { mutableFloatStateOf(initSpeed) }
                //                var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
                var useGlobal by remember { mutableStateOf(!isGlobal && speed == SPEED_USE_GLOBAL) }
                if (!isGlobal) Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useGlobal, onCheckedChange = { isChecked ->
                        useGlobal = isChecked
                        speed = when {
                            useGlobal -> SPEED_USE_GLOBAL
                            feeds.size == 1 -> {
                                if (feeds[0].playSpeed == SPEED_USE_GLOBAL) prefPlaybackSpeed
                                else feeds[0].playSpeed
                            }
                            else -> 1f
                        }
                        //                        if (!useGlobal) sliderPosition = speed2Slider(speed, maxSpeed)
                    })
                    Text(stringResource(R.string.global_default))
                }
                if (!useGlobal) SpeedSetter(initSpeed, maxSpeed) { speed = it }
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                    Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val newSpeed = if (useGlobal) SPEED_USE_GLOBAL else speed
                        speedCB(newSpeed)
                        onDismiss()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackSpeedFullDialog(settingCode: BooleanArray, indexDefault: Int, maxSpeed: Float, onDismiss: () -> Unit) {
    val TAG = "PlaybackSpeedFullDialog"
    fun readPlaybackSpeedArray(valueFromPrefs: String?): List<Float> {
        if (valueFromPrefs != null) {
            try {
                val jsonArray = JSONArray(valueFromPrefs)
                val selectedSpeeds: MutableList<Float> = mutableListOf()
                for (i in 0 until jsonArray.length()) selectedSpeeds.add(jsonArray.getDouble(i).toFloat())
                return selectedSpeeds
            } catch (e: JSONException) { Logs(TAG, e, "Got JSON error when trying to get speeds from JSONArray") }
        }
        return mutableListOf(1.0f, 1.25f, 1.5f)
    }
    fun setPlaybackSpeedArray(speeds: List<Float>) {
        val format = DecimalFormatSymbols(Locale.US)
        format.decimalSeparator = '.'
        val speedFormat = DecimalFormat("0.00", format)
        val jsonArray = JSONArray()
        for (speed in speeds) jsonArray.put(speedFormat.format(speed.toDouble()))
        upsertBlk(appPrefs) { it.playbackSpeedArray = jsonArray.toString()}
    }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Card(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 10.dp, bottom = 10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                var speed by remember { mutableFloatStateOf(curPBSpeed) }
                val speeds = remember { readPlaybackSpeedArray(appPrefs.playbackSpeedArray).toMutableStateList() }
                var showEdit by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(50.dp))
                    if (showEdit) FilterChip(onClick = {
                        if (speed !in speeds) {
                            speeds.add(speed)
                            speeds.sort()
                            setPlaybackSpeedArray(speeds)
                        } }, label = { Text(String.format(Locale.getDefault(), "%.2f", speed)) }, selected = false,
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon", modifier = Modifier.size(FilterChipDefaults.IconSize)) })
                    else IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit preset") }
                }
                if (showEdit) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
                    val stepSize = 0.05f
                    Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize - stepSize
                            if (speed_ >= 0.1f) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        }))
                    Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(10.dp).padding(start = 20.dp, end = 20.dp),
                        onValueChange = {
                            sliderPosition = it
                            speed = slider2Speed(sliderPosition, maxSpeed)
                            Logd("PlaybackSpeedDialog", "slider value: $it $speed}")
                        })
                    Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = {
                            val speed_ = round(speed / stepSize) * stepSize + stepSize
                            if (speed_ <= maxSpeed) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        }))
                }
                var forCurrent by remember { mutableStateOf(indexDefault == 0) }
                var forPodcast by remember { mutableStateOf(indexDefault == 1) }
                var forGlobal by remember { mutableStateOf(indexDefault == 2) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forCurrent, onCheckedChange = { isChecked -> forCurrent = isChecked })
                    Text(stringResource(R.string.current_episode))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forPodcast, onCheckedChange = { isChecked -> forPodcast = isChecked })
                    Text(stringResource(R.string.current_podcast))
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = forGlobal, onCheckedChange = { isChecked -> forGlobal = isChecked })
                    Text(stringResource(R.string.global))
                    Spacer(Modifier.weight(1f))
                }
                FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    speeds.forEach { chipSpeed ->
                        FilterChip(onClick = {
                            Logd("VariableSpeedDialog", "holder.chip settingCode0: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            settingCode[0] = forCurrent
                            settingCode[1] = forPodcast
                            settingCode[2] = forGlobal
                            Logd("VariableSpeedDialog", "holder.chip settingCode: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                            if (playbackService != null) {
                                isSpeedForward = false
                                isFallbackSpeed = false
                                if (settingCode.size == 3) {
                                    Logd(TAG, "setSpeed codeArray: ${settingCode[0]} ${settingCode[1]} ${settingCode[2]}")
                                    if (settingCode[2]) upsertBlk(appPrefs) { it.playbackSpeed = chipSpeed.toString() }
                                    if (settingCode[1] && curEpisode?.feed != null) upsertBlk(curEpisode!!.feed!!) { it.playSpeed = chipSpeed }
                                    if (settingCode[0]) {
                                        curTempSpeed = chipSpeed
                                        mPlayer?.setPlaybackParams(chipSpeed)
                                    }
                                } else {
                                    curTempSpeed = chipSpeed
                                    mPlayer?.setPlaybackParams(chipSpeed)
                                }
                            }
                            else {
                                upsertBlk(appPrefs) { it.playbackSpeed = chipSpeed.toString() }
                                EventFlow.postEvent(FlowEvent.SpeedChangedEvent(chipSpeed))
                            }
                            onDismiss()
                        }, label = { Text(String.format(Locale.getDefault(), "%.2f", chipSpeed)) }, selected = false,
                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                                modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                                    speeds.remove(chipSpeed)
                                    setPlaybackSpeedArray(speeds)
                                })) })
                    }
                }
                var showMore by remember { mutableStateOf(false) }
                TextButton(onClick = { showMore = !showMore }) { Text("More>>", style = MaterialTheme.typography.headlineSmall) }
                if (showMore) {
                    Text(stringResource(R.string.pref_skip_silence_title), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        var tmpChecked by remember(curEpisode?.id) { mutableStateOf(tempSkipSilence) }
                        Checkbox(checked = tmpChecked?: false, onCheckedChange = { isChecked ->
                            tmpChecked = isChecked
                            tempSkipSilence = isChecked
                            mPlayer?.setSkipSilence()
                        })
                        Text(stringResource(R.string.current_episode))
                        var feedChecked by remember(curEpisode?.id) { mutableStateOf(curEpisode!!.feed?.skipSilence?: false) }
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = feedChecked, onCheckedChange = { isChecked ->
                            feedChecked = isChecked
                            if (curEpisode?.feed != null) upsertBlk(curEpisode!!.feed!!) { it.skipSilence = isChecked }
                        })
                        Text(stringResource(R.string.current_podcast))
                        Spacer(Modifier.weight(1f))
                        var glChecked by remember { mutableStateOf(isSkipSilence) }
                        Checkbox(checked = glChecked, onCheckedChange = { isChecked ->
                            glChecked = isChecked
                            isSkipSilence = isChecked
                        })
                        Text(stringResource(R.string.global))
                        Spacer(Modifier.weight(1f))
                    }
                    HorizontalDivider(thickness = 5.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shouldRepeat, onCheckedChange = { isChecked ->
                            shouldRepeat = isChecked
                            mPlayer?.setRepeat(isChecked)
                        })
                        Text(stringResource(R.string.repeat_current_media))
                    }
                    HorizontalDivider(thickness = 5.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_rewind), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        NumberEditor(rewindSecs, modifier = Modifier.weight(0.6f)) { rewindSecs = it }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pref_fast_forward), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        NumberEditor(fastForwardSecs, modifier = Modifier.weight(0.6f)) { fastForwardSecs = it }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Text(stringResource(R.string.pref_fallback_speed), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    SpeedSetter(fallbackSpeed, maxSpeed = 3f) {
                        val speed_ = when {
                            it < 0.0f -> 0.0f
                            it > 3.0f -> 3.0f
                            else -> it
                        }
                        fallbackSpeed = round(100 * speed_) / 100f
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Text(stringResource(R.string.pref_speed_forward), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    SpeedSetter(speedforwardSpeed, maxSpeed = 10f) {
                        val speed_ = when {
                            it < 0.0f -> 0.0f
                            it > 10.0f -> 10.0f
                            else -> it
                        }
                        speedforwardSpeed = round(10 * speed_) / 10
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
                    Text(stringResource(R.string.pref_speed_skip), style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    SpeedSetter(skipforwardSpeed, maxSpeed = 10f) {
                        val speed_ = when {
                            it < 0.0f -> 0.0f
                            it > 10.0f -> 10.0f
                            else -> it
                        }
                        skipforwardSpeed = round(10 * speed_) / 10
                    }
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val TAG = "SleepTimerDialog"

    val lcScope = rememberCoroutineScope()
    val timeLeft by remember { mutableLongStateOf(sleepManager?.sleepTimerTimeLeft?:0) }
    var showTimeDisplay by remember { mutableStateOf(false) }
    var showTimeSetup by remember { mutableStateOf(true) }
    var timerText by remember { mutableStateOf(durationStringFull(timeLeft.toInt())) }
    val context by rememberUpdatedState(LocalContext.current)

    var eventSink: Job? by remember { mutableStateOf(null) }
    fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lcScope.launch {
            EventFlow.events.collectLatest { event ->
                when (event) {
                    is FlowEvent.SleepTimerUpdatedEvent -> {
                        showTimeDisplay = !event.isOver && !event.isCancelled
                        showTimeSetup = event.isOver || event.isCancelled
                        timerText = durationStringFull(event.getTimeLeft().toInt())
                    }
                    else -> {}
                }
            }
        }
    }

    LaunchedEffect(Unit) { procFlowEvents() }
    DisposableEffect(Unit) { onDispose { cancelFlowEvents() } }

    var toEnd by remember { mutableStateOf(false) }
    var etxtTime by remember { mutableStateOf(lastTimerValue.toString()) }
    fun extendSleepTimer(extendTime: Long) {
        val timeLeft = sleepManager?.sleepTimerTimeLeft ?: Episode.INVALID_TIME.toLong()
        if (timeLeft != Episode.INVALID_TIME.toLong()) sleepManager?.setSleepTimer(timeLeft + extendTime)
    }

    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sleep_timer_label)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (showTimeSetup) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                        Checkbox(checked = toEnd, onCheckedChange = { toEnd = it })
                        Text(stringResource(R.string.end_episode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                    }
                    if (!toEnd) TextField(value = etxtTime, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(stringResource(R.string.time_minutes)) }, singleLine = true,
                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) etxtTime = it })
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (!PlaybackService.isRunning) {
                            Logt(TAG, context.getString(R.string.no_media_playing_label))
                            return@Button
                        }
                        try {
                            val time = if (!toEnd) etxtTime.toLong() else TimeUnit.MILLISECONDS.toMinutes((max(((curEpisode?.duration ?: 0) - (curEpisode?.position ?: 0)), 0) / curPBSpeed).toLong()) // ms to minutes
                            Logd("SleepTimerDialog", "Sleep timer set: $time")
                            if (time == 0L) throw NumberFormatException("Timer must not be zero")
                            upsertBlk(sleepPrefs) { it.LastValue = time }
                            sleepManager?.setSleepTimer(TimeUnit.MINUTES.toMillis(lastTimerValue))
                            showTimeSetup = false
                            showTimeDisplay = true
                            //                        closeKeyboard(content)
                        } catch (e: NumberFormatException) { Logs(TAG, e, context.getString(R.string.time_dialog_invalid_input)) }
                    }) { Text(stringResource(R.string.set_sleeptimer_label)) }
                }
                if (showTimeDisplay || timeLeft > 0) {
                    Text(timerText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { sleepManager?.disableSleepTimer() }) { Text(stringResource(R.string.disable_sleeptimer_label)) }
                    Row {
                        Button(onClick = { extendSleepTimer((10 * 1000 * 60).toLong()) }) { Text(stringResource(R.string.extend_sleep_timer_label, 10)) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { extendSleepTimer((30 * 1000 * 60).toLong()) }) { Text(stringResource(R.string.extend_sleep_timer_label, 30)) }
                    }
                }
                var cbShakeToReset by remember { mutableStateOf(sleepPrefs.ShakeToReset) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Checkbox(checked = cbShakeToReset, onCheckedChange = { it0 ->
                        cbShakeToReset = it0
                        upsertBlk(sleepPrefs) { it.ShakeToReset = it0 }
                    })
                    Text(stringResource(R.string.shake_to_reset_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                }
                var cbVibrate by remember { mutableStateOf(sleepPrefs.Vibrate) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Checkbox(checked = cbVibrate, onCheckedChange = { it0 ->
                        cbVibrate = it0
                        upsertBlk(sleepPrefs) { it.Vibrate = it0 }
                    })
                    Text(stringResource(R.string.timer_vibration_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                }
                var chAutoEnable by remember { mutableStateOf(sleepPrefs.AutoEnable) }
                var enableChangeTime by remember { mutableStateOf(chAutoEnable) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
                    Checkbox(checked = chAutoEnable, onCheckedChange = { it0 ->
                        chAutoEnable = it0
                        upsertBlk(sleepPrefs) { it.AutoEnable = it0 }
                        enableChangeTime = it0
                    })
                    Text(stringResource(R.string.auto_enable_label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                }
                if (enableChangeTime) {
                    var from by remember { mutableStateOf(autoEnableFrom.toString()) }
                    var to by remember { mutableStateOf(autoEnableTo.toString()) }
                    Text(stringResource(R.string.auto_enable_sum), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
                        TextField(value = from, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 8.dp),
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) from = it })
                        TextField(value = to, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f).padding(end = 8.dp),
                            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) to = it })
                        IconButton(onClick = {
                            upsertBlk(sleepPrefs) {
                                it.AutoEnableFrom = from.toInt()
                                it.AutoEnableTo = to.toInt()
                            }
                        }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "setting") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.close_label)) } }
    )
}
