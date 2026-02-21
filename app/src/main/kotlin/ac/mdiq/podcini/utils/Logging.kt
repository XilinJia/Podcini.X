@file:Suppress("FunctionName")

package ac.mdiq.podcini.utils

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.database.appPrefs
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val LogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
var toastMessages = mutableStateListOf<String>()
var toastMassege by mutableStateOf("")

private suspend fun trimToasts() {
    val size = toastMessages.size
    if (size > 120) {
        withContext(Dispatchers.Main) {
            val newList = toastMessages.subList(20, size).toList()
            toastMessages.clear()
            toastMessages.addAll(newList)
        }
    }
}

fun Logd(t: String, m: String) {
    if (BuildConfig.DEBUG || appPrefs.printDebugLogs) Log.d(t, m)
}

fun Loge(t: String, m: String) {
    if (BuildConfig.DEBUG || appPrefs.printDebugLogs) Log.e(t, m)
    LogScope.launch {
        trimToasts()
        if (appPrefs.showErrorToasts) toastMassege = "$t: Error: $m"
        toastMessages.add("${localDateTimeString()} $t: Error: $m")
    }
}

fun Logs(t: String, e: Throwable, m: String = "") {
    if (BuildConfig.DEBUG || appPrefs.printDebugLogs) Log.e(t, m + "\n" + Log.getStackTraceString(e))
    val me = e.message
    LogScope.launch {
        trimToasts()
        if (appPrefs.showErrorToasts) toastMassege = "$t: $m Error: $me"
        toastMessages.add("${localDateTimeString()} $t: $m Error: $me")
    }
}

fun Logt(t: String, m: String) {
    if (BuildConfig.DEBUG || appPrefs.printDebugLogs) Log.d(t, m)
    LogScope.launch {
        trimToasts()
        toastMassege = "$t: $m"
        toastMessages.add("${localDateTimeString()} $t: $m")
    }
}

fun showStackTrace() {
    if (BuildConfig.DEBUG || appPrefs.printDebugLogs) {
        val stackTraceElements = Thread.currentThread().stackTrace
        stackTraceElements.forEach { element -> Log.d("showStackTrace", element.toString()) }
    }
}

var startTime: Long = 0
var nanoTime: Long = 0

fun startTiming() {
    nanoTime = System.nanoTime()
    startTime = nanoTime
}
fun timeIt(msg: String) {
    if (BuildConfig.DEBUG) {
        val time = System.nanoTime()
        val dTime = (time - nanoTime) / 1000000
        val dsTime = (time - startTime) / 1000000
        Logd("TimeIt", "$msg $time delta: $dTime from Start: $dsTime" )
        nanoTime = time
    }
}