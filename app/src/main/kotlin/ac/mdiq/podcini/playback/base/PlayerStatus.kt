package ac.mdiq.podcini.playback.base

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

    fun toStatusInt(): PlayerStatusSimple {
        return when (this) {
            PLAYING -> PlayerStatusSimple.PLAYING
            PAUSED -> PlayerStatusSimple.PAUSED
            else -> PlayerStatusSimple.OTHER
        }
    }
}

enum class PlayerStatusSimple(val code: Int) {
    PLAYING(1),   // Value of PREF_CURRENT_PLAYER_STATUS if media player status is playing.
    PAUSED(2),    // Value of PREF_CURRENT_PLAYER_STATUS if media player status is paused.
    OTHER(3),     // Value of PREF_CURRENT_PLAYER_STATUS if media player status is neither playing nor paused.
}
