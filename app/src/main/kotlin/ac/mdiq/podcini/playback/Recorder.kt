package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.Media3Player.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.Media3Player.Companion.getCache
import ac.mdiq.podcini.playback.base.Media3Player.Companion.simpleCache
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.cacheDir
import ac.mdiq.podcini.storage.utils.clipsDir
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.fs
import ac.mdiq.podcini.storage.utils.durationStringShort
import ac.mdiq.podcini.storage.utils.internalDir
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.storage.utils.parent
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.net.Uri
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.runBlocking
import okio.BufferedSink
import okio.Path.Companion.toOkioPath
import okio.buffer

private const val TAG = "Recorder"

var curDataSource: SegmentSavingDataSource? = null

/**
 * Custom DataSource that saves clip data during read when recording is active.
 * Adapted to use an existing CacheDataSource instance.
 */
class SegmentSavingDataSource(private val cacheDataSource: CacheDataSource) : DataSource {
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
        if (isRecording) stopRecording(0) // Fallback if not explicitly stopped
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
        } else Loge(TAG, "Cannot start recording: tempDir not set or already recording")
    }

    // Stop recording and return the temp file for processing
    fun stopRecording(endPositionMs: Long): UnifiedFile? {
        if (isRecording) {
            isRecording = false
            clipTempFos?.close()
            clipTempFos = null
            val endByte = (endPositionMs * bitrate / 8 / 1000)
            Logd(TAG, "Stopped recording at byte offset $endByte, written: $clipBytesWritten")
            return clipTempFile?.takeIf { runBlocking { it.exists() } && clipBytesWritten > 0 }
        }
        return null
    }
    override fun addTransferListener(transferListener: TransferListener) {
        cacheDataSource.addTransferListener(transferListener)
    }
}

class SegmentSavingDataSourceFactory(private val upstreamFactory: CacheDataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SegmentSavingDataSource(upstreamFactory.createDataSource())
    }
}


/**
 * Wrapper to handle start/stop/save with SegmentSavingDataSource
 * startPositionMs: Long? = null, // Null for stop/save
 * endPositionMs: Long? = null,   // Null for start
 */
suspend fun saveClipInOriginalFormat(startPositionMs: Long, endPositionMs: Long? = null) {
    val mediaItem = exoPlayer!!.currentMediaItem ?: run {
        Loge(TAG, "No current media item.")
        return
    }
    val uri = mediaItem.localConfiguration?.uri ?: run {
        Loge(TAG, "No URI in MediaItem.")
        return
    }
    if (endPositionMs == null) {
        if (uri.scheme == "file" || uri.scheme == "content") {
            Logd(TAG, "uri is file or content, will extract from the file.")
            return
        }
        curDataSource?.startRecording(startPositionMs, bitrate, cacheDir)
        return
    }
    val tracks = exoPlayer!!.currentTracks
    val audioFormat = tracks.groups.asSequence()
        .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
        .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
    if (audioFormat == null) {
        Loge(TAG, "No audio track found.")
        return
    }
    val mimeType = audioFormat.sampleMimeType
    Logd(TAG, "mimeType: [$mimeType]")
    val ext = getFileExtensionFromMimeType(mimeType)
    if (ext == null) {
        Loge(TAG, "Audio format not supported: $ext")
        return
    }

    val startBytePlayer = exoPlayer?.contentPositionToByte(startPositionMs)
    val endBytePlayer = exoPlayer?.contentPositionToByte(endPositionMs)

    val clipname = "${durationStringShort(startPositionMs, false, "m")}-${durationStringShort(endPositionMs, false, "m")}.$ext"
    val outputFile = curEpisode!!.getClipFile(clipname)
    when {
        uri.scheme == "file" || uri.scheme == "content" -> {
            val bytesPerSecond = bitrate / 8.0
            val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
            val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
            val bytesToRead = endByte - startByte
            val tempFile = cacheDir / "temp_segment.${outputFile.extension}"
            try {
                val sourceFile = uri.toUF()
                val allBytes = sourceFile.readBytes()
                val segmentBytes = allBytes.sliceArray(startByte.toInt() until (startByte + bytesToRead).toInt())
                tempFile.writeBytes(segmentBytes)
                val segment = tempFile.readBytes()
                if (segment.isNotEmpty()) {
                    val adjustedSegment = when (audioFormat.sampleMimeType) {
                        "audio/mp3" -> adjustMp3Clip(segment)
                        "audio/aac" -> adjustRawAacClip(segment)
                        "audio/ogg" -> adjustLocalOggClip(segment)
                        "audio/mp4" -> adjustLocalMp4Clip(segment)
                        else -> segment
                    }
                    outputFile.writeBytes(adjustedSegment)
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved local clip to: ${outputFile.absPath}")
                } else Loge(TAG, "Failed to extract segment from local media")
            } catch (e: Exception) { Logs(TAG, e, "FileKit operation failed")
            } finally { tempFile.delete() }
        }
        else -> {   // streaming
            val tempFileDS = curDataSource?.stopRecording(endPositionMs)
            val cache = getCache()
            val bytesPerSecond = bitrate / 8.0
            val startByte = startBytePlayer ?: (startPositionMs * bytesPerSecond / 1000).toLong()
            val endByte = endBytePlayer ?: (endPositionMs * bytesPerSecond / 1000).toLong()
            val bytesToRead = endByte - startByte
            val key = curEpisode!!.id.toString()
            val cacheSpan = cache.getCachedSpans(key).firstOrNull { span -> span.position <= startByte && (span.position + span.length) >= endByte }
            Logd(TAG, "cacheSpan found: ${cacheSpan != null}")
            if (cacheSpan?.file?.exists() == true) {
                val javaFile = cacheSpan.file ?: run { Loge(TAG, "CacheSpan is null or has no file"); return }
                val path = javaFile.toOkioPath()
                val tempFile = outputFile.parent()!! / "temp_segment.${outputFile.extension}"
                try {
                    fs.source(path).buffer().use { input ->
                        val bytesToSkip = if (startByte >= cacheSpan.position) startByte - cacheSpan.position else 0L
                        input.skip(bytesToSkip)
                        val segmentData = input.readByteArray(bytesToRead)
                        val totalRead = segmentData.size
                        tempFile.writeBytes(segmentData)
                        Logd(TAG, "Total written: $totalRead bytes")
                    }
                } catch (e: Exception) { Logs(TAG, e, "Failed to extract from cache span") }

                val segment = tempFile.readBytes()
                tempFile.delete()
                if (segment.isNotEmpty()) {
                    val adjustedSegment = when (audioFormat.sampleMimeType) {
                        "audio/mp3" -> adjustMp3Clip(segment)
                        "audio/aac" -> adjustRawAacClip(segment)
                        "audio/ogg" -> adjustOggClip(segment, cache, key, startByte, endByte)
                        "audio/mp4" -> adjustMp4Clip(segment, cache, key, startByte, endByte)
                        else -> segment
                    }
                    outputFile.writeBytes(adjustedSegment)
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved cached segment of ${(endPositionMs - startPositionMs) / 1000} seconds to: ${outputFile.absPath}")
                    return
                } else Logd(TAG, "Failed to extract segment from cache")
            }
            Logd(TAG, "Single span not found for range $startByte to $endByte or failed to extract segment from cache. Attempting full extraction.")
            val fullBytes = getFullFileFromCache(cache, key)
            if (fullBytes != null && audioFormat.sampleMimeType == "audio/mp4") {
                outputFile.writeBytes(fullBytes)
                upsert(curEpisode!!) { it.clips.add(clipname) }
                Logd(TAG, "Saved full MP4 file to: ${outputFile.absPath} (re-muxing needed for partial clip)")
                return
            }
            if (tempFileDS != null) {
                Logd(TAG, "Segment not available in cache or full file extraction. Trying with player extract")
                val bytesPerSecond = bitrate / 8.0
                val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
                val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
                val bytesToRead = endByte - startByte
                val tempOutput = outputFile.parent()!! / "temp_segment.${outputFile.extension}"
                try {
                    tempFileDS.source().buffer().use { input ->
                        val segmentData = input.readByteArray(bytesToRead)
                        val totalRead = segmentData.size
                        tempOutput.writeBytes(segmentData)
                        Logd(TAG, "Total written: $totalRead bytes")
                    }
                } catch (e: Exception) { Logs(TAG, e, "Failed to extract from cache span") }
                val segment = tempOutput.readBytes()
                tempOutput.delete()
                if (segment.isNotEmpty()) {
                    val adjustedSegment = when (audioFormat.sampleMimeType) {
                        "audio/mp3" -> adjustMp3Clip(segment)
                        "audio/aac" -> adjustRawAacClip(segment)
                        "audio/ogg" -> adjustOggClip(segment, cache, key, startByte, endByte)
                        "audio/mp4" -> adjustMp4Clip(segment, cache, key, startByte, endByte)
                        else -> segment
                    }
                    outputFile.writeBytes(adjustedSegment)
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved clip to: ${outputFile.absPath}")
                } else Loge(TAG, "Failed to extract segment from temp file")
                tempFileDS.delete()
            } else Loge(TAG, "Failed saving clip: No temp file available after stopping recording")
        }
    }
}


fun ExoPlayer.contentPositionToByte(positionMs: Long): Long? {
    val timeline = currentTimeline
    if (timeline.isEmpty) return null
    val window = Timeline.Window()
    timeline.getWindow(currentMediaItemIndex, window)
    val format = currentTracks.groups.firstOrNull { it.isSelected }?.getTrackFormat(0)
    val bitrate = format?.averageBitrate?.takeIf { it != Format.NO_VALUE } ?: return null
    return (positionMs * bitrate) / 8000 // bps to bytes
}

/**
 * Helper to extract bytes from an InputStream (local media).
 */
//private fun extractFromInputStream(input: java.io.InputStream, startByte: Long, bytesToRead: Long, fos: FileOutputStream) {
//    val buffer = ByteArray(1024)
//    var bytesRead: Int
//    input.skip(startByte)
//    var totalRead = 0L
//    while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < bytesToRead) {
//        val toWrite = minOf(bytesRead.toLong(), bytesToRead - totalRead).toInt()
//        fos.write(buffer, 0, toWrite)
//        totalRead += toWrite
//    }
//    Logd(TAG, "Total written from local source: $totalRead bytes")
//}

// Format adjustments
private fun adjustMp3Clip(bytes: ByteArray): ByteArray = bytes
private fun adjustRawAacClip(bytes: ByteArray): ByteArray = bytes

private fun adjustOggClip(bytes: ByteArray, cache: SimpleCache, key: String, startByte: Long, endByte: Long): ByteArray {
    if (startByte > 0) {
        val headerBytes = getHeaderBytesFromCache(cache, key, 1024)
        return headerBytes?.plus(bytes) ?: bytes
    }
    return bytes
}

private fun adjustMp4Clip(bytes: ByteArray, cache: SimpleCache, key: String, startByte: Long, endByte: Long): ByteArray {
    if (startByte > 0 || endByte < spansTotalLength(cache, key)) {
        Logt(TAG, "MP4 clip may not be playable without re-muxing.")
        val fullFileBytes = getFullFileFromCache(cache, key)
        return fullFileBytes ?: bytes
    }
    return bytes
}
private fun adjustLocalOggClip(bytes: ByteArray): ByteArray = bytes
private fun adjustLocalMp4Clip(bytes: ByteArray): ByteArray {
    Logt(TAG, "Local MP4 clip may not be playable without re-muxing.")
    return bytes
}

private fun getHeaderBytesFromCache(cache: SimpleCache, key: String, maxHeaderSize: Int): ByteArray? {
    val firstSpan = cache.getCachedSpans(key).minByOrNull { it.position } ?: return null
    if (firstSpan.position > 0 || firstSpan.file?.exists() != true) return null
    return firstSpan.file!!.inputStream().use { input ->
        val buffer = ByteArray(maxHeaderSize)
        val bytesRead = input.read(buffer, 0, maxHeaderSize)
        if (bytesRead > 0) buffer.copyOf(bytesRead) else null
    }
}

private fun getFullFileFromCache(cache: SimpleCache, key: String): ByteArray? {
    val spans = cache.getCachedSpans(key).sortedBy { it.position }
    if (spans.isEmpty()) return null
    val outputStream = java.io.ByteArrayOutputStream()
    spans.forEach { span -> span.file?.inputStream()?.use { it.copyTo(outputStream) } }
    return outputStream.toByteArray().takeIf { it.isNotEmpty() }
}

private fun spansTotalLength(cache: SimpleCache, key: String): Long = cache.getCachedSpans(key).sumOf { it.length }

private fun getFileExtensionFromMimeType(mimeType: String?): String? {
    return when (mimeType) {
        "audio/mp3", "audio/mpeg" -> "mp3"
        "audio/aac" -> "aac"
        "audio/mp4", "audio/mp4a-latm" -> "m4a"
        "audio/ogg" -> "ogg"
        else -> null
    }
}

// This is only for migration
fun moveClips() {
    suspend fun move(source: UnifiedFile): Int {
        if (!source.exists()) return 0
        val files = source.listChildren()
        Logt(TAG, "number of clips to move: ${files.size}")
        for (f in files) {
            val fileName = f.absPath.substringAfterLast('/')
            val newPath = "${clipsDir.absPath}/$fileName"
            val destFile = newPath.toUF()
            f.moveTo(destFile)
        }
        return files.size
    }
    runOnIOScope {
        val mediaDir = internalDir / "media"
        var num = move(mediaDir)
        val clipsDir_ = internalDir / "clips"
        num += move(clipsDir_)
        Logt(TAG, "number of clips moved: $num to ${clipsDir_.absPath}")
        upsert(appPrefs) { it.clipsMoved = true }

        val files = clipsDir.listChildren()
        for (f in files) {
            val path = f.absPath
            if (path.contains(":")) {
                val fNew = path.replace(":", "m").toUF()
                f.moveTo(fNew)
            }
        }
        realm.write {
            val episodes = query(Episode::class).query("clips.@count > 0").find()
            for (e in episodes) {
                val s = mutableSetOf<String>()
                for (c in e.clips) s.add(c.replace(":", "m"))
                e.clips.clear()
                e.clips.addAll(s)
            }
        }
    }
}