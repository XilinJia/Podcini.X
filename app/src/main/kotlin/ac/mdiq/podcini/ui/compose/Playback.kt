package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.base.SleepManager.Companion.autoEnableFrom
import ac.mdiq.podcini.playback.base.SleepManager.Companion.autoEnableTo
import ac.mdiq.podcini.playback.base.SleepManager.Companion.lastTimerValue
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
import ac.mdiq.podcini.utils.format
import ac.mdiq.podcini.utils.formatNumberKmp
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONException
import kotlin.math.max
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private fun speed2Slider(speed: Float, maxSpeed: Float): Float {
    return if (speed < 1) (speed - 0.1f) / 1.8f else (speed - 2f + maxSpeed) / 2 / (maxSpeed - 1f)
}
private fun slider2Speed(slider: Float, maxSpeed: Float): Float {
    return if (slider < 0.5) 1.8f * slider + 0.1f else 2 * (maxSpeed - 1f) * slider + 2f - maxSpeed
}
@Composable
private fun SpeedSetter(initSpeed: Float, maxSpeed: Float, speedCB: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        var speed by remember { mutableFloatStateOf(initSpeed) }
        var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
        val stepSize = 0.05f
        Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                val speed_ = round(speed / stepSize) * stepSize - stepSize
                if (speed_ >= 0.1f) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            })
        Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(5.dp).padding(start = 20.dp, end = 20.dp),
            onValueChange = {
                sliderPosition = it
                speed = slider2Speed(sliderPosition, maxSpeed)
                speedCB(speed)
            })
        Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable {
                val speed_ = round(speed / stepSize) * stepSize + stepSize
                if (speed_ <= maxSpeed) {
                    speed = speed_
                    sliderPosition = speed2Slider(speed, maxSpeed)
                    speedCB(speed)
                }
            })
        Spacer(Modifier.width(40.dp))
        Text(text = speed.format(2)+"x")
    }
}


@Composable
fun PlaybackSpeedDialog(feeds: List<Feed>, initSpeed: Float, maxSpeed: Float, isGlobal: Boolean = false, onDismiss: () -> Unit, speedCB: (Float) -> Unit) {
    // min speed set to 0.1 and max speed at 10
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, borderColor)) {
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
                                if (feeds[0].playSpeed == SPEED_USE_GLOBAL) appPrefs.playbackSpeed
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
fun PlaybackSpeedFullDialog(playerId: Int, indexDefault: Int, maxSpeed: Float, onDismiss: () -> Unit) {
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
        val jsonArray = JSONArray()
        for (speed in speeds) jsonArray.put(formatNumberKmp(speed.toDouble()))
        upsertBlk(appPrefs) { it.playbackSpeedArray = jsonArray.toString()}
    }
    Dialog(properties = DialogProperties(usePlatformDefaultWidth = false), onDismissRequest = onDismiss) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.BOTTOM)
        Card(modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 10.dp, bottom = 10.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, borderColor)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                var speed by remember { mutableFloatStateOf(theatres[playerId].mPlayer?.curPBSpeed?:0f) }
                val speeds = remember { readPlaybackSpeedArray(appPrefs.playbackSpeedArray).toMutableStateList() }
                var showEdit by remember { mutableStateOf(false) }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.playback_speed), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(50.dp))
                    if (showEdit) FilterChip(label = { Text(speed.format(2)) }, selected = false,
                        onClick = {
                            if (speed !in speeds) {
                                speeds.add(speed)
                                speeds.sort()
                                setPlaybackSpeedArray(speeds)
                            } },
                        trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon", modifier = Modifier.size(FilterChipDefaults.IconSize)) })
                    else IconButton(onClick = { showEdit = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit preset") }
                }
                if (showEdit) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    var sliderPosition by remember { mutableFloatStateOf(speed2Slider(if (speed == SPEED_USE_GLOBAL) 1f else speed, maxSpeed)) }
                    val stepSize = 0.05f
                    Text("-", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val speed_ = round(speed / stepSize) * stepSize - stepSize
                            if (speed_ >= 0.1f) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        })
                    Slider(value = sliderPosition, modifier = Modifier.weight(1f).height(10.dp).padding(start = 20.dp, end = 20.dp),
                        onValueChange = {
                            sliderPosition = it
                            speed = slider2Speed(sliderPosition, maxSpeed)
                            Logd("PlaybackSpeedDialog", "slider value: $it $speed}")
                        })
                    Text("+", fontSize = MaterialTheme.typography.headlineLarge.fontSize, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val speed_ = round(speed / stepSize) * stepSize + stepSize
                            if (speed_ <= maxSpeed) {
                                speed = speed_
                                sliderPosition = speed2Slider(speed, maxSpeed)
                            }
                        })
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
                        FilterChip(label = { Text(chipSpeed.format(2)) }, selected = false,
                            onClick = {
                                if (playbackService != null) {
                                    theatres[playerId].mPlayer?.isSpeedForward = false
                                    theatres[playerId].mPlayer?.isFallbackSpeed = false
                                    if (forGlobal) upsertBlk(appPrefs) { it.playbackSpeed = chipSpeed }
                                    if (forPodcast && theatres[playerId].mPlayer?.curEpisode?.feed != null) upsertBlk(theatres[playerId].mPlayer?.curEpisode!!.feed!!) { it.playSpeed = chipSpeed }
                                    if (forCurrent) {
                                        theatres[playerId].mPlayer?.curSpeed = chipSpeed
                                        theatres[playerId].mPlayer?.setPlaybackParams(chipSpeed)
                                    }
                                }
                                else {
                                    upsertBlk(appPrefs) { it.playbackSpeed = chipSpeed }
                                    EventFlow.postEvent(FlowEvent.SpeedChangedEvent(playerId, chipSpeed))
                                }
                                onDismiss()
                            },
                            trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon", modifier = Modifier.size(30.dp).padding(start = 3.dp).clickable {
                                speeds.remove(chipSpeed)
                                setPlaybackSpeedArray(speeds)
                            }) })
                    }
                }
                var showMore by remember { mutableStateOf(false) }
                TextButton(onClick = { showMore = !showMore }) { Text("More>>", style = MaterialTheme.typography.headlineSmall) }
                if (showMore) {
                    Text(stringResource(R.string.playback_pitch), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                    var tmpPitch by remember(theatres[playerId].mPlayer?.curEpisode?.id) { mutableStateOf(true) }
                    var feedPitch by remember(theatres[playerId].mPlayer?.curEpisode?.id) { mutableStateOf(false) }
                    var glPitch by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = tmpPitch, onCheckedChange = { isChecked -> tmpPitch = isChecked })
                        Text(stringResource(R.string.current_episode))
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = feedPitch, onCheckedChange = { isChecked -> feedPitch = isChecked })
                        Text(stringResource(R.string.current_podcast))
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = glPitch, onCheckedChange = { isChecked -> glPitch = isChecked })
                        Text(stringResource(R.string.global))
                        Spacer(Modifier.weight(1f))
                    }
                    var pitchStr by remember { mutableStateOf((theatres[playerId].mPlayer?.curPitch?:1f).toString()) }
                    var showSet by remember { mutableStateOf(false) }
                    var unit by remember { mutableStateOf("Ratio") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(value = pitchStr, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text("float", style = MaterialTheme.typography.bodySmall) }, singleLine = true, modifier = Modifier.width(100.dp),
                            onValueChange = {
                                val value = it.toFloatOrNull()
                                if (it.isEmpty() || value != null) pitchStr = it
                                if (value != null && value > 0f) {
                                    if ((unit == "Hz" && value > 50) || (unit == "Ratio" && value < 10)) showSet = true
                                }
                            },
                            trailingIcon = { if (showSet) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).clickable {
                                val pitch = if (unit == "Ratio") pitchStr.toFloat() else pitchStr.toFloat() / 440f
                                Logd(TAG, "pitch set to $pitch")
                                if (tmpPitch) {
                                    theatres[playerId].mPlayer?.curPitch = pitch
                                    theatres[playerId].mPlayer?.setPlaybackParams(theatres[playerId].mPlayer!!.curSpeed, pitch)
                                }
                                if (feedPitch) upsertBlk(theatres[playerId].mPlayer?.curEpisode!!.feed!!) { it.playPitch = pitch }
                                if (glPitch) upsertBlk(appPrefs) { it.playbackPitch = pitch }
                            }) }
                        )
                        Checkbox(checked = unit == "Hz", onCheckedChange = { unit = "Hz" })
                        Text(stringResource(R.string.hz))
                        Checkbox(checked = unit == "Ratio", onCheckedChange = { unit = "Ratio" })
                        Text(stringResource(R.string.ratio))
                    }
                    HorizontalDivider(thickness = 5.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))

                    Text(stringResource(R.string.pref_skip_silence_title), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        var tmpChecked by remember(theatres[playerId].mPlayer?.curEpisode?.id) { mutableStateOf(theatres[playerId].mPlayer?.skipSilence) }
                        Checkbox(checked = tmpChecked?: false, onCheckedChange = { isChecked ->
                            tmpChecked = isChecked
                            theatres[playerId].mPlayer?.skipSilence = isChecked
                            theatres[playerId].mPlayer?.setSkipSilence()
                        })
                        Text(stringResource(R.string.current_episode))
                        var feedChecked by remember(theatres[playerId].mPlayer?.curEpisode?.id) { mutableStateOf(theatres[playerId].mPlayer?.curEpisode!!.feed?.skipSilence?: false) }
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = feedChecked, onCheckedChange = { isChecked ->
                            feedChecked = isChecked
                            if (theatres[playerId].mPlayer?.curEpisode?.feed != null) upsertBlk(theatres[playerId].mPlayer?.curEpisode!!.feed!!) { it.skipSilence = isChecked }
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
                        Checkbox(checked = theatres[playerId].mPlayer?.shouldRepeat == true, onCheckedChange = { isChecked ->
                            theatres[playerId].mPlayer?.shouldRepeat = isChecked
                            theatres[playerId].mPlayer?.setRepeat(isChecked)
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

    val timeLeft by remember { mutableLongStateOf(sleepManager?.timeLeft?:0) }
    var showTimeDisplay by remember { mutableStateOf(false) }
    var showTimeSetup by remember { mutableStateOf(true) }
    var timerText by remember { mutableStateOf(durationStringFull(timeLeft.toInt())) }
    val context by rememberUpdatedState(LocalContext.current)

    LaunchedEffect(Unit) {
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

    var toEnd by remember { mutableStateOf(false) }
    var etxtTime by remember { mutableStateOf(lastTimerValue.toString()) }
    fun extendSleepTimer(extendTime: Long) {
        val timeLeft = sleepManager?.timeLeft ?: Episode.INVALID_TIME.toLong()
        if (timeLeft != Episode.INVALID_TIME.toLong()) sleepManager?.setTimer(timeLeft + extendTime)
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
                        if (theatres[0].mPlayer?.curEpisode == null) return@Button
                        if (!PlaybackService.isRunning) {
                            Logt(TAG, context.getString(R.string.no_media_playing_label))
                            return@Button
                        }
                        try {
                            val time = if (!toEnd) etxtTime.toLong() else (max(((theatres[0].mPlayer!!.curEpisode!!.duration) - (theatres[0].mPlayer!!.curEpisode!!.position)), 0) / theatres[0].mPlayer!!.curPBSpeed).toLong().milliseconds.inWholeMinutes // ms to minutes
                            Logd("SleepTimerDialog", "Sleep timer set: $time")
                            if (time == 0L) throw NumberFormatException("Timer must not be zero")
                            upsertBlk(sleepPrefs) { it.LastValue = time }
                            sleepManager?.setTimer(lastTimerValue.minutes.inWholeMilliseconds)
                            showTimeSetup = false
                            showTimeDisplay = true
                            //                        closeKeyboard(content)
                        } catch (e: NumberFormatException) { Logs(TAG, e, context.getString(R.string.time_dialog_invalid_input)) }
                    }) { Text(stringResource(R.string.set_sleeptimer_label)) }
                }
                if (showTimeDisplay || timeLeft > 0) {
                    Text(timerText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { sleepManager?.disable() }) { Text(stringResource(R.string.disable_sleeptimer_label)) }
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
