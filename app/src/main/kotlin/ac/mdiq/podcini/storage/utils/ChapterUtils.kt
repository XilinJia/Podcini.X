package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.PodciniHttpClient.getKtorClient
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.parser.FrameHeader
import ac.mdiq.podcini.storage.parser.ID3Reader
import ac.mdiq.podcini.storage.parser.VorbisCommentReader
import ac.mdiq.podcini.storage.parser.VorbisCommentReaderException
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.content.ContentResolver
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.EOFException
import okio.FileSystem
import okio.buffer
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG: String = "ChapterUtils"

suspend fun fetchChapters(episode: Episode): List<Chapter> {
    Logd(TAG, "fetchChapters for ${episode.title}")
    var chaptersFromDatabase: List<Chapter>? = null
    var chaptersFromPodcastIndex: List<Chapter>? = null
    if (episode.chapters.isNotEmpty()) chaptersFromDatabase = episode.chapters
    if (!episode.podcastIndexChapterUrl.isNullOrEmpty()) {
        fun parse(jsonStr: String): List<Chapter> {
            try {
                val chapters: MutableList<Chapter> = mutableListOf()
                val obj = JSONObject(jsonStr)
                val objChapters = obj.getJSONArray("chapters")
                for (i in 0 until objChapters.length()) {
                    val jsonObject = objChapters.getJSONObject(i)
                    val startTime = jsonObject.optInt("startTime", 0)
                    val title = jsonObject.optString("title").takeIf { it.isNotEmpty() }
                    val link = jsonObject.optString("url").takeIf { it.isNotEmpty() }
                    val img = jsonObject.optString("img").takeIf { it.isNotEmpty() }
                    chapters.add(Chapter(startTime * 1000L, title, link, img))
                }
                return chapters
            } catch (e: JSONException) { Logs(TAG, e) }
            return listOf()
        }
        chaptersFromPodcastIndex = try {
            val url = episode.podcastIndexChapterUrl!!
            Logd(TAG, "fetchChapters fetching from url: $url")
            val response = getKtorClient().get(url) { header(HttpHeaders.CacheControl, null) }
            if (response.status.isSuccess()) parse(response.bodyAsText()) else listOf()
        } catch (e: Exception) { listOf() }
    }
    val chaptersFromMediaFile = loadChaptersFromMedia(episode)
    val chaptersMergePhase1 = mergeChapters(chaptersFromDatabase, chaptersFromMediaFile)
    return mergeChapters(chaptersMergePhase1, chaptersFromPodcastIndex) ?: listOf()
}

suspend fun loadChapters(episode: Episode, forceReload: Boolean) {
    if (!gearbox.canCheckMediaSize(episode)) return

    Logd(TAG, "loadChapters chaptersLoaded: ${episode.chaptersLoaded} forceReload: $forceReload")
    if (episode.chaptersLoaded && !forceReload) return

    val chapters = fetchChapters(episode)
    Logd(TAG, "loadChapters chapters size: ${chapters.size} ${episode.getEpisodeTitle()}")

    upsert(episode) { it.setChapters(chapters) }
}

/**
 * This method might modify the input data.
 */
private fun mergeChapters(chapters1: List<Chapter>?, chapters2: List<Chapter>?): List<Chapter>? {
    Logd(TAG, "Merging chapters")
    /**
     * Tries to give a score that can determine which list of chapters a user might want to see.
     */
    fun score(chapters: List<Chapter>): Int {
        var score = 0
        for (chapter in chapters) {
            score = (score + (if (chapter.title.isNullOrEmpty()) 0 else 1) + (if (chapter.link.isNullOrEmpty()) 0 else 1) + (if (chapter.imageUrl.isNullOrEmpty()) 0 else 1))
        }
        return score
    }
    when {
        chapters1 == null -> return chapters2
        chapters2 == null -> return chapters1
        chapters2.size > chapters1.size -> return chapters2
        chapters2.size < chapters1.size -> return chapters1
        else -> {
            // Merge chapter lists of same length. Store in chapters2 array.
            // In case the lists can not be merged, return chapters1 array.
            for (i in chapters2.indices) {
                val chapterTarget = chapters2[i]
                val chapterOther = chapters1[i]

                if (abs((chapterTarget.start - chapterOther.start).toDouble()) > 1000) {
                    Loge(TAG, "Chapter lists are too different. Cancelling merge.")
                    return if (score(chapters1) > score(chapters2)) chapters1 else chapters2
                }

                if (chapterTarget.imageUrl.isNullOrEmpty()) chapterTarget.imageUrl = chapterOther.imageUrl
                if (chapterTarget.link.isNullOrEmpty()) chapterTarget.link = chapterOther.link
                if (chapterTarget.title.isNullOrEmpty()) chapterTarget.title = chapterOther.title
            }
            return chapters2
        }
    }
}

/**
 * Reads ID3 chapters.
 * See https://id3.org/id3v2-chapters-1.0
 */
class ChapterReader(input: CountingSource) : ID3Reader(input) {
    val chapters: MutableList<Chapter> = mutableListOf()

    override fun readFrame(frameHeader: FrameHeader) {
        fun readChapter(): Chapter {
            Logd(TAG, "readChapter")
            val chapterStartedPosition = position
            val elementId = readIsoStringNullTerminated(100)
            val startTime = buffer.readInt().toLong()
            skipBytes(12) // Ignore end time, start offset, end offset
            val chapter = Chapter()
            chapter.start = startTime
            chapter.chapterId = elementId
            while (position < chapterStartedPosition + frameHeader.size) {
                val subFrameHeader = readFrameHeader() ?: break
                //                Logd(TAG, "readChapter Handling subframe: $subFrameHeader")
                val frameStartPosition = position
                when (subFrameHeader.id) {
                    FRAME_ID_TITLE -> {
                        chapter.title = readEncodingAndString(subFrameHeader.size)
                        Logd(TAG, "Found title: " + chapter.title)
                    }
                    FRAME_ID_LINK -> {
                        readEncodingAndString(subFrameHeader.size) // skip description
                        val url = readIsoStringNullTerminated((frameStartPosition + subFrameHeader.size - position).toInt())
                        try {
                            //                            val decodedLink = URLDecoder.decode(url, "ISO-8859-1")
                            val decodedLink = url.decodeURLQueryComponent(charset = Charsets.ISO_8859_1)
                            chapter.link = decodedLink
                            Logd(TAG, "Found link: " + chapter.link)
                        } catch (e: IllegalArgumentException) { Logs(TAG, e, "Bad URL found in ID3 data") }
                    }
                    FRAME_ID_PICTURE -> {
                        val encoding = buffer.readByte()
                        val mime = readIsoStringNullTerminated(subFrameHeader.size)
                        val type = buffer.readByte()
                        val description = readEncodedString(encoding.toInt(), subFrameHeader.size)
                        Logd(TAG, "Found apic: $mime,$description")
                        if (MIME_IMAGE_URL == mime) {
                            val link = readIsoStringNullTerminated(subFrameHeader.size)
                            Logd(TAG, "Link: $link")
                            if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) chapter.imageUrl = link
                        } else {
                            val alreadyConsumed = position - frameStartPosition
                            val rawImageDataLength = subFrameHeader.size - alreadyConsumed
                            if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) chapter.imageUrl = "embedded-image://$position/$rawImageDataLength"
                        }
                    }
                    else -> Logd(TAG, "Unknown chapter sub-frame ${subFrameHeader.id}")
                }
                // Skip garbage to fill frame completely
                // This also asserts that we are not reading too many bytes from this frame.
                val alreadyConsumed = position - frameStartPosition
                Logd(TAG, "readChapter subFrameHeader.size: ${subFrameHeader.size} alreadyConsumed: $alreadyConsumed = $position - $frameStartPosition ")
                skipBytes((subFrameHeader.size - alreadyConsumed).toInt())
            }
            return chapter
        }

        if (FRAME_ID_CHAPTER == frameHeader.id) {
            Logd(TAG, "readFrame Handling frame: $frameHeader")
            val chapter = readChapter()
            Logd(TAG, "readFrame Chapter done: $chapter")
            chapters.add(chapter)
        } else super.readFrame(frameHeader)
    }

    companion object {
        private val TAG: String = ChapterReader::class.simpleName ?: "Anonymous"

        const val FRAME_ID_CHAPTER: String = "CHAP"
        const val FRAME_ID_TITLE: String = "TIT2"
        const val FRAME_ID_LINK: String = "WXXX"
        const val FRAME_ID_PICTURE: String = "APIC"
        const val MIME_IMAGE_URL: String = "-->"
        const val IMAGE_TYPE_COVER: Int = 3
    }
}

class MP4ChapterReader(val source: BufferedSource) {
    val chapters: MutableList<Chapter> = mutableListOf()

    fun parseM4A() {
        while (!source.exhausted()) {
            val size = source.readInt().toLong() and 0xFFFFFFFFL
            val type = source.readUtf8(4)
            when (type) {
                "moov", "udta" -> continue
                "chpl" -> {
                    extractM4AChapters(size - 8)
                    return
                }
                else -> if (size >= 8) source.skip(size - 8)
            }
        }
    }

    private fun extractM4AChapters(atomDataSize: Long) {
        var bytesRead = 0L
        source.skip(4)
        bytesRead += 4
        val chapterCount = source.readByte().toInt() and 0xFF
        bytesRead += 1

        for (i in 0 until chapterCount) {
            if (bytesRead + 9 > atomDataSize) break

            val startTimeMs = source.readLong() / 10_000
            val titleLength = source.readByte().toInt() and 0xFF
            bytesRead += 9

            val remainingInAtom = atomDataSize - bytesRead
            val actualTitleLength = minOf(titleLength.toLong(), remainingInAtom)

            val title = source.readUtf8(actualTitleLength)
            bytesRead += actualTitleLength

            val chapter = Chapter()
            chapter.title = title
            chapter.chapterId = "ch_${i}_${startTimeMs}"
            chapter.start = startTimeMs
            chapters.add(chapter)
        }

        if (bytesRead < atomDataSize) source.skip(atomDataSize - bytesRead)
    }
}

class VorbisCommentChapterReader(source: CountingSource) : VorbisCommentReader(source) {
    val chapters: MutableList<Chapter> = mutableListOf()

    public override fun handles(key: String?): Boolean {
        return key?.matches(CHAPTER_KEY.toRegex()) ?: false
    }

    @Throws(VorbisCommentReaderException::class)
    public override fun onContentVectorValue(key: String?, value: String?) {
        Logd(TAG, "Key: $key, value: $value")
        fun getChapterById(id: Long): Chapter? {
            for (c in chapters) if (("" + id) == c.chapterId) return c
            return null
        }
        @Throws(VorbisCommentReaderException::class)
        fun getStartTimeFromValue(value: String?): Long {
            val parts = value!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.size >= 3) {
                try {
                    val hours = parts[0].toLong().hours.inWholeMilliseconds
                    val minutes = parts[1].toLong().minutes.inWholeMilliseconds
                    if (parts[2].contains("-->")) parts[2] = parts[2].substring(0, parts[2].indexOf("-->"))
                    val seconds = parts[2].toFloat().toLong().seconds.inWholeMilliseconds
                    return hours + minutes + seconds
                } catch (e: NumberFormatException) { throw VorbisCommentReaderException(e) }
            } else throw VorbisCommentReaderException("Invalid time string")
        }
        /**
         * Return the id of a vorbiscomment chapter from a string like CHAPTERxxx*
         * @return the id of the chapter key or -1 if the id couldn't be read.
         * @throws VorbisCommentReaderException
         */
        @Throws(VorbisCommentReaderException::class)
        fun getIdFromKey(key: String?): Int {
            if (key!!.length >= CHAPTERXXX_LENGTH) { // >= CHAPTERxxx
                try {
                    val strId = key.substring(8, 10)
                    return strId.toInt()
                } catch (e: NumberFormatException) { throw VorbisCommentReaderException(e) }
            }
            throw VorbisCommentReaderException("key is too short ($key)")
        }
        /**
         * Get the string that comes after 'CHAPTERxxx', for example 'name' or 'url'.
         */
        fun getAttributeTypeFromKey(key: String?): String? {
            if (key != null && key.length > CHAPTERXXX_LENGTH) return key.substring(CHAPTERXXX_LENGTH)
            return null
        }

        val attribute = getAttributeTypeFromKey(key)
        val id = getIdFromKey(key)
        var chapter = getChapterById(id.toLong())
        when (attribute) {
            null -> {
                if (getChapterById(id.toLong()) == null) {
                    // new chapter
                    val start = getStartTimeFromValue(value)
                    chapter = Chapter()
                    chapter.chapterId = "" + id
                    chapter.start = start
                    chapters.add(chapter)
                } else throw VorbisCommentReaderException("Found chapter with duplicate ID ($key, $value)")
            }
            CHAPTER_ATTRIBUTE_TITLE -> if (chapter != null) chapter.title = value
            CHAPTER_ATTRIBUTE_LINK -> if (chapter != null) chapter.link = value
        }
    }

    companion object {
        private val TAG: String = VorbisCommentChapterReader::class.simpleName ?: "Anonymous"

        private const val CHAPTER_KEY = "chapter\\d\\d\\d.*"
        private const val CHAPTER_ATTRIBUTE_TITLE = "name"
        private const val CHAPTER_ATTRIBUTE_LINK = "url"
        private const val CHAPTERXXX_LENGTH = "chapterxxx".length
    }
}

suspend fun loadChaptersFromMedia(episode: Episode): List<Chapter> {
    val tempPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "stream_buffer.tmp"
    val fileUrl = episode.fileUrl
    val streamurl = episode.downloadUrl

    suspend fun openSource(cb: suspend (BufferedSource, Long?)->Unit) {
        if (!fileUrl.isNullOrBlank()) {
            Logd(TAG, "openSource fileUrl: $fileUrl")
            val file = fileUrl.toUF()
            if (!file.exists()) {
                Loge(TAG, "file doesn't exist: $fileUrl")
                return
            }
            val size = file.size()
            Logd(TAG, "openSource size: $size")
            if (size == null || size > 0) cb(file.source().buffer(), size)
            else Loge(TAG, "loadChaptersFromMediaFile1 file is empty: $fileUrl")
        } else {
            if (streamurl != null && (streamurl.startsWith(ContentResolver.SCHEME_CONTENT) || streamurl.startsWith(ContentResolver.SCHEME_FILE))) {
                val file = streamurl.toUF()
                if (!file.exists()) {
                    Loge(TAG, "streamurl doesn't exist: $fileUrl")
                    return
                }
                val size = file.size()
                cb(file.source().buffer(), size)
            } else {
                if (streamurl.isNullOrEmpty()) throw Exception("stream url is null of empty")

                Logd(TAG, "openSource open streaming source")
                getKtorClient().prepareGet(streamurl).execute { response ->
                    val channel: ByteReadChannel = response.body()
                    try {
                        fs.sink(tempPath).buffer().use { sink ->
                            val limit = 1_000_000L
                            var totalRead = 0L
                            while (!channel.isClosedForRead && totalRead < limit) {
                                val packet = channel.readRemaining(8192)
                                if (packet.exhausted()) break
                                val bytes = packet.readByteArray()
                                sink.write(bytes)
                                totalRead += bytes.size
                                Logd(TAG, "fs.sink(tempPath) totalRead: $totalRead")
                            }
                        }
                        fs.source(tempPath).buffer().use { fileSource -> cb(fileSource, null) }
                    } catch (e: Exception) { Loge(TAG, "Download or Parse failed: ${e.message}")
                    } finally { fs.delete(tempPath) }
                }
            }
        }
    }

    suspend fun openNetSourceTail(cb: (BufferedSource)->Unit) {
        val client = getKtorClient()
        val contentLength = client.head(streamurl!!).headers[HttpHeaders.ContentLength]?.toLong() ?: 0L
        if (contentLength > 1_000_000L) {
            val tailSize = 128_000L // 128KB is usually enough for the 'moov' atom
            val startRange = contentLength - tailSize
            try {
                client.prepareGet(streamurl) { header(HttpHeaders.Range, "bytes=$startRange-$contentLength") }.execute { response ->
                    val channel = response.bodyAsChannel()
                    fs.appendingSink(tempPath).buffer().use { sink ->
                        val buffer = ByteArray(8192)
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                        }
                        sink.flush()
                    }
                    fs.source(tempPath).buffer().use { fileSource -> cb(fileSource) }
                }
            } catch (e: Exception) { Logs(TAG, e, "Tail download failed") }
        }
    }

    var chapters: List<Chapter> = listOf()
    openSource { fileSource, size ->
        val peek = fileSource.peek()
        val header4 = peek.readByteString(4)
        val header8 = if (peek.request(8)) peek.readByteString(8) else header4

        val format = when {
            header4.startsWith("ID3".encodeUtf8()) -> "MP3"
            header4.startsWith("OggS".encodeUtf8()) -> "OGG"
            header4.startsWith("fLaC".encodeUtf8()) -> "FLAC"
            header8.substring(4, 8).string(Charsets.UTF_8) == "ftyp" -> "M4A"
            header4.startsWith("RIFF".encodeUtf8()) -> "WAV"
            else -> "UNKNOWN"
        }
        Logd(TAG, "loadChaptersFromMediaFile1 format: $format")
        val countingSource = CountingSource(fileSource)
        fun enumerateEmptyChapterTitles(chapters: List<Chapter>) {
            for (i in chapters.indices) {
                val c = chapters[i]
                if (c.title == null) c.title = i.toString()
            }
        }
        fun chaptersValid(chapters: List<Chapter>): Boolean {
            if (chapters.isEmpty()) return false
            for (c in chapters) if (c.start < 0) return false
            return true
        }
        var chapters_ = when(format) {
            "MP3" -> {
                val reader = ChapterReader(countingSource)
                reader.readSource()
                reader.chapters.toList()
            }
            "M4A" -> {
                val reader = MP4ChapterReader(fileSource)
                try {
                    reader.parseM4A()
                    reader.chapters.toList()
                } catch (e: EOFException) {
                    Logd(TAG, "failed getting chapters in MP4 media header, try tail")
                    var cList = listOf<Chapter>()
                    if (size != null) {
                        val skipAmount = maxOf(0L, size - 128_000L)
                        fileSource.use { buffered ->
                            buffered.skip(skipAmount)
                            reader.parseM4A()
                            cList = reader.chapters.toList()
                        }
                    } else if (!streamurl.isNullOrBlank()) {
                        openNetSourceTail { fileSource ->
                            val reader = MP4ChapterReader(fileSource)
                            try {
                                reader.parseM4A()
                                cList = reader.chapters.toList()
                            } catch (e: Exception) {
                                Logs(TAG, e, "failed to get chapters for MP4 media")
                                cList = emptyList()
                            }
                        }
                    }
                    cList
                }
            }
            "OGG" -> {
                val reader = VorbisCommentChapterReader(countingSource)
                reader.readSource()
                reader.chapters.toList()
            }
            else -> {
                Logt(TAG, "file format: $format currently not handled")
                emptyList()
            }
        }
        chapters_ = chapters_.sortedWith { c1, c2 -> (c1.start - c2.start).toInt() }
        enumerateEmptyChapterTitles(chapters_)
        if (!chaptersValid(chapters_)) chapters_ = emptyList()
        if (chapters_.isNotEmpty()) chapters = chapters_
    }
    return chapters
}

