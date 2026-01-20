package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.model.ARCHIVED_VOLUME_ID
import ac.mdiq.podcini.storage.model.AppAttribs
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
import ac.mdiq.podcini.storage.model.Timer
import ac.mdiq.podcini.storage.model.Todo
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.showStackTrace
import android.net.Uri
import io.github.xilinjia.krdb.MutableRealm
import io.github.xilinjia.krdb.Realm
import io.github.xilinjia.krdb.RealmConfiguration
import io.github.xilinjia.krdb.UpdatePolicy
import io.github.xilinjia.krdb.dynamic.DynamicMutableRealmObject
import io.github.xilinjia.krdb.dynamic.DynamicRealmObject
import io.github.xilinjia.krdb.dynamic.getNullableValue
import io.github.xilinjia.krdb.dynamic.getValue
import io.github.xilinjia.krdb.dynamic.getValueList
import io.github.xilinjia.krdb.dynamic.getValueSet
import io.github.xilinjia.krdb.ext.isManaged
import io.github.xilinjia.krdb.ext.toRealmList
import io.github.xilinjia.krdb.ext.toRealmSet
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.TypedRealmObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.ContinuationInterceptor

private const val TAG: String = "RealmDB"

private val ioScope = CoroutineScope(Dispatchers.IO)

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
        SubscriptionsPrefs::class,
        FacetsPrefs::class,
        SleepPrefs::class
    )).name("Podcini.realm").schemaVersion(101)
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
                    newObject?.run { //                            Logd(TAG, "start: ${getNullableValue("title", String::class)}")
                        val media = oldObject.getObject(propertyName = "media")
                        if (media != null) {
                            set("fileUrl", media.getNullableValue("fileUrl", String::class))
                            set("downloadUrl", media.getNullableValue("downloadUrl", String::class))
                            set("mimeType", media.getNullableValue("mimeType", String::class)) //                                Logd(TAG, "after mimeType")
                            set("downloaded", media.getValue("downloaded", Boolean::class)) //                                Logd(TAG, "after downloaded")
                            set("downloadTime", media.getValue("downloadTime", Long::class))
                            set("duration", media.getValue("duration", Long::class))
                            set("position", media.getValue("position", Long::class))
                            set("lastPlayedTime", media.getValue("lastPlayedTime", Long::class))
                            set("startPosition", media.getValue("startPosition", Long::class)) //                                Logd(TAG, "after startPosition")
                            set("playedDurationWhenStarted", media.getValue("playedDurationWhenStarted", Long::class))
                            set("playedDuration", media.getValue("playedDuration", Long::class))
                            set("timeSpentOnStart", media.getValue("timeSpentOnStart", Long::class))
                            set("startTime", media.getValue("startTime", Long::class)) //                                Logd(TAG, "after startTime")
                            set("timeSpent", media.getValue("timeSpent", Long::class))
                            set("size", media.getValue("size", Long::class))
                            set("playbackCompletionTime", media.getValue("playbackCompletionTime", Long::class)) //                                Logd(TAG, "after all")
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
                            set("password", pref.getNullableValue("password", String::class)) //                                Logd(TAG, "after password")
                            set("videoMode", pref.getValue("videoMode", Long::class))
                            set("playSpeed", pref.getValue("playSpeed", Float::class))
                            set("introSkip", pref.getValue("introSkip", Long::class))
                            set("endingSkip", pref.getValue("endingSkip", Long::class))
                            set("autoDelete", pref.getValue("autoDelete", Long::class)) //                                Logd(TAG, "after autoDelete")
                            set("audioType", pref.getValue("audioType", Long::class))
                            set("volumeAdaption", pref.getValue("volumeAdaption", Long::class))
                            set("audioQuality", pref.getValue("audioQuality", Long::class))
                            set("videoQuality", pref.getValue("videoQuality", Long::class))
                            set("prefStreamOverDownload", pref.getValue("prefStreamOverDownload", Boolean::class))
                            set("filterString", pref.getValue("filterString", String::class)) //                                Logd(TAG, "after filterString")
                            set("sortOrderCode", pref.getValue("sortOrderCode", Long::class))
                            val tagsSet = getValueSet<String>("tags")
                            tagsSet.addAll(pref.getValueSet<String>("tags"))
                            set("autoDownload", pref.getValue("autoDownload", Boolean::class))
                            set("queueId", pref.getValue("queueId", Long::class)) //                                Logd(TAG, "after queueId")
                            set("autoAddNewToQueue", pref.getValue("autoAddNewToQueue", Boolean::class))
                            set("autoDLInclude", pref.getNullableValue("autoDLInclude", String::class))
                            set("autoDLExclude", pref.getNullableValue("autoDLExclude", String::class))
                            set("autoDLMinDuration", pref.getValue("autoDLMinDuration", Long::class)) //                                Logd(TAG, "after autoDLMinDuration")
                            set("markExcludedPlayed", pref.getValue("markExcludedPlayed", Boolean::class))
                            set("autoDLMaxEpisodes", pref.getValue("autoDLMaxEpisodes", Long::class))
                            set("countingPlayed", pref.getValue("countingPlayed", Boolean::class))
                            set("autoDLPolicyCode", pref.getValue("autoDLPolicyCode", Long::class)) //                                Logd(TAG, "after all")
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
            if (oldRealm.schemaVersion() < 55) {
                Logd(TAG, "migrating DB from below 55")
                mContext.enumerate(className = "Episode") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
                    newObject?.run {
                        val playState = oldObject.getValue<Long>(fieldName = "playState")
                        if (playState == 7L) {
                            val t = System.currentTimeMillis() + (8.64e7 * (10..100).random()).toLong()
                            set("repeatTime", t)
                        }
                    }
                }
            }
            if (oldRealm.schemaVersion() < 57) {
                Logd(TAG, "migrating DB from below 57")
                val queues = newRealm.query("PlayQueue").find()
                val t = System.currentTimeMillis()
                var c = 0L
                for (queue in queues) {
                    val id = queue.getValue<Long>("id")
                    val oldQueue = oldRealm.query("PlayQueue","id == $0", id).first().find()
                    if (oldQueue != null) {
                        Logd(TAG, "migrating DB old queue: $id")
                        val eids = oldQueue.getValueList<Long>("episodeIds")
                        Logd(TAG, "migrating DB eids: ${eids.size}")
                        val episodes = newRealm.query("Episode", "id IN $0", eids).find()
                        Logd(TAG, "migrating DB episodes: ${episodes.size}")
                        for (e in episodes) e.set("timeInQueue", t + c++)
                        //                            val newEpisodes = queue.getObjectList("episodes")
                        //                            newEpisodes.addAll(episodes)
                        val beids = oldQueue.getValueList<Long>("idsBinList")
                        Logd(TAG, "migrating DB beids: ${beids.size}")
                        val bepisodes = newRealm.query("Episode", "id IN $0", beids).find()
                        Logd(TAG, "migrating DB bepisodes: ${bepisodes.size}")
                        for (e in bepisodes) e.set("timeOutQueue", t + c++)
                    }
                }
            }
            if (oldRealm.schemaVersion() < 59) {
                Logd(TAG, "migrating DB from below 59")
                val queues = newRealm.query("PlayQueue").find()
                for (queue in queues) queue.set("launchAutoEQDlWhenEmpty", true)
            }
            if (oldRealm.schemaVersion() < 65) {
                Logd(TAG, "migrating DB from below 65")
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
                Logd(TAG, "migrating DB from below 71")
                val queues = newRealm.query("PlayQueue").find()
                for (queue in queues) queue.set("playInSequence", true)
            }
            if (oldRealm.schemaVersion() < 72) {
                Logd(TAG, "migrating DB from below 72")
                val appAttribs = newRealm.query("AppAttribs").find()
                if (appAttribs.isNotEmpty()) {
                    val feedsOld = oldRealm.query("Feed").find()
                    val tagsSet = mutableSetOf<String>()
                    val langsSet = mutableSetOf<String>()
                    for (f in feedsOld) {
                        val id = f.getValue<Long>("id")
                        val lang = f.getNullableValue<String>("language") ?: ""
                        Logd(TAG, "migrating languages [$lang]")
                        langsSet.add(lang)

                        Logd(TAG, "migrating feed language [$lang]")
                        val fNew = newRealm.query("Feed", "id == $id").first().find()
                        if (fNew != null) {
                            val langs = fNew.getValueList<String>("languages")
                            langs.add(lang)
                        }

                        val tags = f.getValueSet<String>("tags").toRealmList()
                        Logd(TAG, "migrating tags [$tags]")
                        tagsSet.addAll(tags)
                    }
                    val aa = appAttribs[0]
                    Logd(TAG, "migrating all tags")
                    val fTags = aa.getValueList<String>("feedTags")
                    fTags.addAll(tagsSet.toRealmList())
                    Logd(TAG, "migrating all languages")
                    val languages = aa.getValueList<String>("languages")
                    languages.addAll(langsSet.toRealmList())
                }
            }
            if (oldRealm.schemaVersion() < 83) {
                Logd(TAG, "migrating DB from below 83")
                val attrOld = oldRealm.query("AppAttribs").find()
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrOld.isNotEmpty() && attrNew.isNotEmpty()) {
                    Logd(TAG, "migrating AppAttribs languages")
                    val langsOld = attrOld[0].getValueList<String>("languages").toRealmSet()
                    val langsNew = attrNew[0].getValueSet<String>("langSet")
                    langsNew.addAll(langsOld)
                    Logd(TAG, "migrating AppAttribs feedTags")
                    val fTagsOld = attrOld[0].getValueList<String>("feedTags").toRealmSet()
                    val fTagsNew = attrNew[0].getValueSet<String>("feedTagSet")
                    fTagsNew.addAll(fTagsOld)
                    Logd(TAG, "migrating AppAttribs episodeTags")
                    val eTagsOld = attrOld[0].getValueList<String>("episodeTags").toRealmSet()
                    val eTagsNew = attrNew[0].getValueSet<String>("episodeTagSet")
                    eTagsNew.addAll(eTagsOld)
                }
                val feedsOld = oldRealm.query("Feed", "languages.@count > 0").find()
                for (f in feedsOld) {
                    val id = f.getValue<Long>("id")
                    val fNew = newRealm.query("Feed", "id == $id").first().find() ?: continue
                    Logd(TAG, "migrating feed languages $id")
                    val langsOld = f.getValueList<String>("languages").toRealmSet()
                    val langsNew = fNew.getValueSet<String>("langSet")
                    langsNew.addAll(langsOld)
                }
            }
            if (oldRealm.schemaVersion() < 85) {
                Logd(TAG, "migrating DB from below 85")
                val feedsOld = oldRealm.query("Feed").find()
                for (f in feedsOld) {
                    val id = f.getValue<Long>("id")
                    val fNew = newRealm.query("Feed", "id == $id").first().find() ?: continue
                    fNew.set("volumeId", -1L)
                }
            }
            if (oldRealm.schemaVersion() < 88) {
                Logd(TAG, "migrating DB from below 88")
                val queues = oldRealm.query("PlayQueue").find()
                val time = System.currentTimeMillis()
                var i = 0L
                for (q in queues) {
                    val qid = q.getValue<Long>("id")
                    val eids = q.getValueList<Long>("episodeIds")
                    Logd(TAG, "migrating queue: $qid with ${eids.size} episodes")
                    var ip = 1L
                    for (eid in eids) {
                        newRealm.copyToRealm(
                            DynamicMutableRealmObject.create(
                                type = "QueueEntry",
                                mapOf(
                                    "id" to time+i++,
                                    "queueId" to qid,
                                    "episodeId" to eid,
                                    "position" to ip
                                )
                            )
                        )
                        ip += 10000L
                    }
                }
            }
            if (oldRealm.schemaVersion() < 95) {
                Logd(TAG, "migrating DB from below 95")
                val attrNew = newRealm.query("AppAttribs").find()
                if (attrNew.isNotEmpty()) attrNew[0].set("topChartCountryCode", Locale.getDefault().country)
            }
            if (oldRealm.schemaVersion() < 98) {
                Logd(TAG, "migrating DB from below 98")
                newRealm.copyToRealm(
                    DynamicMutableRealmObject.create(
                        type = "Volume",
                        mapOf(
                            "id" to ARCHIVED_VOLUME_ID,
                            "name" to "Archived",
                            "parentId" to -1L
                        )
                    )
                )
            }
            if (oldRealm.schemaVersion() < 101) {
                Logd(TAG, "migrating DB from below 101")
                val presSynd = newRealm.query("Feed").query("id == 21").first().find()
                presSynd?.set("volumeId", ARCHIVED_VOLUME_ID)
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
