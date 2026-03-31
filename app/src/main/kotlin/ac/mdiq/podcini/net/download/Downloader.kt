package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.PodciniHttpClient.DownloadRequestKey
import ac.mdiq.podcini.net.download.PodciniHttpClient.getKtorClient
import ac.mdiq.podcini.net.utils.NetworkUtils.getURIFromRequestUrl
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkUrl
import ac.mdiq.podcini.net.utils.NetworkUtils.wasDownloadBlocked
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.utils.freeSpaceAvailable
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.storage.utils.parseDate
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.startTiming
import ac.mdiq.podcini.utils.timeIt
import androidx.compose.runtime.mutableStateMapOf
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.util.network.UnresolvedAddressException
import io.ktor.utils.io.asSource
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.buffered
import okio.buffer
import kotlin.use

abstract class Downloader(val request: DownloadRequest) {

    var cancelled: Boolean
    
    var permanentRedirectUrl: String? = null

    
    val result: DownloadResult

    protected val downloadDispatcher = Dispatchers.IO.limitedParallelism(3)

    init {
        this.request.statusMsg = (R.string.download_pending)
        this.cancelled = false
        this.result = DownloadResult(this.request.feedfileId, this.request.title?:"", null, false, "", this.request.feedfileType, nowInMillis())
    }

    open suspend fun download() { Loge(TAG, "download() method is not implemented") }

    open suspend fun download(cb: suspend (Source)->Unit) { Loge(TAG, "download(cb) method is not implemented") }

    fun cancel() {
        cancelled = true
    }

    protected fun checkResults(isGzip: Boolean, response: HttpResponse): Boolean {
        if (cancelled) onCancelled()
        else {
            // check if size specified in the response header is the same as the size of the
            // written file. This check cannot be made if compression was used
            when {
                !isGzip && request.size != DownloadResult.SIZE_UNKNOWN.toLong() && request.soFar != request.size -> {
                    onFail(DownloadError.ERROR_IO_WRONG_SIZE, "Download completed but size: ${request.soFar} does not equal expected size ${request.size}")
                    return false
                }
                request.size > 0 && request.soFar == 0L -> {
                    onFail(DownloadError.ERROR_IO_ERROR, "Download completed, but nothing was read")
                    return false
                }
                else -> {
                    val lastModified = response.headers[HttpHeaders.LastModified]
                    if (lastModified != null) request.lastModified = lastModified
                    else request.lastModified = response.headers[HttpHeaders.ETag]
                    result.setSuccessful()
                }
            }
        }
        return true
    }

    protected fun callOnFailByResponseCode(response: HttpResponse) {
        val statusCodeInt = response.status.value
        val details = statusCodeInt.toString()
        val error: DownloadError = when (response.status) {
            HttpStatusCode.Unauthorized -> DownloadError.ERROR_UNAUTHORIZED
            HttpStatusCode.Forbidden -> DownloadError.ERROR_FORBIDDEN
            HttpStatusCode.NotFound, HttpStatusCode.Gone -> DownloadError.ERROR_NOT_FOUND
            else -> DownloadError.ERROR_HTTP_DATA_ERROR
        }
        onFail(error, details)
    }

    protected fun checkIfRedirect(response: HttpResponse) {
        val isRedirect = response.status.value in 300..399
        if (!isRedirect) return

        val location = response.headers["Location"] ?: return
        val originalUrl = response.request.url.toString()
        when {
            response.status == HttpStatusCode.MovedPermanently -> { // 301
                Logd(TAG, "Detected permanent redirect from $originalUrl to $location")
                permanentRedirectUrl = location
            }
            location == originalUrl.replace("http://", "https://") -> {
                Logd(TAG, "Treating http->https redirect as permanent: $originalUrl")
                permanentRedirectUrl = location
            }
        }
    }

    protected fun onFail(reason: DownloadError, reasonDetailed: String?) {
        Logs(TAG, "onFail() called with: reason = [$reason], reasonDetailed = [$reasonDetailed]")
        result.isSuccessful = false
        result.reason = reason
        result.addDetail(reasonDetailed?:"")
    }

    protected fun onCancelled() {
        Logd(TAG, "Download was cancelled")
        result.isSuccessful = false
        result.reason = DownloadError.ERROR_DOWNLOAD_CANCELLED
        cancelled = true
    }

    companion object {
        private val TAG: String = Downloader::class.simpleName ?: "Anonymous"

        val downloadStates = mutableStateMapOf<String, DownloadStatus>()

        protected const val BUFFER_SIZE = 8 * 1024

        fun downloaderFor(request: DownloadRequest): Downloader? {
            if (!isNetworkUrl(request.source)) {
                Loge(TAG, "Could not find appropriate downloader for " + request.source)
                return null
            }
            return if (request.feedfileType == RequestTye.FEED.ordinal) FeedDownloader(request) else EpisodeDownloader(request)
        }
    }
}

class FeedDownloader(request: DownloadRequest): Downloader(request) {
    override suspend fun download(cb: suspend (Source)->Unit) {
        Logd(TAG, "starting downloadFeed() source: ${request.source} dest: ${request.destination}")
        if (request.source == null) return

        val destFile = request.destination.toUF()
        val fileExists = destFile.exists()
        Logd(TAG, "destination: ${request.destination} fileExists: $fileExists")

        var startPosition = 0L
        try {
            val uri = getURIFromRequestUrl(request.source)
//            val DownloadRequestKey = AttributeKey<DownloadRequest>("DownloadRequest")
            getKtorClient().prepareGet(uri.toString()){
                attributes.put(DownloadRequestKey, request)
                header(HttpHeaders.CacheControl, "no-store")
                if (uri.scheme == "http") header("Upgrade-Insecure-Requests", "1")
                Logd(TAG, "starting download: ${request.feedfileType} ${uri.scheme}")

                if (!request.lastModified.isNullOrEmpty()) {
                    val lastModified = request.lastModified
                    val lastModifiedDate = parseDate(lastModified)
                    if (lastModifiedDate != null) {
                        val threeDaysAgo = nowInMillis() - 1000 * 60 * 60 * 24 * 3
                        if (lastModifiedDate.toEpochMilliseconds() > threeDaysAgo) {
                            Logd(TAG, "addHeader(\"If-Modified-Since\", \"$lastModified\")")
                            header(HttpHeaders.IfModifiedSince, lastModified)
                        }
                    } else {
                        Logd(TAG, "addHeader(\"If-None-Match\", \"$lastModified\")")
                        header(HttpHeaders.IfNoneMatch, lastModified?:"")
                    }
                }
                val size = destFile.size()?:0
                if (fileExists && size > 0) {
                    request.soFar = size
                    header(HttpHeaders.Range, "bytes=${request.soFar}-")
                    Logd(TAG, "Adding range header: " + request.soFar)
                }
                header(HttpHeaders.AcceptEncoding, "identity")
            }.execute { response ->
                val contentEncodingHeader = response.headers[HttpHeaders.ContentEncoding]
                Logd(TAG, "response.status: ${response.status}")

                when {
                    response.status == HttpStatusCode.NotModified -> {
                        Logd(TAG, "Feed '" + request.source + "' not modified since last update, Download canceled")
                        onCancelled()
                        return@execute
                    }
                    response.status == HttpStatusCode.RequestedRangeNotSatisfiable -> {
                        val lastModified = response.headers[HttpHeaders.LastModified]
                        if (lastModified != null) request.lastModified = lastModified
                        else request.lastModified = response.headers[HttpHeaders.ETag]
                        result.setSuccessful()
                        request.progressPercent = 100
                        return@execute
                    }
                    response.status == HttpStatusCode.PartialContent -> {
                        val contentRangeHeader = if (fileExists) response.headers[HttpHeaders.ContentRange] else null
                        if (fileExists && response.status == HttpStatusCode.PartialContent && !contentRangeHeader.isNullOrEmpty()) {
                            val start = contentRangeHeader.removePrefix("bytes ").substringBefore('-').toLong()
                            if (start != request.soFar) {
                                Logt(TAG, "Unexpected resume offset $start, restarting download")
                                destFile.delete()
                            } else {
                                Logd(TAG, "Resuming download at $start")
                                startPosition = start
                            }
                            val remaining = response.contentLength()
                            request.size = if (remaining != null) remaining + request.soFar else DownloadResult.SIZE_UNKNOWN.toLong()
                        }
                    }
                    response.status == HttpStatusCode.OK -> {
                        destFile.delete()
                        request.soFar = 0
                        request.size = response.contentLength() ?: DownloadResult.SIZE_UNKNOWN.toLong()
                    }
                    response.status == HttpStatusCode.NoContent -> {
                        callOnFailByResponseCode(response)
                        return@execute
                    }
                    !response.status.isSuccess() -> {
                        callOnFailByResponseCode(response)
                        return@execute
                    }
                    else -> throw IOException("Unexpected HTTP status ${response.status}")
                }
                checkIfRedirect(response)

                //            val buffer = ByteArray(BUFFER_SIZE)
                request.statusMsg = (R.string.download_running)
                Logd(TAG, "Getting size of download")
                val contentLength = response.contentLength()
                request.size = if (contentLength != null)  contentLength + request.soFar else -1L
                Logd(TAG, "downloadRequest size is " + request.size)
                if (request.size < 0) request.size = DownloadResult.SIZE_UNKNOWN.toLong()

                response.bodyAsChannel().asSource().buffered().use { source -> cb(source) }

                if (cancelled) onCancelled()
                else {
                    val lastModified = response.headers[HttpHeaders.LastModified]
                    if (lastModified != null) request.lastModified = lastModified
                    else request.lastModified = response.headers[HttpHeaders.ETag]
                    result.setSuccessful()
                }
            }
        } catch (e: IllegalArgumentException) { onFail(DownloadError.ERROR_MALFORMED_URL, e.message)
        } catch (e: SocketTimeoutException) { onFail(DownloadError.ERROR_CONNECTION_ERROR, e.message)
        } catch (e: UnresolvedAddressException) { onFail(DownloadError.ERROR_UNKNOWN_HOST, e.message)
        } catch (e: IOException) {
            if (wasDownloadBlocked(e)) {
                onFail(DownloadError.ERROR_IO_BLOCKED, e.message)
                return
            }
            val message = e.message
            if (message != null && message.contains("Trust anchor for certification path not found")) {
                onFail(DownloadError.ERROR_CERTIFICATE, e.message)
                return
            }
            onFail(DownloadError.ERROR_IO_ERROR, e.message)
        } catch (e: NullPointerException) {
            onFail(DownloadError.ERROR_CONNECTION_ERROR, request.source)    // might be thrown by connection.getInputStream()
        } catch (e: Throwable) {
            onFail(DownloadError.ERROR_NOT_FOUND, e.message)
        } finally { }
    }

    companion object {
        private val TAG: String = FeedDownloader::class.simpleName ?: "Anonymous"
    }

}

class EpisodeDownloader(request: DownloadRequest): Downloader(request) {
    override suspend fun download() {
        withContext(downloadDispatcher) {
            Logd(TAG, "starting downloadEpisode(): destination: ${request.destination}")
            if (request.source == null) return@withContext
            startTiming()
            downloadStates[request.source] = DownloadStatus(DownloadStatus.State.QUEUED.ordinal, 5)
            timeIt("$TAG start")

            val destFile = request.destination.toUF()
            var startPosition = 0L
            try {
                val uri = getURIFromRequestUrl(request.source)
                //            timeIt("$TAG got uri")

                suspend fun writeBody(response: HttpResponse) {
                    Logd(TAG, "writeBody")
                    val buffer = ByteArray(BUFFER_SIZE)
                    try {
                        destFile.sink(startPosition>0).buffer().use { out ->
                            val channel = response.bodyAsChannel()
                            var lastReportedProgress = -1
                            while (true) {
                                val read = channel.readAvailable(buffer)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                request.soFar += read
                                request.progressPercent = (100.0 * request.soFar / request.size).toInt()
                                val currentProgress = request.progressPercent
                                if (currentProgress / 10 > lastReportedProgress / 10 || currentProgress == 100) {
                                    lastReportedProgress = currentProgress
                                    withContext(Dispatchers.Main.immediate) {
                                        downloadStates[request.source] = DownloadStatus(if (request.progressPercent < 100) DownloadStatus.State.RUNNING.ordinal else DownloadStatus.State.COMPLETED.ordinal, request.progressPercent)
                                    }
                                }
                                //                                Logd(TAG, "writeBody request.soFar: ${request.soFar} progressPercent: $progressPercent")
                            }
                        }
                    } catch (e: IOException) { Logs(TAG, e, "writeBody error") }
                }

                fun isContentTypeTextAndSmallerThan100kb(response: HttpResponse): Boolean {
                    var contentLength = -1
                    val contentLen = response.headers[HttpHeaders.ContentLength]
                    if (contentLen != null) try { contentLength = contentLen.toInt() } catch (e: NumberFormatException) { Logs(TAG, e) }
                    Logd(TAG, "content length: $contentLength")
                    val contentType = response.headers[HttpHeaders.ContentType]
                    Logd(TAG, "content type: $contentType")
                    return contentType != null && contentType.startsWith("text/") && contentLength < 100 * 1024
                }

//                val DownloadRequestKey = AttributeKey<DownloadRequest>("DownloadRequest")
                getKtorClient().prepareGet(uri.toString()) {
                    onDownload { bytesSentTotal, contentLength ->
                        if ((contentLength?:0) > 0) {
                            val progress = (100 * bytesSentTotal / contentLength!!).toInt()
                            //                        timeIt("$TAG got progress: $progress")
                            request.progressPercent = progress
                            downloadStates[request.source] = DownloadStatus(if(request.progressPercent < 100) DownloadStatus.State.RUNNING.ordinal else DownloadStatus.State.COMPLETED.ordinal, request.progressPercent)
                        }
                    }
                    attributes.put(DownloadRequestKey, request)
                    header(HttpHeaders.CacheControl, "no-store")
                    header(HttpHeaders.AcceptEncoding, "identity")
                    if (uri.scheme == "http") header("Upgrade-Insecure-Requests", "1")

                    val existingFileSize: Long = destFile.size() ?:0
                    if (existingFileSize > 0) {
                        request.soFar = existingFileSize
                        header(HttpHeaders.Range, "bytes=${request.soFar}-")
                        Logd(TAG, "Adding range header: " + request.soFar)
                    }
                }.execute { response ->
                    timeIt("$TAG got response")

                    val contentEncodingHeader = response.headers[HttpHeaders.ContentEncoding]
                    var isGzip = false
                    if (!contentEncodingHeader.isNullOrEmpty()) isGzip = (contentEncodingHeader.lowercase() == "gzip")
                    timeIt("$TAG after isGzip")

                    Logd(TAG, "Response status is " + response.status) // check if size specified in the response header is the same as the size of the
                    // written file. This check cannot be made if compression was used
                    //                    Logd(TAG,"buffer: $buffer")
                    when {
                        response.status == HttpStatusCode.NotModified -> {
                            Logd(TAG, "Feed '${request.source}' not modified")
                            onCancelled()
                            return@execute
                        }
                        response.status == HttpStatusCode.RequestedRangeNotSatisfiable -> {
                            val lastModified = response.headers[HttpHeaders.LastModified]
                            if (lastModified != null) request.lastModified = lastModified
                            else request.lastModified = response.headers[HttpHeaders.ETag]
                            result.setSuccessful()
                            request.progressPercent = 100
                            return@execute
                        }
                        response.status == HttpStatusCode.PartialContent -> {
                            val fileExists = destFile.exists()
                            val contentRangeHeader = if (fileExists) response.headers[HttpHeaders.ContentRange] else null
                            if (fileExists && response.status == HttpStatusCode.PartialContent && !contentRangeHeader.isNullOrEmpty()) {
                                val start = contentRangeHeader.removePrefix("bytes ").substringBefore('-').toLong()
                                if (start != request.soFar) {
                                    Logt(TAG, "Unexpected resume offset $start, restarting download")
                                    destFile.delete()
                                } else {
                                    Logd(TAG, "Resuming download at $start")
                                    startPosition = start
                                }
                                val remaining = response.contentLength()
                                request.size = if (remaining != null) remaining + request.soFar else DownloadResult.SIZE_UNKNOWN.toLong()
                            }
                        }
                        response.status == HttpStatusCode.OK -> {
                            destFile.delete()
                            request.soFar = 0
                            request.size = response.contentLength() ?: DownloadResult.SIZE_UNKNOWN.toLong()
                        }
                        response.status == HttpStatusCode.NoContent -> {
                            callOnFailByResponseCode(response)
                            return@execute
                        }
                        !response.status.isSuccess() -> {
                            callOnFailByResponseCode(response)
                            return@execute
                        }
                        isContentTypeTextAndSmallerThan100kb(response) -> {
                            onFail(DownloadError.ERROR_FILE_TYPE, null)
                            return@execute
                        }
                        else -> throw IOException("Unexpected HTTP status ${response.status}")
                    }
                    timeIt("$TAG after when")
                    downloadStates[request.source] = DownloadStatus(DownloadStatus.State.RUNNING.ordinal, 10)
                    checkIfRedirect(response)
                    timeIt("$TAG after checkIfRedirect")
                    downloadStates[request.source] = DownloadStatus(DownloadStatus.State.RUNNING.ordinal, 15)
                    request.ensureMediaFileExists()
                    timeIt("$TAG after ensureMediaFileExists")
                    downloadStates[request.source] = DownloadStatus(DownloadStatus.State.RUNNING.ordinal, 18)
                    request.statusMsg = (R.string.download_running)

                    if (appPrefs.checkAvailableSpace) {
                        val freeSpace = freeSpaceAvailable
                        Logd(TAG, "Free space is $freeSpace > ${request.size}")
                        timeIt("$TAG after freeSpace")
                        if (request.size != DownloadResult.SIZE_UNKNOWN.toLong() && request.size > freeSpace) {
                            onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null)
                            return@execute
                        }
                    }

                    writeBody(response)
                    timeIt("$TAG after writeBody")
                    if (!checkResults(isGzip, response)) return@execute
                    timeIt("$TAG after checkResults")
                }
            } catch (e: IllegalArgumentException) { onFail(DownloadError.ERROR_MALFORMED_URL, e.message)
            } catch (e: SocketTimeoutException) { onFail(DownloadError.ERROR_CONNECTION_ERROR, e.message)
            } catch (e: UnresolvedAddressException) { onFail(DownloadError.ERROR_UNKNOWN_HOST, e.message)
            } catch (e: IOException) {
                if (wasDownloadBlocked(e)) {
                    onFail(DownloadError.ERROR_IO_BLOCKED, e.message)
                    return@withContext
                }
                val message = e.message
                if (message != null && message.contains("Trust anchor for certification path not found")) {
                    onFail(DownloadError.ERROR_CERTIFICATE, e.message)
                    return@withContext
                }
                onFail(DownloadError.ERROR_IO_ERROR, e.message)
            } catch (e: NullPointerException) { onFail(DownloadError.ERROR_CONNECTION_ERROR, request.source)    // might be thrown by connection.getInputStream()
            } finally { }
        }
    }

    companion object {
        private val TAG: String = EpisodeDownloader::class.simpleName ?: "Anonymous"
    }
}