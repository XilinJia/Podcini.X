package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import android.net.Uri
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ExportTypes(val contentType: String, val outputNameTemplate: String, @field:StringRes val labelResId: Int) {
    OPML("text/x-opml", "podcini-feeds-%s.opml", R.string.opml_export_label),
    OPML_SELECTED("text/x-opml", "podcini-feeds-selected-%s.opml", R.string.opml_export_label),
    HTML("text/html", "podcini-feeds-%s.html", R.string.html_export_label),
    FAVORITES("text/html", "podcini-favorites-%s.html", R.string.favorites_export_label),
    PROGRESS("text/x-json", "podcini-progress-%s.json", R.string.progress_export_label),
}

class ExportWorker private constructor(private val exportWriter: ExportWriter, private val output: UnifiedFile) {
    constructor(exportWriter: ExportWriter) : this(exportWriter, EXPORT_DIR.toUF() / (DEFAULT_OUTPUT_NAME + "." + exportWriter.fileExtension()))

    suspend fun exportFile(feeds: List<Feed>? = null): UnifiedFile {
        return withContext(Dispatchers.IO) {
            if (output.exists()) {
                output.delete()
                Logd(TAG, "Overwriting previously exported file")
            }
            val feeds_ = feeds ?: getFeedList()
            Logd(TAG, "feeds_: ${feeds_.size}")
            exportWriter.writeDocument(feeds_, output)
            output
        }
    }
    companion object {
        private const val EXPORT_DIR = "export/"
        private val TAG: String = ExportWorker::class.simpleName ?: "Anonymous"
        private const val DEFAULT_OUTPUT_NAME = "podcini-feeds"
    }
}

class DocumentFileExportWorker(private val exportWriter: ExportWriter, private val outputFileUri: Uri) {
    suspend fun exportFile(feeds: List<Feed>? = null): UnifiedFile {
        return withContext(Dispatchers.IO) {
            val output = outputFileUri.toUF()
            try {
                val feeds_ = feeds ?: getFeedList()
                Logd("DocumentFileExportWorker", "feeds_: ${feeds_.size}")
                exportWriter.writeDocument(feeds_, output)
                output
            } catch (e: Exception) { throw e }
        }
    }
}
