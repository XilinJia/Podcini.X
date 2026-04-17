package ac.mdiq.podcini.utils

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.internalDir
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.buffer
import kotlin.time.Clock

class CrashReportWriter : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        try { writeCrashToFile(ex) } catch (e: Exception) { Logs(TAG, e, "Failed to write crash log") } finally { defaultHandler?.uncaughtException(thread, ex) }
        writeCrashToFile(ex)
    }

    private fun writeCrashToFile(ex: Throwable) {
        fun Int.pad() = this.toString().padStart(2, '0')
        crashLogFile1.sink().buffer().use { sink ->
            sink.writeString("## Crash info\n", Charsets.UTF_8)
            sink.writeString("Time: " + with(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { "${day.pad()}-${month.ordinal.pad()}-$year ${hour.pad()}:${minute.pad()}:${second.pad()}" }, Charsets.UTF_8)
            sink.writeString("Podcini version: " + BuildConfig.VERSION_NAME, Charsets.UTF_8)
            sink.writeString("", Charsets.UTF_8)
            sink.writeString("## StackTrace", Charsets.UTF_8)
            sink.writeString("```", Charsets.UTF_8)
            sink.writeString(ex.stackTraceToString(), Charsets.UTF_8)
            sink.writeString("```", Charsets.UTF_8)
        }
    }

    companion object {
        private val TAG: String = CrashReportWriter::class.simpleName ?: "Anonymous"

        val crashLogFile1: UnifiedFile
            get() = internalDir / "crash-report.log"
    }
}
