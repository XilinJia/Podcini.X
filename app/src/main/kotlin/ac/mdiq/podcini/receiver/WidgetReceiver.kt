package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.EpisodeInfoActivity
import ac.mdiq.podcini.activity.MainActivity
import ac.mdiq.podcini.activity.PlayerUIActivity
import ac.mdiq.podcini.activity.QueuePickerActivity
import ac.mdiq.podcini.activity.starter.MainActivityStarter
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.playback.base.InTheatre.ensureAController

import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isCasting
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.isRunning
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.playbackService
import ac.mdiq.podcini.storage.database.episodeById
import ac.mdiq.podcini.storage.database.fastForwardSecs
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.rewindSecs
import ac.mdiq.podcini.storage.database.smartRemoveFromQueues
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.WidgetEpisode
import ac.mdiq.podcini.storage.model.toWidget
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VideoMode
import ac.mdiq.podcini.storage.utils.durationStringFull
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.formatDateTimeFlex
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val TAG = "WidgetReceiver"

class WidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PodciniWidget()
}

val episodesJson = stringPreferencesKey("episodes_json")


class PodciniWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    val brColorProvider = ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))
    val textColorProvider = ColorProvider(day = Color(0xFFE0D7C1), night = Color(0xFFE0D7C1))
    val buttonColorProvider = ColorProvider(day = Color(0xDDFFD700), night = Color(0xDDFFD700))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
//        Logd(TAG, "provideGlance id: $id actQueue ${actQueue.name}")

        var episodes: List<WidgetEpisode> = listOf()

        provideContent { GlanceTheme {
            Logd(TAG, "provideGlance in provideContent id: $id")

            val prefs = currentState<Preferences>()

            // these unfortunately have to be reset every time
            var markedId = prefs[MARKED_EPISODE_KEY]
            var queueName = prefs [stringPreferencesKey("queue_name")] ?: "Default"
            var queueId = prefs[longPreferencesKey("queue_id")] ?: 0L
            var queueSize = prefs[intPreferencesKey("queue_size")] ?: 0

            val updateYpe = prefs[stringPreferencesKey("update_type")] ?: ""
            Logd(TAG, "provideGlance updateYpe: $updateYpe")

            when (updateYpe) {
                "update" -> {
                    val json = prefs[stringPreferencesKey("episodes")] ?: "[]"
                    episodes = Json.decodeFromString<List<WidgetEpisode>>(json)
                }
                "episode" -> markedId = prefs[MARKED_EPISODE_KEY]
                "queue" -> {
                    queueId = prefs[longPreferencesKey("queue_id")] ?: 0L
                    queueName = prefs [stringPreferencesKey("queue_name")] ?: "Default"
                    queueSize = prefs[intPreferencesKey("queue_size")] ?: 0
                    val json = prefs[stringPreferencesKey("episodes")] ?: "[]"
                    episodes = Json.decodeFromString<List<WidgetEpisode>>(json)
                    Logd(TAG, "provideGlance episodes: ${episodes.size}")
                }
            }

            Column(modifier = GlanceModifier.fillMaxSize().background(brColorProvider).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = GlanceModifier.clickable(actionStartActivity(Intent(context, QueuePickerActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }),
                        rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple))) {
                        Text(text = ("$queueName: $queueSize"), style = TextStyle(color = textColorProvider, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Image(provider = ImageProvider(R.drawable.ic_launcher_foreground), contentDescription = "App", modifier = GlanceModifier.size(48.dp).clickable(actionStartActivity<MainActivity>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Spacer(GlanceModifier.width(10.dp))
                    Image(provider = ImageProvider(R.drawable.ic_refresh), contentDescription = "Refresh", colorFilter = ColorFilter.tint(textColorProvider), modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<RefreshAction>(parameters = actionParametersOf(QUEUE_ID_KEY to queueId)), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                }
                LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(episodes) { episode ->
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(provider = ImageProvider(R.drawable.ic_close_white), contentDescription = "remove", colorFilter = ColorFilter.tint(buttonColorProvider),
                                modifier = GlanceModifier.size(36.dp).clickable(actionRunCallback<RemoveAction>(parameters = actionParametersOf(EPISODE_ID_KEY to episode.id, QUEUE_ID_KEY to queueId)), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                            val isMarked = episode.id == markedId || episode.id == theatres[0].mPlayer?.curEpisode?.id
                            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<EpisodeInfoActivity>(parameters = actionParametersOf(EPISODE_INFO_ID_KEY to episode.id)), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple))) {
                                Text(episode.t ?: "", style = TextStyle(color = textColorProvider, fontSize = 13.sp, fontWeight = if (isMarked) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(provider = ImageProvider(EpisodeState.fromCode(episode.s).res), contentDescription = "playState", colorFilter = ColorFilter.tint(buttonColorProvider), modifier = GlanceModifier.size(16.dp))
                                    if (episode.r != Rating.UNRATED.code) Image(provider = ImageProvider(Rating.fromCode(episode.r).res), contentDescription = "rating", colorFilter = ColorFilter.tint(buttonColorProvider), modifier = GlanceModifier.size(16.dp))
                                    val dateSizeText = " · " + formatDateTimeFlex(episode.pd) + " · " + durationStringFull(episode.du)
                                    Text(dateSizeText, style = TextStyle(color = textColorProvider, fontSize = 10.sp), maxLines = 1)
                                }
                            }
                            Image(provider = ImageProvider(R.drawable.outline_play_pause_24), contentDescription = "Play/pause", colorFilter = ColorFilter.tint(if (isMarked) textColorProvider else buttonColorProvider),
                                modifier = GlanceModifier.size(48.dp).clickable(actionRunCallback<PlayAction>(parameters = actionParametersOf(EPISODE_ID_KEY to episode.id)), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                        }
                    }
                }
                val buttonSize = 60.dp
                Row(modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 5.dp),  horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(provider = ImageProvider(R.drawable.outline_smart_display_24), contentDescription = "PlayerUI", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).padding(end = 5.dp).clickable(actionStartActivity<PlayerUIActivity>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Image(provider = ImageProvider(R.drawable.baseline_skip_previous_24), contentDescription = "Restart", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).padding(end = 5.dp).clickable(actionRunCallback<PrevAction>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Image(provider = ImageProvider(R.drawable.ic_fast_rewind), contentDescription = "Rewind", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).padding(end = 5.dp).clickable(actionRunCallback<RewindAction>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Image(provider = ImageProvider(R.drawable.outline_play_pause_24), contentDescription = "Play/pause", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).padding(end = 5.dp).clickable(actionRunCallback<ToggleAction>(if (episodes.isNotEmpty()) actionParametersOf(EPISODE_ID_KEY to episodes[0].id) else actionParametersOf()), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Image(provider = ImageProvider(R.drawable.ic_fast_forward), contentDescription = "Forward", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).padding(end = 5.dp).clickable(actionRunCallback<ForwardAction>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                    Image(provider = ImageProvider(R.drawable.ic_skip_48dp), contentDescription = "Skip", colorFilter = ColorFilter.tint(buttonColorProvider),
                        modifier = GlanceModifier.size(buttonSize).clickable(actionRunCallback<NextAction>(), rippleOverride = R.drawable.widget_ripple).background(ImageProvider(R.drawable.widget_ripple)))
                }
            }
        } }
    }
}

val EPISODE_INFO_ID_KEY = ActionParameters.Key<Long>("episode_info_id")
val EPISODE_ID_KEY = ActionParameters.Key<Long>("episode_id")
val QUEUE_ID_KEY = ActionParameters.Key<Long>("queue_id")

val MARKED_EPISODE_KEY = longPreferencesKey("marked_episode_id")

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "RefreshAction onAction")
        val queueId = parameters[QUEUE_ID_KEY]
        if (queueId == null) {
            Loge("RefreshAction", "queueId from parameter is null.")
            return
        }
        val episodes = withContext(Dispatchers.IO) {
            val queue = realm.query(PlayQueue::class).query("id == $queueId").first().find()
            queue?.episodesSorted?.take(40)?.map { it.toWidget() } ?: listOf()
        }
        val json = Json.encodeToString(episodes)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("episodes")] = json
                this[stringPreferencesKey("update_type")] = "update"
            }
        }
        PodciniWidget().update(context, glanceId)
    }
}

class RemoveAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[EPISODE_ID_KEY]
        if (id == null) {
            Loge("RemoveAction", "id from parameter is null.")
            return
        }
        val episode = episodeById(id)
        if (episode == null) {
            Loge("RemoveAction", "episode with id: $id is null.")
            return
        }
        Logd(TAG, "RemoveAction onAction episode: ${episode.title}")
        val episodes = withContext(Dispatchers.IO) {
            smartRemoveFromQueues(episode)
            val queueId = parameters[QUEUE_ID_KEY]
            if (queueId == null) {
                Loge("RemoveAction", "queueId from parameter is null.")
                return@withContext listOf()
            }
            Logd(TAG, "RemoveAction onAction queueId: $queueId")
            val queue = realm.query(PlayQueue::class).query("id == $queueId").first().find()
            queue?.episodesSorted?.take(40)?.map { it.toWidget() } ?: listOf()
        }
        val json = Json.encodeToString(episodes)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[stringPreferencesKey("episodes")] = json
                this[stringPreferencesKey("update_type")] = "update"
            }
        }
        PodciniWidget().update(context, glanceId)
    }
}

class PlayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "onReceive")
        ensureAController()
        updateAppWidgetState(context, glanceId) { prefs ->
            val id = parameters[EPISODE_ID_KEY]
            if (id == null) {
                Loge("PlayAction", "id from parameter is null.")
                return@updateAppWidgetState
            }
            val episode = episodeById(id)
            if (episode == null) {
                Loge("PlayAction", "episode with id: $id is null.")
                return@updateAppWidgetState
            }
            prefs[MARKED_EPISODE_KEY] = id
            prefs[stringPreferencesKey("update_type")] = "episode"
            Logd(TAG, "PlayAction onAction episode: ${episode.title}")
            withContext(Dispatchers.Main) { PlaybackStarter(episode).setWidgetId(glanceId.toString()).shouldStreamThisTime(null).start() }
        }
        PodciniWidget().update(context, glanceId)
    }
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "onReceive")
        ensureAController()
        Logd(TAG, "ToggleAction onAction isPlaying: $theatres[0].isPlaying")
        if (theatres[0].mPlayer?.curEpisode == null) {
            val id = parameters[EPISODE_ID_KEY]
            if (id != null) {
                val e = realm.query(Episode::class).query("id == $id").first().find()
                if (e != null) theatres[0].mPlayer?.setAsCurEpisode(e)
            }
        }
        if (theatres[0].mPlayer?.curEpisode != null) {
            fun getPlayerActivityIntent(context: Context, mediaType_: MediaType? = null): Intent {
                val mediaType = mediaType_ ?: theatres[0].mPlayer!!.currentMediaType
                val showVideoPlayer = if (isRunning) mediaType == MediaType.VIDEO && !isCasting else theatres[0].mPlayer?.curState?.curIsVideo ?: false
                theatres[0].mPlayer?.playVideo = showVideoPlayer
                return MainActivityStarter(context).withOpenPlayer().getIntent()
            }
            withContext(Dispatchers.Main) {
                if (theatres[0].mPlayer?.curEpisode!!.getMediaType() == MediaType.VIDEO && !theatres[0].mPlayer!!.isPlaying && (theatres[0].mPlayer?.curEpisode?.feed?.videoModePolicy != VideoMode.AUDIO_ONLY)) {
                    theatres[0].mPlayer?.playPause()
                    context.startActivity(getPlayerActivityIntent(context, theatres[0].mPlayer?.curEpisode!!.getMediaType()))
                } else {
                    Logd(TAG, "Play button clicked: status: ${theatres[0].mPlayer?.status} is ready: ${playbackService?.isServiceReady()}")
                    PlaybackStarter(theatres[0].mPlayer?.curEpisode!!).setWidgetId(glanceId.toString()).shouldStreamThisTime(null).start()
                }
            }
            PodciniWidget().update(context, glanceId)
        }
    }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "PrevAction onAction")
        withContext(Dispatchers.Main) { theatres[0].mPlayer?.seekTo(0) }
        PodciniWidget().update(context, glanceId)
    }
}

class RewindAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "RewindAction onAction")
        withContext(Dispatchers.Main) { theatres[0].mPlayer?.seekDelta(-rewindSecs * 1000) }
        PodciniWidget().update(context, glanceId)
    }
}

class ForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "ForwardAction onAction")
        withContext(Dispatchers.Main) { theatres[0].mPlayer?.seekDelta(fastForwardSecs * 1000) }
        PodciniWidget().update(context, glanceId)
    }
}
class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Logd(TAG, "NextAction onAction")
        withContext(Dispatchers.Main) {
            Logd(TAG, "NextAction onAction isPlaying: $theatres[0].isPlaying isPaused: $theatres[0].isPaused")
            if (theatres[0].mPlayer!!.isPlaying || theatres[0].mPlayer!!.isPaused) theatres[0].mPlayer?.skip() }
        PodciniWidget().update(context, glanceId)
    }
}