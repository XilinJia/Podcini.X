package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.model.ARCHIVED_VOLUME_ID
import ac.mdiq.podcini.storage.model.AppAttribs
import ac.mdiq.podcini.storage.model.AppPrefs
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.FacetsPrefs
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SleepPrefs
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.model.SubscriptionsPrefs
import ac.mdiq.podcini.storage.model.SyncPrefs
import ac.mdiq.podcini.storage.model.Timer
import ac.mdiq.podcini.storage.model.Todo
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.ui.screens.DefaultPages
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.showStackTrace
import android.util.Log
import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.dynamic.getValue
import io.github.xilinjia.krdb.ext.isManaged
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.ContinuationInterceptor

private const val TAG: String = "RealmDB"

private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

val config: RealmConfiguration by lazy {
    RealmConfiguration.Builder(schema = setOf(
        Volume::class,
        Feed::class,
        Episode::class,
        CurrentState::class,
        PlayQueue::class,
        QueueEntry::class,
        DownloadResult::class,
        ShareLog::class,
        SubscriptionLog::class,
        Chapter::class,
        Todo::class,
        Timer::class,
        PAFeed::class,
        AppAttribs::class,
        AppPrefs::class,
        SubscriptionsPrefs::class,
        FacetsPrefs::class,
        SleepPrefs::class,
        SyncPrefs::class,
    )).name("Podcini.realm").schemaVersion(116)
        .migration({ mContext ->
            val oldRealm = mContext.oldRealm // old realm using the previous schema
            val newRealm = mContext.newRealm // new realm using the new schema
            if (oldRealm.schemaVersion() < 55) {
                Log.d(TAG, "migrating DB from below 55")
                mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    newObject?.run {
                        val playState = oldObject.getValue<Long>(fieldName = "playState")
                        if (playState == EpisodeState.AGAIN.code.toLong()) {
                            val t = nowInMillis() + (8.64e7 * (10..100).random()).toLong()
                            set("repeatTime", t)
                        }
                    }
                }
            }
            if (oldRealm.schemaVersion() < 59) {
                Log.d(TAG, "migrating DB from below 59")
                val queues = newRealm.query("PlayQueue").find()
                for (queue in queues) queue.set("launchAutoEQDlWhenEmpty", true)
            }
            if (oldRealm.schemaVersion() < 65) {
                Log.d(TAG, "migrating DB from below 65")
                newRealm.copyToRealm(
                    DynamicMutableRealmObject.create(
                        type = "AppAttribs",
                        mapOf(
                            "id" to 0L,
                            "curQueueId" to 0L
                        )
                    )
                )
            }
            if (oldRealm.schemaVersion() < 71) {
                Log.d(TAG, "migrating DB from below 71")
                val queues = newRealm.query("PlayQueue").find()
                for (queue in queues) queue.set("playInSequence", true)
            }
            if (oldRealm.schemaVersion() < 85) {
                Log.d(TAG, "migrating DB from below 85")
                val feedsOld = oldRealm.query("Feed").find()
                for (f in feedsOld) {
                    val id = f.getValue<Long>("id")
                    val fNew = newRealm.query("Feed", "id == $id").first().find() ?: continue
                    fNew.set("volumeId", -1L)
                }
            }
            if (oldRealm.schemaVersion() < 95) {
                Log.d(TAG, "migrating DB from below 95")
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrNew.isNotEmpty()) attrNew[0].set("topChartCountryCode", Locale.getDefault().country)
            }
            if (oldRealm.schemaVersion() < 101) {
                Log.d(TAG, "migrating DB from below 101")
                val presSynd = newRealm.query("Feed").query("id == 21").first().find()
                presSynd?.set("volumeId", ARCHIVED_VOLUME_ID)
            }
            if (oldRealm.schemaVersion() < 106) {
                Log.d(TAG, "migrating DB from below 106")
                val feeds = newRealm.query("Feed").find()
                for (f in feeds) {
                    val id = f.getValue<Long>("id")
                    val count = newRealm.query("Episode").query("feedId == $id").count().find()
                    f.set("episodesCount", count)
                }
            }
            if (oldRealm.schemaVersion() < 107) {
                Log.d(TAG, "migrating DB from below 107")
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrNew.isNotEmpty()) attrNew[0].set("transceivePort", 21080L)
            }
            if (oldRealm.schemaVersion() < 108) {
                Log.d(TAG, "migrating DB from below 108")
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrNew.isNotEmpty()) attrNew[0].set("udpPort", 21088L)
            }
            if (oldRealm.schemaVersion() < 109) {
                Log.d(TAG, "migrating DB from below 109")
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrNew.isNotEmpty()) attrNew[0].set("name", "My Podcini")
            }
            if (oldRealm.schemaVersion() < 111) {
                Log.d(TAG, "migrating DB from below 111")
                val episodes = newRealm.query("Episode").query("playState == ${EpisodeState.FOREVER.code}").find()
                for (e in episodes) {
                    e.set("repeatInterval", (8.64e7 * 10).toLong())
                    val t = nowInMillis() + (8.64e7 * (10..100).random()).toLong()
                    e.set("repeatTime", t)
                }
            }
            if (oldRealm.schemaVersion() < 115) {
                Log.d(TAG, "migrating DB from below 115")
                val prefsNew = newRealm.query("AppPrefs").first().find()
                if (prefsNew != null) {
                    val dp = prefsNew.getValue<String>("defaultPage")
                    if (dp == "Remember") {
                        prefsNew.set("defaultPage", DefaultPages.Library.name)
                        val att = newRealm.query("AppAttribs").first().find()
                        att?.set("restoreLastScreen", true)
                    }
                }
            }
        }).build()
}

lateinit var realm: Realm
    private set

fun getRealmInstance() {
    if (::realm.isInitialized) return
    realm = Realm.open(config)
}

fun <T : TypedRealmObject> unmanaged(entity: T) : T {
    if (BuildConfig.DEBUG) {
        val stackTrace = Thread.currentThread().stackTrace
        val caller = if (stackTrace.size > 3) stackTrace[3] else null
        Logd(TAG, "${caller?.className}.${caller?.methodName} unmanaged: ${entity.javaClass.simpleName}")
    }
    return if (entity.isManaged()) realm.copyFromRealm(entity) else entity
}

suspend fun <T : TypedRealmObject> update(entity: T, block: MutableRealm.(T) -> Unit) : T {
    return realm.write {
        val result: T = findLatest(entity)?.let {
            block(it)
            it
        } ?: entity
        result
    }
}

suspend fun <T : RealmObject> upsert(entity: T, block: MutableRealm.(T) -> Unit) : T {
//    if (BuildConfig.DEBUG) {
//        val stackTrace = Thread.currentThread().stackTrace
//        val caller = if (stackTrace.size > 3) stackTrace[3] else null
//        Logd(TAG, "${caller?.className}.${caller?.methodName} upsert: ${entity.javaClass.simpleName} ${entity.isManaged()}")
//    }
    return realm.write {
        var result: T = entity
        if (entity.isManaged()) {
            result = findLatest(entity)?.let {
                block(it)
                it
            } ?: entity
        } else {
            try {
                result = copyToRealm(entity, UpdatePolicy.ALL).let {
                    block(it)
                    it
                }
            } catch (e: Exception) {
                Logs(TAG, e, "copyToRealm error")
                showStackTrace()
            }
        }
        result
    }
}

fun <T : RealmObject> upsertBlk(entity: T, block: MutableRealm.(T) -> Unit) : T {
//    if (BuildConfig.DEBUG) {
//        val stackTrace = Thread.currentThread().stackTrace
//        val caller = if (stackTrace.size > 3) stackTrace[3] else null
//        Logd(TAG, "${caller?.className}.${caller?.methodName} upsertBlk: ${entity.javaClass.simpleName}")
//    }
    return realm.writeBlocking {
        var result: T = entity
        if (entity.isManaged()) {
            result = findLatest(entity)?.let {
                block(it)
                it
            } ?: entity
        } else {
            try {
                result = copyToRealm(entity, UpdatePolicy.ALL).let {
                    block(it)
                    it
                }
            } catch (e: Exception) {
                Logs(TAG, e, "copyToRealm error")
                showStackTrace()
            }
        }
        result
    }
}

fun <T : EmbeddedRealmObject> upsertBlkEmb(entity: T, block: MutableRealm.(T) -> Unit) : T {
    //    if (BuildConfig.DEBUG) {
    //        val stackTrace = Thread.currentThread().stackTrace
    //        val caller = if (stackTrace.size > 3) stackTrace[3] else null
    //        Logd(TAG, "${caller?.className}.${caller?.methodName} upsertBlk: ${entity.javaClass.simpleName}")
    //    }
    return realm.writeBlocking {
        var result: T = entity
            result = findLatest(entity)?.let {
                block(it)
                it
            } ?: entity
        result
    }
}


fun runOnIOScope(block: suspend () -> Unit) : Job {
    return ioScope.launch {
        if (Dispatchers.IO == coroutineContext[ContinuationInterceptor]) block()
        else withContext(Dispatchers.IO) { block() }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private val lastId = AtomicLong(0)
@OptIn(ExperimentalAtomicApi::class)
fun getId(now: Long = nowInMillis()): Long {
    while (true) {
        val last = lastId.load()
        val next = if (now > last) now else last + 1
        if (lastId.compareAndSet(last, next)) return next
    }
}

private val subscriptionMutex = Mutex()

data class MonitorEntity(
    val tag: String,
    val onChanges: suspend (Episode, fields: Array<String>)->Unit,
    val onInit: (suspend (Episode)->Unit)? = null)

class EpisodeMonitors {
    var job: Job? = null
    val entities: MutableSet<MonitorEntity> = mutableSetOf()
}

private val idMonitorsMap: MutableMap<Long, EpisodeMonitors> = mutableMapOf()

private fun episodeMonitor(episode: Episode): Job {
    return CoroutineScope(Dispatchers.IO).launch {
        val item_ = realm.query(Episode::class).query("id == ${episode.id}").first()
        Logd(TAG, "start monitoring episode: ${episode.id} ${episode.title}")
        item_.asFlow().collect { changes: SingleQueryChange<Episode> ->
            //                Logd(TAG, "episodeMonitor in collect subscriptionLock: $subscriptionLock")
            subscriptionMutex.withLock {
                val ms = idMonitorsMap[episode.id] ?: return@collect
                when (changes) {
                    is UpdatedObject -> {
                        Logd(TAG, "episodeMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                        for (e in ms.entities) {
                            if (episode.id == changes.obj.id) {
                                Logd(TAG, "episodeMonitor onChange callback for ${e.tag} ${episode.title}")
                                e.onChanges(changes.obj, changes.changedFields)
                            }
                        }
                    }
                    is InitialObject -> {
                        Logd(TAG, "episodeMonitor InitialObject ${changes.obj.title}")
                        for (e in ms.entities) {
                            if (episode.id == changes.obj.id) {
                                Logd(TAG, "episodeMonitor onChange callback for ${e.tag} ${episode.title}")
                                e.onInit?.invoke(changes.obj)
                            }
                        }
                    }
                    else -> Logd(TAG, "episodeMonitor other changes: $changes")
                }
            }
        }
    }
}

fun hasSubscribed(episode: Episode, tag: String): Boolean {
    val ms = idMonitorsMap[episode.id] ?: return false
    return ms.entities.firstOrNull { it.tag == tag } != null
}

suspend fun subscribeEpisode(episode: Episode, entity: MonitorEntity) {
    subscriptionMutex.withLock {
        var ms = idMonitorsMap[episode.id]
        if (ms == null) {
            ms = EpisodeMonitors()
            ms.entities.add(entity)
            ms.job = episodeMonitor(episode)
            idMonitorsMap[episode.id] = ms
        } else {
            ms.entities.removeIf { it.tag == entity.tag }
            ms.entities.add(entity)
        }
        Logd(TAG, "subscribeEpisode ${entity.tag} ${episode.id} ${episode.title}")
        Logd(TAG, "subscribeEpisode idMonitorsMap: ${idMonitorsMap.size}")
        for ((k, v) in idMonitorsMap.entries.toList()) for (e in v.entities) Logd(TAG, "subscribeEpisode idMonitorsMap $k tag: ${e.tag} job: ${v.job != null}")
    }
}

fun unsubscribeEpisode(episode: Episode, tag: String) {
    CoroutineScope(Dispatchers.IO).launch {
        subscriptionMutex.withLock {
            val ms = idMonitorsMap[episode.id]
            if (ms != null) {
                try {
                    ms.entities.removeIf { it.tag == tag }
                    if (ms.entities.isEmpty()) {
                        ms.job?.cancel()
                        idMonitorsMap.remove(episode.id)
                    }
                } catch (e: Throwable) { Logs(TAG, e, "unsubscribe episode failed $tag ${episode.title}") }
            }
            Logd(TAG, "unsubscribeEpisode $tag ${episode.id} ${episode.title}")
            Logd(TAG, "unsubscribeEpisode idMonitorsMap: ${idMonitorsMap.size}")
            for ((k, v) in idMonitorsMap.entries.toList()) for (e in v.entities) Logd(TAG, "unsubscribeEpisode idMonitorsMap $k tag: ${e.tag} job: ${v.job != null}")
        }
    }
}
