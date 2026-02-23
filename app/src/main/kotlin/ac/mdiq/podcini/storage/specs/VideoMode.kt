package ac.mdiq.podcini.storage.specs

enum class VideoMode(val code: Int, val tag: String) {
    DEFAULT(0, "default"),
    WINDOW(1, "window"),
    FULL_SCREEN(2, "full screen"),
    AUDIO_ONLY(3, "audio only");

    companion object {
        val videoModeTags = entries.map { it.tag }

        fun fromCode(code: Int): VideoMode {
            return entries.firstOrNull { it.code == code } ?: DEFAULT
        }
        fun fromTag(tag: String): VideoMode {
            return entries.firstOrNull { it.tag == tag } ?: DEFAULT
        }
    }
}