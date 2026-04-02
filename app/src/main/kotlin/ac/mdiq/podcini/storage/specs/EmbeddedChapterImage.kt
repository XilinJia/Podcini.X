package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.storage.model.Episode

class EmbeddedChapterImage(val media: Episode, private val imageUrl: String) {

    var position: Int = 0
    var length: Int = 0

    init {
        val match = EMBEDDED_IMAGE_MATCHER.find(imageUrl)
        if (match != null) {
            this.position = match.groups[1]?.value?.toIntOrNull() ?: 0
            this.length = match.groups[2]?.value?.toIntOrNull() ?: 0
        } else throw IllegalArgumentException("Not an embedded chapter: $imageUrl")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || javaClass != other.javaClass) return false

        val that = other as EmbeddedChapterImage
        return (imageUrl == that.imageUrl)
    }

    override fun hashCode(): Int {
        return imageUrl.hashCode()
    }

    companion object {
        private val EMBEDDED_IMAGE_MATCHER = Regex("embedded-image://(\\d+)/(\\d+)")

        // TODO: need to check this ???
        fun getModelFor(media: Episode, chapter: Int): Any? {
            if (media.chapters.isEmpty()) return null
            val imageUrl = media.chapters[chapter].imageUrl
            return if (imageUrl != null && EMBEDDED_IMAGE_MATCHER.matches(imageUrl)) EmbeddedChapterImage(media, imageUrl) else imageUrl
        }
    }
}