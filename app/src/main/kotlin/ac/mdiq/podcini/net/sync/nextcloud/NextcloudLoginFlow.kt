package ac.mdiq.podcini.net.sync.nextcloud

import ac.mdiq.podcini.net.sync.HostnameParser
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import android.content.Intent
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URL

class NextcloudLoginFlow(
    private val httpClient: HttpClient,
    private val rawHostUrl: String,
    private val context: Context,
    private val callback: AuthenticationCallback) {

    private var job1: Job? = null
    private var job2: Job? = null
    private val hostname = HostnameParser(rawHostUrl)
    private var token: String? = null
    private var endpoint: String? = null
    private var isWaitingForBrowser:Boolean = false

    fun saveInstanceState(): MutableList<String?> {
        val state = mutableListOf<String?>()
        state.add(rawHostUrl)
        state.add(token)
        state.add(endpoint)
        return state
    }

    fun start() {
        if (token != null) {
            poll()
            return
        }

        job1 = CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URI(hostname.scheme, null, hostname.host, hostname.port, hostname.subfolder + "/index.php/login/v2", null, null).toURL()
                    val result = doRequest(url, "")
                    val loginUrl = result.getString("login")
                    token = result.getJSONObject("poll").getString("token")
                    endpoint = result.getJSONObject("poll").getString("endpoint")
                    loginUrl
                }
                withContext(Dispatchers.Main) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, result.toSafeUri())
                    context.startActivity(browserIntent)
                    isWaitingForBrowser = true
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                token = null
                endpoint = null
                callback.onNextcloudAuthError(e.localizedMessage)
            }
        }
    }

    private suspend fun <T> retryIO(retries: Int = 3, delay: Long = 1000, block: suspend () -> T): T {
        var attempt = 0
        while (attempt < retries) {
            try { return block() }
            catch (e: Throwable) {
                if (attempt < retries - 1) {
                    delay(delay)
                    attempt++
                } else throw e
            }
        }
        throw RuntimeException("Maximum retries exceeded")
    }

    // trigger poll only when returning from the browser
    fun onResume(){
        if (token != null && isWaitingForBrowser){
            poll()
            isWaitingForBrowser = false
        }
    }

    fun poll() {
        job2 = CoroutineScope(Dispatchers.IO).launch {
            try {
                // time out in 5 minutes, retry 5 times
                val result = withTimeout(5 * 60 * 1000) { retryIO(5) { doRequest(URI.create(endpoint).toURL(), "token=$token") } }
                withContext(Dispatchers.Main) { callback.onNextcloudAuthenticated(result.getString("server"), result.getString("loginName"), result.getString("appPassword")) }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    token = null
                    endpoint = null
                    callback.onNextcloudAuthError(e.localizedMessage)
                }
            }
        }
    }

    fun cancel() {
        job2?.cancel()
        job1?.cancel()
    }

    @Throws(IOException::class, JSONException::class)
    private suspend fun doRequest(url: URL, bodyContent: String): JSONObject {
        Logd(TAG, "doRequest $url $bodyContent")
        val response: HttpResponse = httpClient.post(url) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(bodyContent)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value != 200) {
//            response.close()
            throw IOException("Return code " + response.status)
        }
        Logd(TAG, "doRequest body: $responseBody ")
        return JSONObject(responseBody)
    }

    interface AuthenticationCallback {
        fun onNextcloudAuthenticated(server: String, username: String, password: String)
        fun onNextcloudAuthError(errorMessage: String?)
    }

    companion object {
        private val TAG: String = NextcloudLoginFlow::class.simpleName ?: "Anonymous"

        fun fromInstanceState(httpClient: HttpClient, context: Context, callback: AuthenticationCallback, instanceState: MutableList<String>): NextcloudLoginFlow {
            val flow = NextcloudLoginFlow(httpClient, instanceState[0], context, callback)
            flow.token = instanceState[1]
            flow.endpoint = instanceState[2]
            return flow
        }
    }
}
