package ac.mdiq.podcini.utils.error

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.utils.getDataFolder
import ac.mdiq.podcini.utils.Logs
import android.os.Build
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import ac.mdiq.podcini.storage.utils.nowInMillis
import kotlin.time.Clock

class CrashReportWriter : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        write(ex)
        defaultHandler?.uncaughtException(thread, ex)
    }

    companion object {
        private val TAG: String = CrashReportWriter::class.simpleName ?: "Anonymous"

        
        val crashLogFile: File
            get() = File(getDataFolder(null), "crash-report.log")

        
        fun write(exception: Throwable) {
            fun Int.pad() = this.toString().padStart(2, '0')

            val path = crashLogFile
            var out: PrintWriter? = null
            try {
                out = PrintWriter(path, "UTF-8")
                out.println("## Crash info")
                out.println("Time: " + with(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) { "${day.pad()}-${month.ordinal.pad()}-$year ${hour.pad()}:${minute.pad()}:${second.pad()}" })
                out.println("Podcini version: " + BuildConfig.VERSION_NAME)
                out.println()
                out.println("## StackTrace")
                out.println("```")
                exception.printStackTrace(out)
                out.println("```")
            } catch (e: IOException) { Logs(TAG, e)
            } finally { IOUtils.closeQuietly(out) }
        }

        val systemInfo: String
            get() = """
                 ## Environment
                 Android version: ${Build.VERSION.RELEASE}
                 OS version: ${System.getProperty("os.version")}
                 Podcini version: ${BuildConfig.VERSION_NAME}
                 Model: ${Build.MODEL}
                 Device: ${Build.DEVICE}
                 Product: ${Build.PRODUCT}
                 """.trimIndent()
    }
}
