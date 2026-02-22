package ac.mdiq.podcini.playback.base

enum class VideoMode(val code: Int, val tag: String) {
    DEFAULT(0, "default"),
    VIDEO(1, "video"),
    AUDIO_ONLY(3, "audio only");

    companion object {
        val videoModeTags = VideoMode.entries.map { it.tag }

        fun fromCode(code: Int): VideoMode {
            return VideoMode.entries.firstOrNull { it.code == code } ?: DEFAULT
        }
        fun fromTag(tag: String): VideoMode {
            return VideoMode.entries.firstOrNull { it.tag == tag } ?: DEFAULT
        }
    }
}
