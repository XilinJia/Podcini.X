package ac.mdiq.podcini.playback

import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.LogeFor
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import kotlinx.coroutines.runBlocking
import okio.BufferedSink
import okio.buffer

class SegmentSavingDataSource(private val cacheDataSource: CacheDataSource) : DataSource {
    private val TAG = "SegmentSavingDataSource"

    private var currentDataSpec: DataSpec? = null

    private var mediaId: String = ""

    private var isRecording = false
    private var clipTempFile: UnifiedFile? = null
    private var clipTempFos: BufferedSink? = null
    private var clipStartByte: Long = 0L
    private var clipBytesWritten: Long = 0L

    private var bitrate: Int = 0

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        mediaId = dataSpec.key ?: dataSpec.uri.toString()
//        val existingSpans = getCache().getCachedSpans(mediaId)
//        Logd(TAG, "open Before listener: mediaId=[$mediaId] spans=${existingSpans.size}, totalBytes=${existingSpans.sumOf { it.length }}")
        return cacheDataSource.open(dataSpec).also { Logd(TAG, "Open: position=${dataSpec.position}, length=$it") }
    }

    fun forceCommitCurrentSink() {
        cacheDataSource.close()
        currentDataSpec?.let { cacheDataSource.open(it) }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = cacheDataSource.read(buffer, offset, length)
        if (isRecording) {
            Logd(TAG, "read isRecording bytesRead: $bytesRead")
            if (bytesRead > 0) {
                clipTempFos?.write(buffer, offset, bytesRead)
                clipBytesWritten += bytesRead
            } else if (bytesRead == -1) clipTempFos?.flush()
        }
        return bytesRead
    }

    override fun close() {
//        Logd(TAG, "closing")
        if (isRecording) stopRecording(0) // Fallback if not explicitly stopped
        clipTempFos?.flush()
        clipTempFos?.close()
        clipTempFos = null
        clipTempFile = null
        clipBytesWritten = 0L
        cacheDataSource.close()
    }

    fun startRecording(startPositionMs: Long, bitrate: Int, tmpDir: UnifiedFile) {
        if (!isRecording) {
            isRecording = true
            this.bitrate = bitrate
            clipTempFile = tmpDir / "clip_temp_${nowInMillis()}.tmp"
            clipTempFos = clipTempFile!!.sink().buffer()
            clipStartByte = (startPositionMs * bitrate / 8 / 1000)
            clipBytesWritten = 0L
            Logd(TAG, "Started recording at byte offset $clipStartByte")
        } else LogeFor(TAG, mediaId.toLongOrNull(), "Cannot start recording: tempDir not set or already recording")
    }

    fun stopRecording(endPositionMs: Long): UnifiedFile? {
        if (isRecording) {
            isRecording = false
            clipTempFos?.flush()
            clipTempFos?.close()
            forceCommitCurrentSink()
            clipTempFos = null
            val endByte = (endPositionMs * bitrate / 8 / 1000)
            Logd(TAG, "Stopped recording at byte offset $endByte, written: $clipBytesWritten")
            return clipTempFile?.takeIf { runBlocking { it.exists() } && clipBytesWritten > 0 }
        }
        return null
    }

    override fun getUri(): Uri? = cacheDataSource.uri
    override fun addTransferListener(transferListener: TransferListener) {
        cacheDataSource.addTransferListener(transferListener)
    }
    override fun getResponseHeaders(): Map<String, List<String>> {
        return cacheDataSource.responseHeaders
    }
}
