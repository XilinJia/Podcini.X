package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.Media3Player.Companion.simpleCache
import ac.mdiq.podcini.playback.base.MediaPlayerBase
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logpe
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import kotlinx.coroutines.runBlocking
import okio.BufferedSink
import okio.buffer

private const val TAG = "Recorder"

/**
 * Custom DataSource that saves clip data during read when recording is active.
 * Adapted to use an existing CacheDataSource instance.
 */
class SegmentSavingDataSource(private val mPlayer: MediaPlayerBase, private val cacheDataSource: CacheDataSource) : DataSource {
    private val TAG = "SegmentSavingDataSource"

    private var cacheListener: Cache.Listener? = null
    private var isRecording = false
    private var clipTempFile: UnifiedFile? = null
    private var clipTempFos: BufferedSink? = null
    private var clipStartByte: Long = 0L
    private var clipBytesWritten: Long = 0L
    private lateinit var tempDir: UnifiedFile // Must be set externally, e.g., via constructor or setter

    override fun open(dataSpec: DataSpec): Long {
        //            val keys = simpleCache?.keys
        //            keys?.forEach { Logd(TAG, "key: $it") }
        val mediaId = dataSpec.key ?: dataSpec.uri.toString()
        val existingSpans = simpleCache?.getCachedSpans(mediaId)
        Logd(TAG, "Before listener: mediaId=[$mediaId] spans=${existingSpans?.size}, totalBytes=${existingSpans?.sumOf { it.length }}")

        cacheListener = object : Cache.Listener {
            override fun onSpanAdded(cache: Cache, span: CacheSpan) {
                Logd(TAG, "Span added: key=$mediaId, position=${span.position}, length=${span.length}, file=${span.file?.absolutePath}")
            }
            override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
                Logd(TAG, "Span removed: key=$mediaId, position=${span.position}, length=${span.length}")
            }
            override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
                Logd(TAG, "Span touched: key=$mediaId, oldPos=${oldSpan.position}, newPos=${newSpan.position}")
            }
        }
        simpleCache?.addListener(mediaId, cacheListener!!)
        return cacheDataSource.open(dataSpec).also { Logd(TAG, "Open: position=${dataSpec.position}, length=$it") }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var bytesRead = -1
        try {
            bytesRead = cacheDataSource.read(buffer, offset, length)
            //            Logd(TAG, "read offset=$offset length=$length bytesRead=$bytesRead")
            if (bytesRead > 0 && isRecording) {
                clipTempFos?.write(buffer, offset, bytesRead)
                clipBytesWritten += bytesRead
            }
        } catch (e: Throwable) { Logd(TAG, "data source read/write error: ${e.message}") }
        return bytesRead
    }

    override fun getUri(): Uri? = cacheDataSource.uri

    override fun close() {
        Logd(TAG, "closing")
        if (isRecording) stopRecording(mPlayer, 0) // Fallback if not explicitly stopped
        clipTempFos?.close()
        clipTempFos = null
        clipTempFile = null
        clipBytesWritten = 0L
        cacheDataSource.uri?.toString()?.let { mediaId -> cacheListener?.let { simpleCache?.removeListener(mediaId, it) } }
        cacheDataSource.close()
    }

    // Start recording at a given position (in ms, converted to bytes)
    fun startRecording(startPositionMs: Long, bitrate: Int, tmpDir: UnifiedFile) {
        tempDir = tmpDir
        if (!isRecording) {
            isRecording = true
            clipTempFile = tempDir / "clip_temp_${nowInMillis()}.tmp"
            clipTempFos = clipTempFile!!.sink().buffer()
            clipStartByte = (startPositionMs * bitrate / 8 / 1000)
            clipBytesWritten = 0L
            Logd(TAG, "Started recording at byte offset $clipStartByte")
        } else Logpe(TAG, mPlayer.curEpisode, "Cannot start recording: tempDir not set or already recording")
    }

    // Stop recording and return the temp file for processing
    fun stopRecording(mPlayer: MediaPlayerBase, endPositionMs: Long): UnifiedFile? {
        if (isRecording) {
            isRecording = false
            clipTempFos?.close()
            clipTempFos = null
            val endByte = (endPositionMs * mPlayer.bitrate / 8 / 1000)
            Logd(TAG, "Stopped recording at byte offset $endByte, written: $clipBytesWritten")
            return clipTempFile?.takeIf { runBlocking { it.exists() } && clipBytesWritten > 0 }
        }
        return null
    }
    override fun addTransferListener(transferListener: TransferListener) {
        cacheDataSource.addTransferListener(transferListener)
    }
}

class SegmentSavingDataSourceFactory(private val mPlayer: MediaPlayerBase, private val upstreamFactory: CacheDataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SegmentSavingDataSource(mPlayer,upstreamFactory.createDataSource())
    }
}
