package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.R

enum class Rating(val code: Int, val res: Int) {
    UNRATED(-3, R.drawable.baseline_thumb_right_off_alt_24),
    TRASH(-2, R.drawable.ic_delete),
    BAD(-1, R.drawable.baseline_thumb_down_24),
    OK(0, R.drawable.baseline_sentiment_neutral_24),
    GOOD(1, R.drawable.baseline_thumb_up_24),
    SUPER(2, R.drawable.ic_star);

    companion object {
        fun fromCode(code: Int): Rating {
            return enumValues<Rating>().firstOrNull { it.code == code } ?: OK
        }
    }
}