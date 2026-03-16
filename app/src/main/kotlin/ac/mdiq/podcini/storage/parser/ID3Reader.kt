package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import okio.Buffer
import okio.buffer

abstract class Header internal constructor(
    val id: String,
    val size: Int) {
    override fun toString(): String {
        return "Header [id=$id, size=$size]"
    }
}

class TagHeader(
    id: String,
    size: Int,
    val version: Short,
    private val flags: Byte) : Header(id, size) {
    override fun toString(): String {
        return ("TagHeader [version=$version, flags=$flags, id=$id, size=$size]")
    }
}

class FrameHeader(
    id: String,
    size: Int,
    flags: Short) : Header(id, size)

/**
 * Reads the ID3 Tag of a given file.
 * See https://id3.org/id3v2.3.0
 */
open class ID3Reader(private val source: CountingSource) {
    private var tagHeader: TagHeader? = null
    val position: Long
        get() = source.count

    val buffer = source.buffer()

    fun readSource() {
        tagHeader = readTagHeader() ?: return
        val tagContentStartPosition = position
        while (position < tagContentStartPosition + tagHeader!!.size) {
            val frameHeader = readFrameHeader() ?: break
            if (frameHeader.id[0] !in '0'..'z') {
                Logd(TAG, "Stopping because of invalid frame: $frameHeader")
                return
            }
            readFrame(frameHeader)
        }
    }

    protected open fun readFrame(frameHeader: FrameHeader) {
        //        Logd(TAG, "Skipping frame: " + frameHeader.id + ", size: " + frameHeader.size)
        skipBytes(frameHeader.size)
    }

    /**
     * Skip a certain number of bytes on the given input stream.
     */
    @Throws(ID3ReaderException::class)
    protected fun skipBytes(number: Int) {
        if (number < 0) throw ID3ReaderException("Trying to skip a negative number of bytes: $number")
        buffer.skip(number.toLong())
    }

    protected fun readTagHeader(): TagHeader? {
        val header = buffer.readByteArray(3)
        if (header.decodeToString() != "ID3") {
            Logt(TAG, "File is either not a ID3 file or does not have ID3 header")
            return null
        }
        val version = buffer.readShort()
        val flags = buffer.readByte()
        val size = unsynchsafe(buffer.readInt())
        if ((flags.toInt() and 64) != 0) {
            val extendedHeaderSize = buffer.readInt()
            skipBytes(extendedHeaderSize - 4)
        }
        return TagHeader("ID3", size, version, flags)
    }

    protected fun readFrameHeader(): FrameHeader? {
        val id = readPlainBytesToString(FRAME_ID_LENGTH)
        if (id.isBlank() || id[0].code == 0) {
            Logd(TAG, "Hit padding or end of tags. Stopping.")
            return null
        }
        if (!id.matches(Regex("[A-Z0-9]{4}"))) {
            Logd(TAG, "Invalid Frame ID found: $id. We are likely misaligned.")
            return null
        }
        val rawSize = buffer.readInt()
        val size = when {
            tagHeader != null && tagHeader!!.version >= 0x0400 -> unsynchsafe(rawSize)
            tagHeader != null && tagHeader!!.version == 0x0300.toShort() -> rawSize
            else -> {
                Loge(TAG, "header version not supported: ${tagHeader?.version}")
                return null
            }
        }
//        if (tagHeader != null && tagHeader!!.version >= 0x0400) size = unsynchsafe(size)
        val flags = buffer.readShort()
        return FrameHeader(id, size, flags)
    }

    private fun unsynchsafe(n: Int): Int = (n and 0x7F) or ((n shr 8 and 0x7F) shl 7) or ((n shr 16 and 0x7F) shl 14) or ((n shr 24 and 0x7F) shl 21)

//    private fun unsynchsafe(inVal: Int): Int {
//        var out = 0
//        var mask = 0x7F000000
//        while (mask != 0) {
//            out = out shr 1
//            out = out or (inVal and mask)
//            mask = mask shr 8
//        }
//        return out
//    }

    /**
     * Reads a null-terminated string with encoding.
     */
    fun readEncodingAndString(max: Int): String = readEncodedString(buffer.readByte().toInt(), max - 1)

    protected fun readPlainBytesToString(length: Int): String {
        val stringBuilder = StringBuilder()
        var bytesRead = 0
        while (bytesRead < length) {
            stringBuilder.append(Char(buffer.readByte().toUShort()))
            bytesRead++
        }
        return stringBuilder.toString()
    }

    protected fun readIsoStringNullTerminated(max: Int): String = readEncodedString(ENCODING_ISO.toInt(), max)

    fun readEncodedString(encoding: Int, max: Int): String {
        var charset = Charsets.UTF_8
        // Reads chars where the encoding uses 1 char per symbol.
        fun readEncodedString1(): String {
            val tempBuffer = Buffer()
            var bytesRead = 0
            while (bytesRead < max && !buffer.exhausted()) {
                val b = buffer.readByte()
                bytesRead++
                if (b == 0.toByte()) break
                tempBuffer.writeByte(b.toInt())
            }
            return tempBuffer.readString(charset)
        }
         // Reads chars where the encoding uses 2 chars per symbol.
        fun readEncodedString2(): String {
            val tempBuffer = Buffer()
            var bytesRead = 0
            var foundEnd = false
            while (bytesRead + 1 < max && !buffer.exhausted()) {
                val c1 = buffer.readByte()
                val c2 = buffer.readByte()
                if (c1.toInt() == 0 && c2.toInt() == 0) {
                    foundEnd = true
                    break
                }
                bytesRead += 2
                tempBuffer.writeByte(c1.toInt())
                tempBuffer.writeByte(c2.toInt())
            }
            if (!foundEnd && bytesRead < max && !buffer.exhausted()) {
                val c = buffer.readByte()
                if (c.toInt() != 0) tempBuffer.writeByte(c.toInt())
            }
            return try { tempBuffer.readString(tempBuffer.size, charset) } catch (e: Exception) { Loge(TAG, "readEncodedString2 failed: ${e.message}"); "" }
        }

        return when (encoding) {
            ENCODING_UTF16_WITH_BOM.toInt(), ENCODING_UTF16_WITHOUT_BOM.toInt() -> {
                charset = Charsets.UTF_16
                readEncodedString2()
            }
            ENCODING_UTF8.toInt() -> {
                charset = Charsets.UTF_8
                readEncodedString2()
            }
            else -> {
                charset = Charsets.ISO_8859_1
                readEncodedString1()
            }
        }
    }

    companion object {
        private val TAG: String = ID3Reader::class.simpleName ?: "Anonymous"
        private const val FRAME_ID_LENGTH = 4
        const val ENCODING_ISO: Byte = 0
        const val ENCODING_UTF16_WITH_BOM: Byte = 1
        const val ENCODING_UTF16_WITHOUT_BOM: Byte = 2
        const val ENCODING_UTF8: Byte = 3
    }
}

class Id3MetadataReader(source: CountingSource) : ID3Reader(source) {
    var comment: String? = null
        private set

    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_COMMENT == frameHeader.id) {
            val frameStart = position.toLong()
            val encoding = buffer.readByte().toInt()
            skipBytes(3) // Language
            val shortDescription = readEncodedString(encoding, frameHeader.size - 4)
            val longDescription = readEncodedString(encoding, (frameHeader.size - (position - frameStart)).toInt())
            comment = if (shortDescription.length > longDescription.length) shortDescription else longDescription
        } else super.readFrame(frameHeader)
    }

    companion object {
        const val FRAME_ID_COMMENT: String = "COMM"
    }
}

class ID3ReaderException(message: String?) : Exception(message)