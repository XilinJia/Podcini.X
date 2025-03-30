@file:Suppress("FunctionName")

package ac.mdiq.podcini.util

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.util.MiscFormatter.localDateTimeString
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val LogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
var toastMessages = mutableStateListOf<String>()
var toastMassege by mutableStateOf("")

fun Logd(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPrefs.prefPrintDebugLogs, false)) Log.d(t, m)
}

fun Loge(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPrefs.prefPrintDebugLogs, false)) Log.e(t, m)
    LogScope.launch {
        if (getPref(AppPrefs.prefShowErrorToasts, true)) toastMassege = "$t: Error: $m"
        toastMessages.add("${localDateTimeString()} $t: Error: $m")
    }
}

fun Logs(t: String, e: Throwable, m: String = "") {
    if (BuildConfig.DEBUG || getPref(AppPrefs.prefPrintDebugLogs, false)) Log.e(t, m + "\n" + Log.getStackTraceString(e))
    val me = e.message
    LogScope.launch {
        if (getPref(AppPrefs.prefShowErrorToasts, true)) toastMassege = "$t: $m Error: $me"
        toastMessages.add("${localDateTimeString()} $t: $m Error: $me")
    }
}

fun Logt(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPrefs.prefPrintDebugLogs, false)) Log.d(t, m)
    LogScope.launch {
        toastMassege = "$t: $m"
        toastMessages.add("${localDateTimeString()} $t: $m")
    }
}

fun showStackTrace() {
    if (BuildConfig.DEBUG || getPref(AppPrefs.prefPrintDebugLogs, false)) {
        val stackTraceElements = Thread.currentThread().stackTrace
        stackTraceElements.forEach { element -> Log.d("showStackTrace", element.toString()) }
    }
}
