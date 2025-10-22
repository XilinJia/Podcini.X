package ac.mdiq.podcini.util

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import android.content.Context
import android.view.KeyEvent
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

sealed class FlowEvent {
    val TAG = this::class.simpleName ?: "FlowEvent"
    val id: Long = Date().time

    data class PlaybackServiceEvent(val action: Action) : FlowEvent() {
        enum class Action { SERVICE_STARTED, SERVICE_SHUT_DOWN, }
    }

    data class BufferUpdateEvent(val progress: Float) : FlowEvent() {
        fun hasStarted(): Boolean = progress == PROGRESS_STARTED
        fun hasEnded(): Boolean = progress == PROGRESS_ENDED

        companion object {
            private const val PROGRESS_STARTED = -1f
            private const val PROGRESS_ENDED = -2f
            fun started(): BufferUpdateEvent = BufferUpdateEvent(PROGRESS_STARTED)
            fun ended(): BufferUpdateEvent = BufferUpdateEvent(PROGRESS_ENDED)
            fun progressUpdate(progress: Float): BufferUpdateEvent = BufferUpdateEvent(progress)
        }
    }

    data class QueueEvent(val action: Action, val episodes: List<Episode>, val position: Int) : FlowEvent() {
        enum class Action {
            ADDED, SET_QUEUE, REMOVED, IRREVERSIBLE_REMOVED, CLEARED, DELETED_MEDIA, SORTED, MOVED, SWITCH_QUEUE
        }
//        fun inQueue(): Boolean {
//            return when (action) {
//                Action.ADDED, Action.SET_QUEUE, Action.SORTED, Action.MOVED, Action.SWITCH_QUEUE -> true
//                else -> false
//            }
//        }
        companion object {
            fun added(episode: Episode, position: Int): QueueEvent = QueueEvent(Action.ADDED, listOf(episode), position)
//            fun setQueue(queue: List<Episode>): QueueEvent = QueueEvent(Action.SET_QUEUE, queue, -1)
            fun removed(episode: Episode): QueueEvent = QueueEvent(Action.REMOVED, listOf(episode), -1)
            fun removed(episodes: List<Episode>): QueueEvent = QueueEvent(Action.REMOVED, episodes, -1)
            fun irreversibleRemoved(episode: Episode): QueueEvent = QueueEvent(Action.IRREVERSIBLE_REMOVED, listOf(episode), -1)
            fun cleared(): QueueEvent = QueueEvent(Action.CLEARED, listOf(), -1)
            fun sorted(sortedQueue: List<Episode>): QueueEvent = QueueEvent(Action.SORTED, sortedQueue, -1)
            fun moved(episode: Episode, newPosition: Int): QueueEvent = QueueEvent(Action.MOVED, listOf(episode), newPosition)
//            fun switchQueue(qItems: List<Episode>): QueueEvent = QueueEvent(Action.SWITCH_QUEUE, qItems, -1)
        }
    }

    data class HistoryEvent(val sortOrder: EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_NEW_OLD, val startDate: Long = 0, val endDate: Long = Date().time) : FlowEvent()

    data class SleepTimerUpdatedEvent(private val timeLeft: Long) : FlowEvent() {
        val isOver: Boolean
            get() = timeLeft <= 0L
        val isCancelled: Boolean
            get() = timeLeft == CANCELLED

        fun getTimeLeft(): Long = abs(timeLeft)
        companion object {
            private const val CANCELLED = Long.MAX_VALUE
            fun updated(timeLeft: Long): SleepTimerUpdatedEvent = SleepTimerUpdatedEvent(max(0, timeLeft))
            fun cancelled(): SleepTimerUpdatedEvent = SleepTimerUpdatedEvent(CANCELLED)
        }
    }

    data class FeedListEvent(val action: Action, val feedIds: List<Long> = emptyList()) : FlowEvent() {
        enum class Action { ADDED, REMOVED, ERROR, UNKNOWN }

        constructor(action: Action, feedId: Long) : this(action, listOf(feedId))

        fun contains(feed: Feed): Boolean = feedIds.contains(feed.id)
    }

    data class FeedChangeEvent(val feed: Feed, val changedFields: Array<String>) : FlowEvent()

    data class SpeedChangedEvent(val newSpeed: Float) : FlowEvent()

    data class DownloadLogEvent(val dummy: Unit = Unit) : FlowEvent()

    data class EpisodeEvent(val episodes: List<Episode>) : FlowEvent() {
        companion object {
            fun updated(vararg episodes: Episode): EpisodeEvent = EpisodeEvent(listOf(*episodes))
        }
    }

    data class EpisodeMediaEvent(val action: Action, val episodes: List<Episode>) : FlowEvent() {
        enum class Action { ADDED, REMOVED, UPDATED, ERROR, UNKNOWN }
        companion object {
            fun added(vararg episodes: Episode): EpisodeMediaEvent = EpisodeMediaEvent(Action.ADDED, listOf(*episodes))
            fun updated(episodes: List<Episode>): EpisodeMediaEvent = EpisodeMediaEvent(Action.UPDATED, episodes)
            fun updated(vararg episodes: Episode): EpisodeMediaEvent = EpisodeMediaEvent(Action.UPDATED, listOf(*episodes))
            fun removed(vararg episodes: Episode): EpisodeMediaEvent = EpisodeMediaEvent(Action.REMOVED, listOf(*episodes))
        }
    }

    data class FeedTagsChangedEvent(val dummy: Unit = Unit) : FlowEvent()

    data class FeedUpdatingEvent(val isRunning: Boolean) : FlowEvent()

    data class MessageEvent(val message: String, val action: ((Context)->Unit)? = null, val actionText: String? = null) : FlowEvent()

    data class SyncServiceEvent(val messageResId: Int, val message: String = "") : FlowEvent()

    data class DiscoveryDefaultUpdateEvent(val dummy: Unit = Unit) : FlowEvent()
}

object EventFlow {
    val events: MutableSharedFlow<FlowEvent> = MutableSharedFlow(replay = 0)
    val stickyEvents: MutableSharedFlow<FlowEvent> = MutableSharedFlow(replay = 1)
    val keyEvents: MutableSharedFlow<KeyEvent> = MutableSharedFlow(replay = 0)

    fun postEvent(event: FlowEvent) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd("EventFlow", "${caller?.className}.${caller?.methodName} posted: $event")
        }
        CoroutineScope(Dispatchers.Default).launch { events.emit(event) }
    }

    fun postStickyEvent(event: FlowEvent) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd("EventFlow", "${caller?.className}.${caller?.methodName} posted sticky: $event")
        }
        CoroutineScope(Dispatchers.Default).launch { stickyEvents.emit(event) }
    }

    fun postEvent(event: KeyEvent) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Logd("EventFlow", "${caller?.className}.${caller?.methodName} posted key: $event")
        }
        CoroutineScope(Dispatchers.Default).launch { keyEvents.emit(event) }
    }
}