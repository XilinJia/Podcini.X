package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import org.apache.commons.io.EndianUtils
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class VorbisCommentReader internal constructor(private val input: InputStream) {
    @Throws(VorbisCommentReaderException::class)
    fun readInputStream() {
        /**
         * Reads backwards in haystack, starting at position. Checks if the bytes match needle.
         * Uses haystack circularly, so when reading at (-1), it reads at (length - 1).
         */
        fun bufferMatches(haystack: ByteArray, needle: ByteArray, position: Int): Boolean {
            for (i in needle.indices) {
                var posInHaystack = position - i
                while (posInHaystack < 0) posInHaystack += haystack.size
                posInHaystack %= haystack.size
                if (haystack[posInHaystack] != needle[needle.size - 1 - i]) return false
            }
            return true
        }
        @Throws(IOException::class)
        fun readUtf8String(length: Long): String {
            val buffer = ByteArray(length.toInt())
            IOUtils.readFully(input, buffer)
            val charset = Charset.forName("UTF-8")
            return charset.newDecoder().decode(ByteBuffer.wrap(buffer)).toString()
        }
        @Throws(VorbisCommentReaderException::class)
        fun readUserComment() {
            @Throws(IOException::class)
            fun readContentVectorKey(vectorLength: Long): String? {
                val builder = StringBuilder()
                for (i in 0 until vectorLength) {
                    val c = input.read().toChar()
                    if (c == '=') return builder.toString()
                    else builder.append(c)
                }
                return null // no key found
            }
            try {
                val vectorLength = EndianUtils.readSwappedUnsignedInteger(input)
                if (vectorLength > 20 * 1024 * 1024) {
                    val keyPart = readUtf8String(10)
                    throw VorbisCommentReaderException("User comment unrealistically long. key=$keyPart, length=$vectorLength")
                }
                val key = readContentVectorKey(vectorLength)!!.lowercase()
                val shouldReadValue = handles(key)
                Logd(TAG, "key=$key, length=$vectorLength, handles=$shouldReadValue")
                if (shouldReadValue) {
                    val value = readUtf8String(vectorLength - key.length - 1)
                    onContentVectorValue(key, value)
                } else IOUtils.skipFully(input, vectorLength - key.length - 1)
            } catch (e: IOException) {
                Logs(TAG, e, "readUserComment failed")
            }
        }
        /**
         * Looks for an identification header in the first page of the file. If an
         * identification header is found, it will be skipped completely
         */
        @Throws(IOException::class)
        fun findIdentificationHeader() {
            val buffer = ByteArray(FIRST_OPUS_PAGE_LENGTH)
            IOUtils.readFully(input, buffer)
            val oggIdentificationHeader = byteArrayOf(PACKET_TYPE_IDENTIFICATION.toByte(),
                'v'.code.toByte(),
                'o'.code.toByte(),
                'r'.code.toByte(),
                'b'.code.toByte(),
                'i'.code.toByte(),
                's'.code.toByte())
            for (i in 6 until buffer.size) {
                when {
                    bufferMatches(buffer, oggIdentificationHeader, i) -> {
                        IOUtils.skip(input, (FIRST_OGG_PAGE_LENGTH - FIRST_OPUS_PAGE_LENGTH).toLong())
                        return
                    }
                    bufferMatches(buffer, "OpusHead".toByteArray(), i) -> return
                }
            }
            throw IOException("No vorbis identification header found")
        }
        @Throws(IOException::class)
        fun findCommentHeader() {
            val buffer = ByteArray(64) // Enough space for some bytes. Used circularly.
            val oggCommentHeader = byteArrayOf(PACKET_TYPE_COMMENT.toByte(),
                'v'.code.toByte(),
                'o'.code.toByte(),
                'r'.code.toByte(),
                'b'.code.toByte(),
                'i'.code.toByte(),
                's'.code.toByte())
            for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
                buffer[bytesRead % buffer.size] = input.read().toByte()
                when {
                    bufferMatches(buffer, oggCommentHeader, bytesRead) -> return
                    bufferMatches(buffer, "OpusTags".toByteArray(), bytesRead) -> return
                }
            }
            throw IOException("No comment header found")
        }
        @Throws(IOException::class, VorbisCommentReaderException::class)
        fun readCommentHeader(): VorbisCommentHeader {
            try {
                val vendorLength = EndianUtils.readSwappedUnsignedInteger(input)
                val vendorName = readUtf8String(vendorLength)
                val userCommentLength = EndianUtils.readSwappedUnsignedInteger(input)
                return VorbisCommentHeader(vendorName, userCommentLength)
            } catch (e: UnsupportedEncodingException) { throw VorbisCommentReaderException(e) }
        }
        @Throws(IOException::class)
        fun findOggPage() {
            // find OggS
            val buffer = ByteArray(4)
            val oggPageHeader = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
            for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
                val data = input.read()
                if (data == -1) throw IOException("EOF while trying to find vorbis page")
                buffer[bytesRead % buffer.size] = data.toByte()
                if (bufferMatches(buffer, oggPageHeader, bytesRead)) break
            }
            // read segments
            IOUtils.skipFully(input, 22)
            val numSegments = input.read()
            IOUtils.skipFully(input, numSegments.toLong())
        }
        try {
            findIdentificationHeader()
            findOggPage()
            findCommentHeader()
            val commentHeader = readCommentHeader()
            Logd(TAG, commentHeader.toString())
            for (i in 0 until commentHeader.userCommentLength) readUserComment()
        } catch (e: IOException) { Logd(TAG, "Vorbis parser: ${e.message}")
        } catch (e: Throwable) { Loge(TAG, "${e.message}") }
    }

    /**
     * Is called every time the Reader finds a content vector. The handler
     * should return true if it wants to handle the content vector.
     */
    protected abstract fun handles(key: String?): Boolean

    /**
     * Is called if onContentVectorKey returned true for the key.
     */
    @Throws(VorbisCommentReaderException::class)
    protected abstract fun onContentVectorValue(key: String?, value: String?)

    internal class VorbisCommentHeader(
        private val vendorString: String,
        val userCommentLength: Long) {

        override fun toString(): String {
            return ("VorbisCommentHeader [vendorString=$vendorString, userCommentLength=$userCommentLength]")
        }
    }

    companion object {
        private val TAG: String = VorbisCommentReader::class.simpleName ?: "Anonymous"
        private const val FIRST_OGG_PAGE_LENGTH = 58
        private const val FIRST_OPUS_PAGE_LENGTH = 47
        private const val SECOND_PAGE_MAX_LENGTH = 64 * 1024 * 1024
        private const val PACKET_TYPE_IDENTIFICATION = 1
        private const val PACKET_TYPE_COMMENT = 3
    }
}

class VorbisCommentChapterReader(input: InputStream) : VorbisCommentReader(input) {
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
            if (key!!.length > CHAPTERXXX_LENGTH) return key.substring(CHAPTERXXX_LENGTH)
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

class VorbisCommentMetadataReader(input: InputStream) : VorbisCommentReader(input) {
    var description: String? = null
        private set

    public override fun handles(key: String?): Boolean {
        return KEY_DESCRIPTION == key || KEY_COMMENT == key
    }

    public override fun onContentVectorValue(key: String?, value: String?) {
        if (KEY_DESCRIPTION == key || KEY_COMMENT == key) {
            if (description == null || (value != null && value.length > description!!.length)) description = value
        }
    }

    companion object {
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_COMMENT = "comment"
    }
}

class VorbisCommentReaderException : Exception {
    constructor(message: String?) : super(message)

    constructor(message: Throwable?) : super(message)
}