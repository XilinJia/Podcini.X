package ac.mdiq.podcini.utils

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import android.content.Context
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Date
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
        enum class Action { REMOVED, CLEARED }
        companion object {
            fun removed(episodes: List<Episode>): QueueEvent = QueueEvent(Action.REMOVED, episodes, -1)
            fun cleared(): QueueEvent = QueueEvent(Action.CLEARED, listOf(), -1)
        }
    }

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

    // TODO: perhaps FeedDetails Settings need to post this?
//    data class FeedChangeEvent(val feed: Feed, val changedFields: Array<String>) : FlowEvent()

    data class SpeedChangedEvent(val newSpeed: Float) : FlowEvent()

    data class EpisodeMediaEvent(val action: Action, val episodes: List<Episode>) : FlowEvent() {
        enum class Action { REMOVED }
        companion object {
            fun removed(vararg episodes: Episode): EpisodeMediaEvent = EpisodeMediaEvent(Action.REMOVED, listOf(*episodes))
        }
    }

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