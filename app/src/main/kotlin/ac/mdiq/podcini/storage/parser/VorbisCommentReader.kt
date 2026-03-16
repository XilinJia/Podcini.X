package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import okio.buffer

abstract class VorbisCommentReader internal constructor(private val source: CountingSource) {
    val buffered = source.buffer()

    @Throws(VorbisCommentReaderException::class)
    fun readSource() {
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
        fun readUtf8String(length: Long): String = buffered.readUtf8(length)

        @Throws(VorbisCommentReaderException::class)
        fun readUserComment() {
            fun readContentVectorKey(vectorLength: Long): String? {
                val builder = StringBuilder()
                for (i in 0 until vectorLength) {
                    val c = (buffered.readByte().toInt() and 0xFF).toChar()
                    if (c == '=') return builder.toString()
                    else builder.append(c)
                }
                return null // no key found
            }
            try {
                val vectorLength = buffered.readIntLe().toLong() and 0xFFFFFFFFL
                if (vectorLength > 20 * 1024 * 1024) {
                    val keyPart = readUtf8String(10)
                    throw VorbisCommentReaderException("User comment unrealistically long. key=$keyPart, length=$vectorLength")
                }
                val key = readContentVectorKey(vectorLength)!!.lowercase()
                val shouldReadValue = handles(key)
                Logd(TAG, "key=$key, length=$vectorLength, handles=$shouldReadValue")
                if (shouldReadValue) onContentVectorValue(key, readUtf8String(vectorLength - key.length - 1))
                else buffered.skip(vectorLength - key.length - 1)
            } catch (e: Exception) { Logs(TAG, e, "readUserComment failed") }
        }
        /**
         * Looks for an identification header in the first page of the file. If an
         * identification header is found, it will be skipped completely
         */
        fun findIdentificationHeader() {
            val buffer = ByteArray(FIRST_OPUS_PAGE_LENGTH)
            buffered.readFully(buffer)
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
                        buffered.skip((FIRST_OGG_PAGE_LENGTH - FIRST_OPUS_PAGE_LENGTH).toLong())
                        return
                    }
                    bufferMatches(buffer, "OpusHead".toByteArray(), i) -> return
                }
            }
            throw Exception("No vorbis identification header found")
        }
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
                buffer[bytesRead % buffer.size] = buffered.readByte()
                when {
                    bufferMatches(buffer, oggCommentHeader, bytesRead) -> return
                    bufferMatches(buffer, "OpusTags".toByteArray(), bytesRead) -> return
                }
            }
            throw Exception("No comment header found")
        }
        @Throws(VorbisCommentReaderException::class)
        fun readCommentHeader(): VorbisCommentHeader {
            try {
                val vendorLength = buffered.readIntLe().toLong() and 0xFFFFFFFFL
                val vendorName = readUtf8String(vendorLength)
                val userCommentLength = buffered.readIntLe().toLong() and 0xFFFFFFFFL
                return VorbisCommentHeader(vendorName, userCommentLength)
            } catch (e: Exception) { throw VorbisCommentReaderException(e) }
        }
        fun findOggPage() {
            // find OggS
            val buffer = ByteArray(4)
            val oggPageHeader = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
            for (bytesRead in 0 until SECOND_PAGE_MAX_LENGTH) {
                val data = buffered.readByte().toInt()
                if (data == -1) throw Exception("EOF while trying to find vorbis page")
                buffer[bytesRead % buffer.size] = data.toByte()
                if (bufferMatches(buffer, oggPageHeader, bytesRead)) break
            }
            // read segments
            buffered.skip(22)
            val numSegments = buffered.readByte()
            buffered.skip(numSegments.toLong())
        }

        try {
            findIdentificationHeader()
            findOggPage()
            findCommentHeader()
            val commentHeader = readCommentHeader()
            Logd(TAG, commentHeader.toString())
            for (i in 0 until commentHeader.userCommentLength) readUserComment()
        } catch (e: Throwable) { Loge(TAG, "Vorbis parser: ${e.message}") }
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

class VorbisCommentMetadataReader(source: CountingSource) : VorbisCommentReader(source) {
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