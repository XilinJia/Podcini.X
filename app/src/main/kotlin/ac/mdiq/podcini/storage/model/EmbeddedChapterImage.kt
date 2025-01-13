package ac.mdiq.podcini.storage.model

import java.util.regex.Pattern

class EmbeddedChapterImage(@JvmField val media: Episode, private val imageUrl: String) {
    @JvmField
    var position: Int = 0
    @JvmField
    var length: Int = 0

    init {
        val m = EMBEDDED_IMAGE_MATCHER.matcher(imageUrl)
        if (m.find()) {
            this.position = m.group(1)?.toInt() ?: 0
            this.length = m.group(2)?.toInt() ?: 0
        } else {
            throw IllegalArgumentException("Not an embedded chapter")
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true

        if (o == null || javaClass != o.javaClass) return false

        val that = o as EmbeddedChapterImage
        return (imageUrl == that.imageUrl)
    }

    override fun hashCode(): Int {
        return imageUrl.hashCode()
    }

    companion object {
        private val EMBEDDED_IMAGE_MATCHER: Pattern = Pattern.compile("embedded-image://(\\d+)/(\\d+)")

        @JvmStatic
        fun makeUrl(position: Int, length: Int): String {
            return "embedded-image://$position/$length"
        }

        private fun isEmbeddedChapterImage(imageUrl: String): Boolean {
            return EMBEDDED_IMAGE_MATCHER.matcher(imageUrl).matches()
        }

        fun getModelFor(media: Episode, chapter: Int): Any? {
            if (media.chapters.isEmpty()) return null
            val imageUrl = media.chapters[chapter].imageUrl
            return if (imageUrl != null && isEmbeddedChapterImage(imageUrl)) EmbeddedChapterImage(media, imageUrl) else  imageUrl
        }
    }
}
