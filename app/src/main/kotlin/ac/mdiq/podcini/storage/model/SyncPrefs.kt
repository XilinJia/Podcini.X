package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class SyncPrefs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var QUEUED_EPISODE_ACTIONS: String = "[]"

    var QUEUED_FEEDS_REMOVED: String = "[]"

    var QUEUED_FEEDS_ADDED: String = "[]"

    var PREF_USERNAME: String? = null

    var PREF_PASSWORD: String? = null

    var PREF_DEVICEID: String? = null

    var PREF_HOSTNAME: String? = null

    var PREF_HOSTPORT: Int = 0

    var WIFI_SYNC_ENABLED: Boolean = false

    var SELECTED_SYNC_PROVIDER: String? = null

    var LAST_SYNC_ATTEMPT_SUCCESS: Boolean = false

    var LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP: Long = 0L

    var LAST_SUBSCRIPTION_SYNC_TIMESTAMP: Long = 0L

    var LAST_SYNC_ATTEMPT_TIMESTAMP: Long = 0L

    // play rating stuff
    var KEY_FIRST_START_DATE: Long = 0L

    var KEY_NUMBER_OF_REVIEWS: Int = 0

    var KEY_RATED: Boolean = false
}