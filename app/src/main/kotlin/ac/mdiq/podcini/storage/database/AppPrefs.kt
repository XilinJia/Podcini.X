package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.storage.model.AppAttribs
import ac.mdiq.podcini.storage.model.FacetsPrefs
import ac.mdiq.podcini.storage.model.SleepPrefs
import ac.mdiq.podcini.storage.model.SubscriptionsPrefs
import ac.mdiq.podcini.utils.Logd
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "AppPrefs"

var appAttribs: AppAttribs = realm.query(AppAttribs::class).first().find() ?: AppAttribs()
    private set

val appAttribsJob = CoroutineScope(Dispatchers.Default).launch {
    val flow = realm.query(AppAttribs::class).first().asFlow()
    flow.collect { changes: SingleQueryChange<AppAttribs> ->
        when (changes) {
            is InitialObject -> {
                Logd(TAG, "appAttribsJob InitialObject prefLastScreen: ${changes.obj?.prefLastScreen}")
                appAttribs = changes.obj
            }
            is UpdatedObject -> {
                Logd(TAG, "appAttribsJob UpdatedObject prefLastScreen: ${changes.obj?.prefLastScreen}")
                appAttribs = changes.obj
            }
            is DeletedObject -> {}
            is PendingObject -> {}
        }
    }
}

var sleepPrefs: SleepPrefs = SleepPrefs()
    private set

val sleepPrefsJob = CoroutineScope(Dispatchers.Default).launch {
    val flow = realm.query(SleepPrefs::class).first().asFlow()
    flow.collect { changes: SingleQueryChange<SleepPrefs> ->
        when (changes) {
            is UpdatedObject -> sleepPrefs = changes.obj
            is InitialObject -> sleepPrefs = changes.obj
            is DeletedObject -> {}
            is PendingObject -> {}
        }
    }
}

fun cancelAppPrefs() {
    sleepPrefsJob.cancel()
    appAttribsJob.cancel()
}