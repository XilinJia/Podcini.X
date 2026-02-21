package ac.mdiq.podcini.net.sync

import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink.clearQueue
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.syncPrefs
import ac.mdiq.podcini.storage.database.upsertBlk

/**
 * Manages preferences for accessing gpodder.net service and other sync providers
 */
object SynchronizationCredentials {
    var username: String?
        get() = syncPrefs.PREF_USERNAME
        set(username) {
            upsertBlk(syncPrefs) { it.PREF_USERNAME = username }
        }

    var password: String?
        get() = syncPrefs.PREF_PASSWORD
        set(password) {
            upsertBlk(syncPrefs) { it.PREF_PASSWORD = password }
        }

    var deviceID: String?
        get() = syncPrefs.PREF_DEVICEID
        set(deviceID) {
            upsertBlk(syncPrefs) { it.PREF_DEVICEID = deviceID }
        }

    var hosturl: String?
        get() = syncPrefs.PREF_HOSTNAME
        set(value) {
            upsertBlk(syncPrefs) { it.PREF_HOSTNAME = value }
        }

    var hostport: Int
        get() = syncPrefs.PREF_HOSTPORT
        set(value) {
            upsertBlk(syncPrefs) { it.PREF_HOSTPORT = value }
        }

    @Synchronized
    fun clear() {
        username = null
        password = null
        deviceID = null
        clearQueue()
        upsertBlk(appPrefs) { it.gpodnet_notifications = true }
    }
}
