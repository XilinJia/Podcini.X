package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.storage.model.Feed
import java.io.IOException
import java.io.Writer

interface ExportWriter {
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    fun writeDocument(feeds: List<Feed>, writer: Writer)

    fun fileExtension(): String?
}