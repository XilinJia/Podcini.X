package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import okio.buffer

abstract class VorbisCommentReader internal constructor(private val source: CountingSource) {
    val buffered = source.buffer()
    var trackNumber: Int? = null
    var totalTracks: Int? = null

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

        fun isOgg(): Boolean {
            val signature = buffered.peek().readByteArray(4)
            return signature.contentEquals(byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte()))
        }
        fun isFlac(): Boolean {
            val signature = buffered.peek().readByteArray(4)
            return signature.contentEquals(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
        }

        @Throws(VorbisCommentReaderException::class)
        fun readUserComment() {
            try {
                val vectorLength = buffered.readIntLe().toLong() and 0xFFFFFFFFL
                if (vectorLength > 1_000_000) throw VorbisCommentReaderException("Invalid comment length: $vectorLength")
                val data = ByteArray(vectorLength.toInt())
                buffered.readFully(data)
                val text = String(data, Charsets.UTF_8)
                val idx = text.indexOf('=')
                if (idx <= 0) return
                val key = text.substring(0, idx).lowercase()
                Logd(TAG, "readUserComment key: $key")
                val value = text.substring(idx + 1)
                if (handles(key)) onContentVectorValue(key, value)
            } catch (e: Exception) { Logs(TAG, e, "readUserComment failed") }
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
        fun findOggCommentBlock() {
            val vorbisCommentHeader = byteArrayOf(
                PACKET_TYPE_COMMENT.toByte(),
                'v'.code.toByte(),
                'o'.code.toByte(),
                'r'.code.toByte(),
                'b'.code.toByte(),
                'i'.code.toByte(),
                's'.code.toByte()
            )
            val opusTags = "OpusTags".toByteArray()
            val window = ByteArray(64)
            for (i in 0 until MAX_OGG_SCAN_BYTES) {
                window[i % window.size] = buffered.readByte()
                when {
                    bufferMatches(window, vorbisCommentHeader, i) -> return
                    bufferMatches(window, opusTags, i) -> return
                }
            }
            throw Exception("No Ogg comment block found")
        }
        fun findFlacCommentBlock() {
            val sig = ByteArray(4)
            buffered.readFully(sig)
            if (!sig.contentEquals(byteArrayOf('f'.code.toByte(),'L'.code.toByte(),'a'.code.toByte(),'C'.code.toByte()))) throw IllegalStateException("Not FLAC")
            var last = false
            while (!last) {
                val header = buffered.readByte().toInt() and 0xFF
                last = (header and 0x80) != 0
                val type = header and 0x7F
                val b1 = buffered.readByte().toInt() and 0xFF
                val b2 = buffered.readByte().toInt() and 0xFF
                val b3 = buffered.readByte().toInt() and 0xFF
                val length = (b1 shl 16) or (b2 shl 8) or b3
                if (type == 4) return
                buffered.skip(length.toLong())
            }
            throw IllegalStateException("No Vorbis comment block found")
        }
        try {
            when {
                isOgg() -> findOggCommentBlock()
                isFlac() -> findFlacCommentBlock()
            }
            val commentHeader = readCommentHeader()
            Logd(TAG, "commentHeader: $commentHeader")
            val count = commentHeader.userCommentLength.coerceAtMost(1000)
            repeat(count.toInt()) { readUserComment() }
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
    protected abstract fun onContentVectorValue(key: String?, value: String)

    internal class VorbisCommentHeader(
        private val vendorString: String,
        val userCommentLength: Long) {

        override fun toString(): String {
            return ("VorbisCommentHeader [vendorString=$vendorString, userCommentLength=$userCommentLength]")
        }
    }

    companion object {
        private val TAG: String = VorbisCommentReader::class.simpleName ?: "Anonymous"
        private const val MAX_OGG_SCAN_BYTES = 256 * 1024
        private const val PACKET_TYPE_COMMENT = 3
    }
}

class VorbisCommentMetadataReader(source: CountingSource) : VorbisCommentReader(source) {
    var description: String? = null
        private set

    public override fun handles(key: String?): Boolean {
        return when (key) {
            KEY_DESCRIPTION, KEY_COMMENT -> true
            "tracknumber", "tracktotal" -> true
            else -> false
        }
    }

    public override fun onContentVectorValue(key: String?, value: String) {
        Logd("VorbisCommentMetadataReader", "onContentVectorValue key: $key value: $value")
        when (key) {
            null -> {}
            KEY_DESCRIPTION, KEY_COMMENT -> if (description == null || value.length > description!!.length) description = value
            "tracknumber" -> trackNumber = value.substringBefore('/').toIntOrNull()
            "tracktotal" -> totalTracks = value.toIntOrNull()
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