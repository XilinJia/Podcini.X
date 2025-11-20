package ac.mdiq.podcini.util.error

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.utils.getDataFolder
import ac.mdiq.podcini.util.Logs
import android.os.Build
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val path = crashLogFile
            var out: PrintWriter? = null
            try {
                out = PrintWriter(path, "UTF-8")
                out.println("## Crash info")
                out.println("Time: " + SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
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
