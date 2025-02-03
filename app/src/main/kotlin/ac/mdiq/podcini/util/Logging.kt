package ac.mdiq.podcini.util

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

fun Logd(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPreferences.AppPrefs.prefPrintDebugLogs, false)) Log.d(t, m)
}

fun Loge(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPreferences.AppPrefs.prefPrintDebugLogs, false)) Log.e(t, m)
}

var toastMessages = mutableStateListOf<String>()
var toastMassege by mutableStateOf("")

fun Logt(t: String, m: String) {
    if (BuildConfig.DEBUG || getPref(AppPreferences.AppPrefs.prefPrintDebugLogs, false)) Log.e(t, m)
    if (getPref(AppPreferences.AppPrefs.prefShowErrorToasts, true)) toastMassege = "$t: $m"
    else toastMessages.add("$t: $m")
}

fun showStackTrace() {
    if (BuildConfig.DEBUG || getPref(AppPreferences.AppPrefs.prefPrintDebugLogs, false)) {
        val stackTraceElements = Thread.currentThread().stackTrace
        stackTraceElements.forEach { element ->
            Log.d("showStackTrace", element.toString())
        }
    }
}
