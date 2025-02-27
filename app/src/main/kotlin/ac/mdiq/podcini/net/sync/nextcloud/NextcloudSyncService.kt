package ac.mdiq.podcini.net.sync.nextcloud

import ac.mdiq.podcini.net.sync.HostnameParser
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.EpisodeActionChanges
import ac.mdiq.podcini.net.sync.model.GpodnetUploadChangesResponse
import ac.mdiq.podcini.net.sync.model.ISyncService
import ac.mdiq.podcini.net.sync.model.SubscriptionChanges
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.net.sync.model.UploadChangesResponse
import ac.mdiq.podcini.util.Logd
import java.io.IOException
import java.net.MalformedURLException
import okhttp3.Credentials.basic
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.lang3.StringUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.min

class NextcloudSyncService(private val httpClient: OkHttpClient, baseHosturl: String?, private val username: String, private val password: String) : ISyncService {
    private val hostname = HostnameParser(baseHosturl)

    override fun login() {}

    @Throws(SyncServiceException::class)
    override fun getSubscriptionChanges(lastSync: Long): SubscriptionChanges {
        Logd(TAG, "getSubscriptionChanges")
        try {
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/subscriptions")
            url.addQueryParameter("since", "" + lastSync)
            val responseString = performRequest(url, "GET", null)
            val json = JSONObject(responseString)
            return readSubscriptionChangesFromJsonObject(json)
        } catch (e: JSONException) { throw SyncServiceException(e)
        } catch (e: MalformedURLException) { throw SyncServiceException(e)
        } catch (e: Exception) { throw SyncServiceException(e) }
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    override fun uploadSubscriptionChanges(added: List<String>, removed: List<String>): UploadChangesResponse {
        Logd(TAG, "uploadSubscriptionChanges")
        try {
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/subscription_change/create")
            val requestObject = JSONObject()
            requestObject.put("add", JSONArray(added))
            requestObject.put("remove", JSONArray(removed))
            val requestBody = RequestBody.create("application/json".toMediaType(), requestObject.toString())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) { throw NextcloudSynchronizationServiceException(e) }
        return GpodnetUploadChangesResponse(System.currentTimeMillis() / 1000, HashMap())
    }

    @Throws(SyncServiceException::class)
    override fun getEpisodeActionChanges(timestamp: Long): EpisodeActionChanges {
        Logd(TAG, "getEpisodeActionChanges")
        try {
            val uri: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/episode_action")
            uri.addQueryParameter("since", "" + timestamp)
            val responseString = performRequest(uri, "GET", null)
            val json = JSONObject(responseString)
            return readEpisodeActionsFromJsonObject(json)
        } catch (e: JSONException) { throw SyncServiceException(e)
        } catch (e: MalformedURLException) { throw SyncServiceException(e)
        } catch (e: Exception) { throw SyncServiceException(e) }
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    override fun uploadEpisodeActions(queuedEpisodeActions: List<EpisodeAction>): UploadChangesResponse {
        Logd(TAG, "uploadEpisodeActions")
        var i = 0
        while (i < queuedEpisodeActions.size) {
            uploadEpisodeActionsPartial(queuedEpisodeActions, i, min(queuedEpisodeActions.size.toDouble(), (i + UPLOAD_BULK_SIZE).toDouble()).toInt())
            i += UPLOAD_BULK_SIZE
        }
        return NextcloudGpodderEpisodeActionPostResponse(System.currentTimeMillis() / 1000)
    }

    @Throws(NextcloudSynchronizationServiceException::class)
    private fun uploadEpisodeActionsPartial(queuedEpisodeActions: List<EpisodeAction>, from: Int, to: Int) {
        Logd(TAG, "uploadEpisodeActionsPartial")
        try {
            val list = JSONArray()
            for (i in from until to) {
                val episodeAction = queuedEpisodeActions[i]
                val obj = episodeAction.writeToJsonObjectForServer()
                if (obj != null) list.put(obj)
            }
            val url: HttpUrl.Builder = makeUrl("/index.php/apps/gpoddersync/episode_action/create")
            val requestBody = RequestBody.create("application/json".toMediaType(), list.toString())
            performRequest(url, "POST", requestBody)
        } catch (e: Exception) { throw NextcloudSynchronizationServiceException(e) }
    }

    @Throws(IOException::class)
    private fun performRequest(url: HttpUrl.Builder, method: String, body: RequestBody?): String {
        Logd(TAG, "performRequest $url $method $body")
        val request: Request = Request.Builder().url(url.build()).header("Authorization", basic(username, password)).header("Accept", "application/json").method(method, body).build()
        val response = httpClient.newCall(request).execute()
        if (response.code != 200) throw IOException("Response code: " + response.code)
        return response.body?.string()?:""
    }

    private fun makeUrl(path: String): HttpUrl.Builder {
        Logd(TAG, "makeUrl")
        val builder = HttpUrl.Builder()
        if (hostname.scheme != null) builder.scheme(hostname.scheme!!)
        if (hostname.host != null) builder.host(hostname.host!!)
        return builder.port(hostname.port).addPathSegments(StringUtils.stripStart(hostname.subfolder + path, "/"))
    }

    override fun logout() {}

    @Throws(JSONException::class)
    fun readSubscriptionChangesFromJsonObject(`object`: JSONObject): SubscriptionChanges {
        val added: MutableList<String> = mutableListOf()
        val jsonAdded = `object`.getJSONArray("add")
        for (i in 0 until jsonAdded.length()) {
            var addedUrl = jsonAdded.getString(i)
            // gpodder escapes colons unnecessarily
            addedUrl = addedUrl.replace("%3A", ":")
            added.add(addedUrl)
        }

        val removed: MutableList<String> = mutableListOf()
        val jsonRemoved = `object`.getJSONArray("remove")
        for (i in 0 until jsonRemoved.length()) {
            var removedUrl = jsonRemoved.getString(i)
            // gpodder escapes colons unnecessarily
            removedUrl = removedUrl.replace("%3A", ":")
            removed.add(removedUrl)
        }

        val timestamp = `object`.getLong("timestamp")
        return SubscriptionChanges(added, removed, timestamp)
    }

    @Throws(JSONException::class)
    fun readEpisodeActionsFromJsonObject(`object`: JSONObject): EpisodeActionChanges {
        val episodeActions: MutableList<EpisodeAction> = mutableListOf()

        val timestamp = `object`.getLong("timestamp")
        val jsonActions = `object`.getJSONArray("actions")
        for (i in 0 until jsonActions.length()) {
            val jsonAction = jsonActions.getJSONObject(i)
            val episodeAction = readFromJsonObject(jsonAction)
            if (episodeAction != null) episodeActions.add(episodeAction)
        }
        return EpisodeActionChanges(episodeActions, timestamp)
    }

    private class NextcloudGpodderEpisodeActionPostResponse(epochSecond: Long) : UploadChangesResponse(epochSecond)

    class NextcloudSynchronizationServiceException(e: Throwable?) : SyncServiceException(e)

    companion object {
        const val TAG = "NextcloudSyncService"
        private const val UPLOAD_BULK_SIZE = 30
    }
}

