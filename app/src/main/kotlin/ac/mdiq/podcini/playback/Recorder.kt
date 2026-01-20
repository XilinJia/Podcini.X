package ac.mdiq.podcini.playback

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.playback.base.InTheatre.bitrate
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.LocalMediaPlayer.Companion.exoPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.curDataSource
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCache
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Recorder"

/**
 * Wrapper to handle start/stop/save with SegmentSavingDataSource
 * startPositionMs: Long? = null, // Null for stop/save
 * endPositionMs: Long? = null,   // Null for start
 */
@OptIn(UnstableApi::class)
fun saveClipInOriginalFormat(startPositionMs: Long, endPositionMs: Long? = null) {
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
            Logt(TAG, "uri is file or content, ignored")
            return
        }
        curDataSource?.startRecording(startPositionMs, bitrate, getAppContext().cacheDir)
        return
    }
    val tracks = exoPlayer!!.currentTracks
    val audioFormat = tracks.groups
        .asSequence()
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

    val clipname = "${getDurationStringShort(startPositionMs, false)}-${getDurationStringShort(endPositionMs, false)}.$ext"
    val outputFile = curEpisode!!.getClipFile(clipname)
    runOnIOScope {
        when {
            uri.scheme == "file" || uri.scheme == "content" -> {
                val bytesPerSecond = bitrate / 8.0
                val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
                val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
                val bytesToRead = endByte - startByte
                val tempFile = File(outputFile.parent, "temp_segment.${outputFile.extension}")
                FileOutputStream(tempFile).use { fos ->
                    when (uri.scheme) {
                        "file" -> File(uri.path ?: "").inputStream().use { input -> extractFromInputStream(input, startByte, bytesToRead, fos) }
                        "content" -> getAppContext().contentResolver.openInputStream(uri)?.use { input ->
                            extractFromInputStream(input, startByte, bytesToRead, fos)
                        } ?: run {
                            Loge(TAG, "Failed to open content URI: $uri")
                            return@runOnIOScope
                        }
                    }
                }
                val segment = tempFile.readBytes()
                tempFile.delete()
                if (segment.isNotEmpty()) {
                    val adjustedSegment = when (audioFormat.sampleMimeType) {
                        "audio/mp3" -> adjustMp3Clip(segment)
                        "audio/aac" -> adjustRawAacClip(segment)
                        "audio/ogg" -> adjustLocalOggClip(segment)
                        "audio/mp4" -> adjustLocalMp4Clip(segment)
                        else -> segment
                    }
                    FileOutputStream(outputFile).use { fos -> fos.write(adjustedSegment) }
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved local clip to: ${outputFile.absolutePath}")
                } else Loge(TAG, "Failed to extract segment from local media")
            }
            else -> {
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
                    val tempFile = File(outputFile.parent, "temp_segment.${outputFile.extension}")
                    FileOutputStream(tempFile).use { fos ->
                        cacheSpan.file!!.inputStream().use { input ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            val bytesToSkip = if (startByte >= cacheSpan.position) startByte - cacheSpan.position else 0L
                            Logd(TAG, "Cache span: pos=${cacheSpan.position}, len=${cacheSpan.length}")
                            Logd(TAG, "Skipping $bytesToSkip, reading $bytesToRead")
                            input.skip(bytesToSkip)
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < bytesToRead) {
                                val toWrite = minOf(bytesRead.toLong(), bytesToRead - totalRead).toInt()
                                fos.write(buffer, 0, toWrite)
                                totalRead += toWrite
                            }
                            Logd(TAG, "Total written: $totalRead bytes")
                        }
                    }
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
                        FileOutputStream(outputFile).use { fos -> fos.write(adjustedSegment) }
                        upsert(curEpisode!!) { it.clips.add(clipname) }
                        Logd(TAG, "Saved cached segment of ${(endPositionMs - startPositionMs) / 1000} seconds to: ${outputFile.absolutePath}")
                        return@runOnIOScope
                    } else Logd(TAG, "Failed to extract segment from cache")
                }
                Logd(TAG, "Single span not found for range $startByte to $endByte or failed to extract segment from cache. Attempting full extraction.")
                val fullBytes = getFullFileFromCache(cache, key)
                if (fullBytes != null && audioFormat.sampleMimeType == "audio/mp4") {
                    FileOutputStream(outputFile).use { fos -> fos.write(fullBytes) }
                    upsert(curEpisode!!) { it.clips.add(clipname) }
                    Logd(TAG, "Saved full MP4 file to: ${outputFile.absolutePath} (re-muxing needed for partial clip)")
                    return@runOnIOScope
                }
                if (tempFileDS != null) {
                    Logd(TAG, "Segment not available in cache or full file extraction. Trying with player extract")
                    val bytesPerSecond = bitrate / 8.0
                    val startByte = (startPositionMs * bytesPerSecond / 1000).toLong()
                    val endByte = (endPositionMs * bytesPerSecond / 1000).toLong()
                    val bytesToRead = endByte - startByte
                    val tempOutput = File(outputFile.parent, "temp_segment.${outputFile.extension}")
                    FileOutputStream(tempOutput).use { fos ->
                        tempFileDS.inputStream().use { input ->
                            val buffer = ByteArray(1024)
                            var bytesRead: Int
                            //                                input.skip(startByte)
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < bytesToRead) {
                                val toWrite = minOf(bytesRead.toLong(), bytesToRead - totalRead).toInt()
                                fos.write(buffer, 0, toWrite)
                                totalRead += toWrite
                            }
                            Logd(TAG, "Extracted $totalRead bytes from temp file")
                        }
                    }
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
                        FileOutputStream(outputFile).use { fos -> fos.write(adjustedSegment) }
                        upsert(curEpisode!!) { it.clips.add(clipname) }
                        Logd(TAG, "Saved clip to: ${outputFile.absolutePath}")
                    } else Loge(TAG, "Failed to extract segment from temp file")
                    tempFileDS.delete()
                } else Loge(TAG, "Failed saving clip: No temp file available after stopping recording")
            }
        }
    }
}

@OptIn(UnstableApi::class)
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
private fun extractFromInputStream(input: java.io.InputStream, startByte: Long, bytesToRead: Long, fos: FileOutputStream) {
    val buffer = ByteArray(1024)
    var bytesRead: Int
    input.skip(startByte)
    var totalRead = 0L
    while (input.read(buffer).also { bytesRead = it } != -1 && totalRead < bytesToRead) {
        val toWrite = minOf(bytesRead.toLong(), bytesToRead - totalRead).toInt()
        fos.write(buffer, 0, toWrite)
        totalRead += toWrite
    }
    Logd(TAG, "Total written from local source: $totalRead bytes")
}

// Format adjustments
private fun adjustMp3Clip(bytes: ByteArray): ByteArray = bytes
private fun adjustRawAacClip(bytes: ByteArray): ByteArray = bytes

@OptIn(UnstableApi::class)
private fun adjustOggClip(bytes: ByteArray, cache: SimpleCache, key: String, startByte: Long, endByte: Long): ByteArray {
    if (startByte > 0) {
        val headerBytes = getHeaderBytesFromCache(cache, key, 1024)
        return headerBytes?.plus(bytes) ?: bytes
    }
    return bytes
}

@OptIn(UnstableApi::class)
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

@OptIn(UnstableApi::class)
private fun getHeaderBytesFromCache(cache: SimpleCache, key: String, maxHeaderSize: Int): ByteArray? {
    val firstSpan = cache.getCachedSpans(key).minByOrNull { it.position } ?: return null
    if (firstSpan.position > 0 || firstSpan.file?.exists() != true) return null
    return firstSpan.file!!.inputStream().use { input ->
        val buffer = ByteArray(maxHeaderSize)
        val bytesRead = input.read(buffer, 0, maxHeaderSize)
        if (bytesRead > 0) buffer.copyOf(bytesRead) else null
    }
}

@OptIn(UnstableApi::class)
private fun getFullFileFromCache(cache: SimpleCache, key: String): ByteArray? {
    val spans = cache.getCachedSpans(key).sortedBy { it.position }
    if (spans.isEmpty()) return null
    val outputStream = java.io.ByteArrayOutputStream()
    spans.forEach { span -> span.file?.inputStream()?.use { it.copyTo(outputStream) } }
    return outputStream.toByteArray().takeIf { it.isNotEmpty() }
}

@OptIn(UnstableApi::class)
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
