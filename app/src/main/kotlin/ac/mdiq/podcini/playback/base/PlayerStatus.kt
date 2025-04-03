package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_OTHER
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PAUSED
import ac.mdiq.podcini.storage.model.CurrentState.Companion.PLAYER_STATUS_PLAYING

enum class PlayerStatus(private val statusValue: Int) {
    ERROR(-1),
    INDETERMINATE(0),  // player is currently changing its state, listeners should wait until the state is left
    STOPPED(5),
    INITIALIZED(10), // playback service was started, data source of media player was set
    PREPARING(19),
    PREPARED(20),
    PAUSED(30),
    PLAYING(40);

    fun isAtLeast(other: PlayerStatus?): Boolean {
        return other == null || this.statusValue >= other.statusValue
    }

    fun getAsInt(): Int {
        return when (this) {
            PLAYING -> PLAYER_STATUS_PLAYING
            PAUSED -> PLAYER_STATUS_PAUSED
            else -> PLAYER_STATUS_OTHER
        }
    }

    companion object {
        private val fromOrdinalLookup = entries.toTypedArray()
    }
}
