package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.storage.model.AppAttribs
import ac.mdiq.podcini.storage.model.SleepPrefs
import ac.mdiq.podcini.utils.Logd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "AppPrefs"

var appAttribs: AppAttribs by mutableStateOf(realm.query(AppAttribs::class).query("id == 0").first().find() ?: AppAttribs() )
    private set
var sleepPrefs: SleepPrefs = SleepPrefs()
    private set

var appAttribsJob: Job? = null
var sleepPrefsJob: Job? = null

fun initAppPrefs() {
    if (appAttribsJob == null) appAttribsJob = CoroutineScope(Dispatchers.IO).launch {
        val flow = realm.query(AppAttribs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<AppAttribs> ->
            when (changes) {
                is InitialObject -> {
                    Logd(TAG, "appAttribsJob InitialObject prefLastScreen: ${changes.obj.prefLastScreen}")
                    appAttribs = changes.obj
                }
                is UpdatedObject -> {
                    Logd(TAG, "appAttribsJob UpdatedObject prefLastScreen: ${changes.obj.prefLastScreen}")
                    appAttribs = changes.obj
                }
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }
    if (sleepPrefsJob == null) sleepPrefsJob = CoroutineScope(Dispatchers.IO).launch {
        val flow = realm.query(SleepPrefs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<SleepPrefs> ->
            Logd(TAG, "sleepPrefsJob flow.collect")
            when (changes) {
                is UpdatedObject -> sleepPrefs = changes.obj
                is InitialObject -> sleepPrefs = changes.obj
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }
}

fun cancelAppPrefs() {
    sleepPrefsJob?.cancel()
    appAttribsJob?.cancel()
}