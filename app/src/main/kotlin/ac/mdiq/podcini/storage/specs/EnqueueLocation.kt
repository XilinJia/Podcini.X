package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.R

enum class EnqueueLocation(val code: Int, val res: Int) {
    BACK(0,R.string.enqueue_location_back),
    FRONT(1, R.string.enqueue_location_front),
    AFTER_CURRENTLY_PLAYING(2, R.string.enqueue_location_after_current),
    RANDOM(3, R.string.enqueue_location_random);

    companion object {
        fun fromCode(code: Int): EnqueueLocation {
            return EnqueueLocation.entries.firstOrNull { it.code == code } ?: BACK
        }
    }
}
