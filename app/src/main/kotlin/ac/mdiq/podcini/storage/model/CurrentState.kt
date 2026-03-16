package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.playback.base.PlayerStatusInt
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class CurrentState : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var curMediaType: Long = LONG_MINUS_1

    var curFeedId: Long = 0

    var curMediaId: Long = 0

    var curIsVideo: Boolean = false

    var curPlayerStatus: Int = PlayerStatusInt.OTHER.code

    constructor() {}

    companion object {
        private val TAG: String = CurrentState::class.simpleName ?: "Anonymous"

        const val SPEED_USE_GLOBAL: Float = -1f

        const val LONG_MINUS_1: Long = -1
        const val LONG_PLUS_1: Long = 1
    }
}