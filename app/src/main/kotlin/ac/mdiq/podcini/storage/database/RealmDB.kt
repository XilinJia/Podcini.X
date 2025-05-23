package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.CurrentState
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.showStackTrace
import android.net.Uri
import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.dynamic.getValue
import io.github.xilinjia.krdb.dynamic.getValueSet
import io.github.xilinjia.krdb.ext.isManaged
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.ContinuationInterceptor

object RealmDB {
    private val TAG: String = RealmDB::class.simpleName ?: "Anonymous"

    private val ioScope = CoroutineScope(Dispatchers.IO)

    val realm: Realm

    init {
        Logd(TAG, "RealmDB init")
        val config = RealmConfiguration.Builder(
            schema = setOf(
                Feed::class,
                Episode::class,
                CurrentState::class,
                PlayQueue::class,
                DownloadResult::class,
                ShareLog::class,
                SubscriptionLog::class,
                Chapter::class,
                PAFeed::class,
            ))
            .name("Podcini.realm")
            .schemaVersion(53)
            .migration({ mContext ->
                val oldRealm = mContext.oldRealm // old realm using the previous schema
                val newRealm = mContext.newRealm // new realm using the new schema
                if (oldRealm.schemaVersion() < 25) {
                    Logd(TAG, "migrating DB from below 25")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            set("rating", if (oldObject.getValue<Boolean>(fieldName = "isFavorite")) 2L else 0L)
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 26) {
                    Logd(TAG, "migrating DB from below 26")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            if (oldObject.getValue<Long>(fieldName = "rating") == 0L) set("rating", -3L)
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 28) {
                    Logd(TAG, "migrating DB from below 28")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            if (oldObject.getValue<Long>(fieldName = "playState") == 1L) set("playState", 10L)
                            else {
                                val media = oldObject.getObject(propertyName = "media")
                                var position = 0L
                                if (media != null) position = media.getValue(propertyName = "position", Long::class)
                                if (position > 0) set("playState", 5L)
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 30) {
                    Logd(TAG, "migrating DB from below 30")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            val media = oldObject.getObject(propertyName = "media")
                            if (media != null) {
                                val playedDuration = media.getValue(propertyName = "playedDuration", Long::class)
                                Logd(TAG, "position: $playedDuration")
                                if (playedDuration > 0L) {
                                    val newMedia = newObject.getObject(propertyName = "media")
                                    newMedia?.set("timeSpent", playedDuration)
                                }
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 37) {
                    Logd(TAG, "migrating DB from below 37")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
//                            Logd(TAG, "start: ${getNullableValue("title", String::class)}")
                            val media = oldObject.getObject(propertyName = "media")
                            if (media != null) {
                                set("fileUrl", media.getNullableValue("fileUrl", String::class))
                                set("downloadUrl", media.getNullableValue("downloadUrl", String::class))
                                set("mimeType", media.getNullableValue("mimeType", String::class))
//                                Logd(TAG, "after mimeType")
                                set("downloaded", media.getValue("downloaded", Boolean::class))
//                                Logd(TAG, "after downloaded")
                                set("downloadTime", media.getValue("downloadTime", Long::class))
                                set("duration", media.getValue("duration", Long::class))
                                set("position", media.getValue("position", Long::class))
                                set("lastPlayedTime", media.getValue("lastPlayedTime", Long::class))
                                set("startPosition", media.getValue("startPosition", Long::class))
//                                Logd(TAG, "after startPosition")
                                set("playedDurationWhenStarted", media.getValue("playedDurationWhenStarted", Long::class))
                                set("playedDuration", media.getValue("playedDuration", Long::class))
                                set("timeSpentOnStart", media.getValue("timeSpentOnStart", Long::class))
                                set("startTime", media.getValue("startTime", Long::class))
//                                Logd(TAG, "after startTime")
                                set("timeSpent", media.getValue("timeSpent", Long::class))
                                set("size", media.getValue("size", Long::class))
                                set("playbackCompletionTime", media.getValue("playbackCompletionTime", Long::class))
//                                Logd(TAG, "after all")
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 38) {
                    Logd(TAG, "migrating DB from below 38")
                    mContext.enumerate(className = "Feed") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            val pref = oldObject.getObject(propertyName = "preferences")
                            if (pref != null) {
                                set("useWideLayout", pref.getValue("useWideLayout", Boolean::class))
                                set("keepUpdated", pref.getValue("keepUpdated", Boolean::class))
                                set("username", pref.getNullableValue("username", String::class))
                                set("password", pref.getNullableValue("password", String::class))
//                                Logd(TAG, "after password")
                                set("videoMode", pref.getValue("videoMode", Long::class))
                                set("playSpeed", pref.getValue("playSpeed", Float::class))
                                set("introSkip", pref.getValue("introSkip", Long::class))
                                set("endingSkip", pref.getValue("endingSkip", Long::class))
                                set("autoDelete", pref.getValue("autoDelete", Long::class))
//                                Logd(TAG, "after autoDelete")
                                set("audioType", pref.getValue("audioType", Long::class))
                                set("volumeAdaption", pref.getValue("volumeAdaption", Long::class))
                                set("audioQuality", pref.getValue("audioQuality", Long::class))
                                set("videoQuality", pref.getValue("videoQuality", Long::class))
                                set("prefStreamOverDownload", pref.getValue("prefStreamOverDownload", Boolean::class))
                                set("filterString", pref.getValue("filterString", String::class))
//                                Logd(TAG, "after filterString")
                                set("sortOrderCode", pref.getValue("sortOrderCode", Long::class))
                                val tagsSet = getValueSet<String>("tags")
                                tagsSet.addAll(pref.getValueSet<String>("tags"))
                                set("autoDownload", pref.getValue("autoDownload", Boolean::class))
                                set("queueId", pref.getValue("queueId", Long::class))
//                                Logd(TAG, "after queueId")
                                set("autoAddNewToQueue", pref.getValue("autoAddNewToQueue", Boolean::class))
                                set("autoDLInclude", pref.getNullableValue("autoDLInclude", String::class))
                                set("autoDLExclude", pref.getNullableValue("autoDLExclude", String::class))
                                set("autoDLMinDuration", pref.getValue("autoDLMinDuration", Long::class))
//                                Logd(TAG, "after autoDLMinDuration")
                                set("markExcludedPlayed", pref.getValue("markExcludedPlayed", Boolean::class))
                                set("autoDLMaxEpisodes", pref.getValue("autoDLMaxEpisodes", Long::class))
                                set("countingPlayed", pref.getValue("countingPlayed", Boolean::class))
                                set("autoDLPolicyCode", pref.getValue("autoDLPolicyCode", Long::class))
//                                Logd(TAG, "after all")
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 39) {
                    Logd(TAG, "migrating DB from below 39")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            try {
                                val fileUrl = oldObject.getNullableValue("fileUrl", String::class)
                                Logd(TAG, "fileUrl: $fileUrl")
                                if (!fileUrl.isNullOrBlank()) {
                                    val f = File(fileUrl)
                                    val uri = Uri.fromFile(f)
                                    set("fileUrl", uri.toString())
                                }
                            } catch (e: Throwable) {
                                Logs(TAG, e, " can't create uri from fileUrl")
                                set("fileUrl", "")
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 45) {
                    Logd(TAG, "migrating DB from below 45")
                    mContext.enumerate(className = "Feed") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            if (oldObject.getValue<Long>(fieldName = "autoDLPolicyCode") == 3L) {
                                Logd(TAG, "setting autoDLSoon to true")
                                set("autoDLSoon", true)
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 47) {
                    Logd(TAG, "migrating DB from below 47")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            val playState = oldObject.getValue<Long>(fieldName = "playState")
                            if (playState >= EpisodeState.SKIPPED.code.toLong()) {
                                var playTime = oldObject.getValue<Long>(fieldName = "lastPlayedTime")
                                Logd(TAG, "setting playStateSetTime to $playTime")
                                if (playTime == 0L) playTime = System.currentTimeMillis()
                                set("playStateSetTime", playTime)
                            }
                        }
                    }
                }
                if (oldRealm.schemaVersion() < 48) {
                    Logd(TAG, "migrating DB from below 48")
                    mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                        newObject?.run {
                            val playState = oldObject.getValue<Long>(fieldName = "playState")
                            when (playState) {
                                6L -> set("playState", 9L)
                                12L -> set("playState", 7L)
                                15L -> set("playState", 8L)
                            }
                        }
                    }
                }
            }).build()
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
//        if (BuildConfig.DEBUG) {
//            val stackTrace = Thread.currentThread().stackTrace
//            val caller = if (stackTrace.size > 3) stackTrace[3] else null
//            Logd(TAG, "${caller?.className}.${caller?.methodName} upsert: ${entity.javaClass.simpleName}")
//        }
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
//        if (BuildConfig.DEBUG) {
//            val stackTrace = Thread.currentThread().stackTrace
//            val caller = if (stackTrace.size > 3) stackTrace[3] else null
//            Logd(TAG, "${caller?.className}.${caller?.methodName} upsertBlk: ${entity.javaClass.simpleName}")
//        }
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
    
    fun runOnIOScope(block: suspend () -> Unit) : Job {
        return ioScope.launch {
            if (Dispatchers.IO == coroutineContext[ContinuationInterceptor]) block()
            else withContext(Dispatchers.IO) { block() }
        }
    }

//    private var subscriptionLock = false
    private val subscriptionMutex = Mutex()

    data class MonitorEntity(
        val tag: String,
        val onChanges: suspend (Episode, fields: Array<String>)->Unit,
        val onInit: (suspend (Episode)->Unit)? = null)

//    data class EpisodeMonitorSpec(
//        val episode: Episode,
//        val entity: MonitorEntity
//    )

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
//                while (subscriptionLock) delay(100)
//                if (subscriptionLock) return@collect
//                subscriptionLock = true
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
//                subscriptionLock = false
            }
        }
    }

    fun subscribeEpisode(episode: Episode, entity: MonitorEntity) {
        CoroutineScope(Dispatchers.IO).launch {
//            while (subscriptionLock) delay(100)
//            subscriptionLock = true
            subscriptionMutex.withLock {
                var ms = idMonitorsMap[episode.id]
                if (ms == null) {
                    ms = EpisodeMonitors()
                    ms.entities.add(entity)
                    ms.job = episodeMonitor(episode)
                    idMonitorsMap[episode.id] = ms
                } else {
                    val e = ms.entities.firstOrNull { it.tag == entity.tag }
                    if (e == null) ms.entities.add(entity)
                }
                Logd(TAG, "subscribeEpisode ${entity.tag} ${episode.id} ${episode.title}")
                Logd(TAG, "subscribeEpisode idMonitorsMap: ${idMonitorsMap.size}")
                for ((k, v) in idMonitorsMap.entries.toList()) for (e in v.entities) Logd(TAG, "subscribeEpisode idMonitorsMap $k tag: ${e.tag} job: ${v.job != null}")
//            subscriptionLock = false
            }
        }
    }

//    fun subscribeEpisode(specs: List<EpisodeMonitorSpec>) {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (subscriptionLock) delay(100)
//            subscriptionLock = true
//            for (s in specs) {
//                val episode = s.episode
//                val entity = s.entity
//                var ms = idMonitorsMap[episode.id]
//                if (ms == null) {
//                    ms = EpisodeMonitors()
//                    ms.entities.add(entity)
//                    ms.job = episodeMonitor(episode)
//                    idMonitorsMap[episode.id] = ms
//                } else {
//                    val e = ms.entities.firstOrNull { it.tag == entity.tag }
//                    if (e == null) ms.entities.add(entity)
//                }
//                Logd(TAG, "subscribeEpisode ${entity.tag} ${episode.id} ${episode.title}")
//            }
//            Logd(TAG, "subscribeEpisode idMonitorsMap: ${idMonitorsMap.size}")
//            for ((k,v) in idMonitorsMap.entries.toList()) for (e in v.entities) Logd(TAG, "subscribeEpisode idMonitorsMap $k tag: ${e.tag} job: ${v.job != null}")
//            subscriptionLock = false
//        }
//    }

    fun unsubscribeEpisode(episode: Episode, tag: String) {
        CoroutineScope(Dispatchers.IO).launch {
//            while (subscriptionLock) delay(100)
//            subscriptionLock = true
            subscriptionMutex.withLock {
                var ms = idMonitorsMap[episode.id]
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
//            subscriptionLock = false
            }
        }
    }

//    fun unsubscribeEpisode(ids: List<Long>, tag: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (subscriptionLock) delay(100)
//            Logd(TAG, "unsubscribeEpisode ids: ${ids.size}")
//            subscriptionLock = true
//            for (id in ids) {
//                var ms = idMonitorsMap[id]
//                if (ms != null) {
//                    try {
//                        ms.entities.removeIf { it.tag == tag }
//                        if (ms.entities.isEmpty()) {
//                            ms.job?.cancel()
//                            idMonitorsMap.remove(id)
//                        }
//                    } catch (e: Throwable) { Logs(TAG, e, "unsubscribe episode failed $tag $id") }
//                    Logd(TAG, "unsubscribeEpisode $tag $id")
//                }
//            }
//            Logd(TAG, "unsubscribeEpisode idMonitorsMap: ${idMonitorsMap.size}")
//            for ((k,v) in idMonitorsMap.entries.toList()) for (e in v.entities) Logd(TAG, "unsubscribeEpisode idMonitorsMap $k tag: ${e.tag} job: ${v.job != null}")
//            subscriptionLock = false
//        }
//    }

    fun feedMonitor(feed: Feed, onChanges: suspend (Feed, fields: Array<String>)->Unit): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            val item_ = realm.query(Feed::class).query("id == ${feed.id}").first()
            Logd(TAG, "start monitoring feed: ${feed.id} ${feed.title}")
            val episodeFlow = item_.asFlow()
            episodeFlow.collect { changes: SingleQueryChange<Feed> ->
                when (changes) {
                    is UpdatedObject -> {
                        Logd(TAG, "feedMonitor UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                        if (feed.id == changes.obj.id) {
                            onChanges(changes.obj, changes.changedFields)
                        } else Loge(TAG, "feedMonitor index out bound")
                    }
                    else -> {}
                }
            }
        }
    }
}