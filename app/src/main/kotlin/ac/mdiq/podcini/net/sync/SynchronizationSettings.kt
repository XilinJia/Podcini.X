package ac.mdiq.podcini.net.sync

import ac.mdiq.podcini.storage.database.syncPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.utils.nowInMillis

object SynchronizationSettings {
    const val LAST_SYNC_ATTEMPT_TIMESTAMP: String = "last_sync_attempt_timestamp"
    private const val NAME = "synchronization"
    private const val WIFI_SYNC_ENABLED = "wifi_sync_enabled"
    private const val SELECTED_SYNC_PROVIDER = "selected_sync_provider"
    private const val LAST_SYNC_ATTEMPT_SUCCESS = "last_sync_attempt_success"
    private const val LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = "last_episode_actions_sync_timestamp"
    private const val LAST_SUBSCRIPTION_SYNC_TIMESTAMP = "last_sync_timestamp"

    val isProviderConnected: Boolean
        get() = selectedSyncProviderKey != null

    fun resetTimestamps() {
        upsertBlk(syncPrefs) {
            it.LAST_SUBSCRIPTION_SYNC_TIMESTAMP = 0L
            it.LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = 0L
            it.LAST_SYNC_ATTEMPT_TIMESTAMP = 0L
        }
    }

    val isLastSyncSuccessful: Boolean
        get() = syncPrefs.LAST_SYNC_ATTEMPT_SUCCESS

    val lastSyncAttempt: Long
        get() = syncPrefs.LAST_SYNC_ATTEMPT_TIMESTAMP

    fun setSelectedSyncProvider(provider: SynchronizationProviderViewData?) {
        upsertBlk(syncPrefs) { it.SELECTED_SYNC_PROVIDER = provider?.identifier }
    }

    val selectedSyncProviderKey: String?
        get() = syncPrefs.SELECTED_SYNC_PROVIDER

    fun setWifiSyncEnabled(stat: Boolean) {
        upsertBlk(syncPrefs) { it.WIFI_SYNC_ENABLED = stat }
    }

    val wifiSyncEnabledKey: Boolean
        get() = syncPrefs.WIFI_SYNC_ENABLED

    fun updateLastSynchronizationAttempt() {
        upsertBlk(syncPrefs) { it.LAST_SYNC_ATTEMPT_TIMESTAMP = nowInMillis() }
    }

    fun setLastSynchronizationAttemptSuccess(isSuccess: Boolean) {
        upsertBlk(syncPrefs) { it.LAST_SYNC_ATTEMPT_SUCCESS = isSuccess }
    }

    val lastSubscriptionSynchronizationTimestamp: Long
        get() = syncPrefs.LAST_SUBSCRIPTION_SYNC_TIMESTAMP

    fun setLastSubscriptionSynchronizationAttemptTimestamp(newTimeStamp: Long) {
        upsertBlk(syncPrefs) { it.LAST_SUBSCRIPTION_SYNC_TIMESTAMP = newTimeStamp }
    }

    val lastEpisodeActionSynchronizationTimestamp: Long
        get() = syncPrefs.LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP

    fun setLastEpisodeActionSynchronizationAttemptTimestamp(timestamp: Long) {
        upsertBlk(syncPrefs) { it.LAST_EPISODE_ACTIONS_SYNC_TIMESTAMP = timestamp }
    }

}
