package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.storage.model.AppAttribs
import ac.mdiq.podcini.storage.model.FacetsPrefs
import ac.mdiq.podcini.storage.model.SleepPrefs
import ac.mdiq.podcini.storage.model.SubscriptionsPrefs
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var appAttribs: AppAttribs = AppAttribs()

val appAttribsJob = CoroutineScope(Dispatchers.Default).launch {
    val flow = realm.query(AppAttribs::class).first().asFlow()
    flow.collect { changes: SingleQueryChange<AppAttribs> ->
        when (changes) {
            is UpdatedObject -> appAttribs = changes.obj
            is InitialObject -> appAttribs = changes.obj
            is DeletedObject -> {}
            is PendingObject -> {}
        }
    }
}

var subPrefs: SubscriptionsPrefs = SubscriptionsPrefs()

val subPrefsJob = CoroutineScope(Dispatchers.Default).launch {
    val flow = realm.query(SubscriptionsPrefs::class).first().asFlow()
    flow.collect { changes: SingleQueryChange<SubscriptionsPrefs> ->
        when (changes) {
            is UpdatedObject -> subPrefs = changes.obj
            is InitialObject -> subPrefs = changes.obj
            is DeletedObject -> {}
            is PendingObject -> {}
        }
    }
}

var facetsPrefs: FacetsPrefs = FacetsPrefs()

val facetsPrefsJob = CoroutineScope(Dispatchers.Default).launch {
    val flow = realm.query(FacetsPrefs::class).first().asFlow()
    flow.collect { changes: SingleQueryChange<FacetsPrefs> ->
        when (changes) {
            is UpdatedObject -> facetsPrefs = changes.obj
            is InitialObject -> facetsPrefs = changes.obj
            is DeletedObject -> {}
            is PendingObject -> {}
        }
    }
}

var sleepPrefs: SleepPrefs = SleepPrefs()

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
