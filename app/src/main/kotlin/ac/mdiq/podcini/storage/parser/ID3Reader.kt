package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import com.fleeksoft.charset.decodeToString
import okio.Buffer
import okio.BufferedSource
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


/*
ID,Name,Description
TIT2,Title,The name of the track/episode.
TPE1,Lead Performer,Usually the artist or podcast host.
TRCK,Track Number,The order of the track in the album/series.
TALB,Album/Series,The podcast name or album title.
TDRC,Recording Time,"The year or date of recording (e.g., ""2026"")."
TCON,Content Type,"The genre (e.g., ""Podcast"" or ""Rock"")."
TCOP,Copyright,Legal/Copyright string.
TLEN,Length,This is the total duration in milliseconds.
TENC,Encoded by,The person or software that created the file.
CHAP,Chapter,Defines start/end times for chapters (what you found earlier).
*/

/**
 * Reads the ID3 Tag of a given file.
 * See https://id3.org/id3v2.3.0
 */
open class ID3Reader(private val source: CountingSource) {
    private var tagHeader: TagHeader? = null
    var remainingTagBytes: Long = 0L
    val position: Long
        get() = source.count

    val buffer = source.buffer()

    fun isValidFrameId(id: String): Boolean {
        return id.length == 4 && id.all { it in 'A'..'Z' || it in '0'..'9' }
    }

    fun readSource() {
        tagHeader = readTagHeader() ?: return
        remainingTagBytes = (tagHeader?.size?:0).toLong()

        val tagContentStartPosition = position
        while (position < tagContentStartPosition + tagHeader!!.size) {
            val frameHeader = readFrameHeader() ?: break
            Logd(TAG, "readSource frameHeader.id: ${frameHeader.id}")
            if (!isValidFrameId(frameHeader.id)) {
                Logd(TAG, "Invalid frame id: ${frameHeader.id}, skipping 1 byte to resync")
                skipBytes(1)
                continue
            }
//            if (frameHeader.id == "TLEN") {
//                val frameSize = frameHeader.size
//                if (frameSize > 1) {
//                    val encoding = buffer.readByte().toInt()
//                    val textDataSize = (frameSize - 1).toLong()
//                    val rawText = buffer.readString(textDataSize, Charsets.UTF_8).trim { it <= ' ' || it.code == 0 }
//                    val duration = rawText.toLongOrNull() ?: 0L
//                }
//            }
            readFrame(frameHeader)
        }
    }

    protected open fun readFrame(frameHeader: FrameHeader) {
        Logd(TAG, "readFrame Skipping frame: " + frameHeader.id + ", size: " + frameHeader.size)
        skipBytes(frameHeader.size)
    }

    /**
     * Skip a certain number of bytes on the given input stream.
     */
    @Throws(ID3ReaderException::class)
    protected fun skipBytes(number: Int) {
        if (number < 0) throw ID3ReaderException("Trying to skip a negative number of bytes: $number")
        try { buffer.skip(number.toLong()) } catch (e: Exception) { Logd(TAG, "skipBytes skip exception: ${e.message}")}
    }

    protected fun readTagHeader(): TagHeader? {
        val headerBytes = buffer.readByteArray(3)
        if (headerBytes.decodeToString() != "ID3") {
            Logt(TAG, "Not an ID3 file")
            return null
        }
        val versionMajor = buffer.readByte().toShort()
        val versionRevision = buffer.readByte().toShort()
        val flags = buffer.readByte()
        val size = unsynchsafe(buffer.readInt())
        var totalHeaderSize = 10

        // Extended header (if flag set)
        if ((flags.toInt() and 0x40) != 0) {
            val extSizeRaw = buffer.readInt()
            val extSize = if (versionMajor >= 4) unsynchsafe(extSizeRaw) else extSizeRaw
            skipBytes(extSize - 4)
            totalHeaderSize += extSize
        }

        val totalTagSize = totalHeaderSize + size
        return TagHeader("ID3", totalTagSize, versionMajor, flags)
    }

    protected fun readFrameHeader(): FrameHeader? {
        val peekBuffer = buffer.peek()
        if (peekBuffer.request(10)) {    // 4 ID + 4 size + 2 flags
            val idBytes = peekBuffer.readByteArray(4)
            val id = idBytes.decodeToString(Charsets.ISO_8859_1)
            if (!id.matches(Regex("[A-Z0-9]{4}"))) {
                if (idBytes.all { it.toInt() == 0 }) return null
                buffer.skip(1)
                return readFrameHeader()
            }
            val rawSize = peekBuffer.readInt()
            val size = when (tagHeader?.version) {
                4.toShort() -> unsynchsafe(rawSize)
                3.toShort() -> rawSize
                else -> return null
            }
            val flags = peekBuffer.readShort()
            buffer.skip(10)
            return FrameHeader(id, size, flags)
        }
        return null
    }

    fun readSubFrameHeader(chapBuffer: Buffer): FrameHeader? {
        if (chapBuffer.size < 10) return null
        val idBytes = chapBuffer.readByteArray(4)
        val id = idBytes.decodeToString(Charsets.ISO_8859_1)
        if (!id.matches(Regex("[A-Z0-9]{4}"))) return null
        val rawSize = chapBuffer.readInt()
        val size = when (tagHeader?.version) {
            4.toShort() -> unsynchsafe(rawSize)
            3.toShort() -> rawSize
            else -> rawSize
        }
        val flags = chapBuffer.readShort()
        return FrameHeader(id, size, flags)
    }

    fun readTextFrame(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val encoding = payload[0].toInt()
        val textBytes = payload.copyOfRange(1, payload.size)
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return textBytes.toString(charset).trimEnd('\u0000')
    }

    fun indexOfNull(byteArray: ByteArray, start: Int = 0): Int {
        for (i in start until byteArray.size) if (byteArray[i] == 0.toByte()) return i
        return -1
    }

    fun readWXXXFrame(payload: ByteArray): Pair<String, String> {
        if (payload.isEmpty()) return "" to ""
        val encoding = payload[0].toInt()
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val descEnd = indexOfNull(payload, start = 1)
        val description = if (descEnd != -1) payload.copyOfRange(1, descEnd).toString(charset) else ""
        val urlStart = if (descEnd != -1) descEnd + 1 else payload.size
        val url = if (urlStart < payload.size) payload.copyOfRange(urlStart, payload.size).toString(Charsets.ISO_8859_1).trimEnd('\u0000') else ""
        return description to url
    }

    data class APIC(
        val mimeType: String,
        val picType: Byte,
        val description: String,
        val imageData: ByteArray
    )

    fun readAPICFrame(payload: ByteArray): APIC? {
        if (payload.isEmpty()) return null

        val encodingByte = payload[0].toInt()
        val contentBytes = payload.copyOfRange(1, payload.size)

        val charset = when (encodingByte.toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }

        var offset = 0

        val mimeEnd = indexOfNull(contentBytes, offset)
        if (mimeEnd == -1) return null
        val mimeType = contentBytes.copyOfRange(offset, mimeEnd).toString(charset)
        offset = mimeEnd + 1

        if (offset >= contentBytes.size) return null
        val picType = contentBytes[offset]
        offset += 1

        val descEnd = indexOfNull(contentBytes, offset)
        val description = if (descEnd != -1) contentBytes.copyOfRange(offset, descEnd).toString(charset) else ""
        offset = (descEnd + 1).coerceAtMost(contentBytes.size)
        val imageData = contentBytes.copyOfRange(offset, contentBytes.size)

        return APIC(mimeType, picType, description, imageData)
    }

    private fun unsynchsafe(n: Int): Int = (n and 0x7F) or ((n shr 8 and 0x7F) shl 7) or ((n shr 16 and 0x7F) shl 14) or ((n shr 24 and 0x7F) shl 21)

    protected fun readPlainBytesToString(length: Int): String {
        val stringBuilder = StringBuilder()
        var bytesRead = 0
        while (bytesRead < length) {
            stringBuilder.append(Char(buffer.readByte().toUShort()))
            bytesRead++
        }
        return stringBuilder.toString()
    }

    fun readNullTerminatedString(buffer: Buffer): String {
        val bytes = mutableListOf<Byte>()
        while (!buffer.exhausted()) {
            val b = buffer.readByte()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        return bytes.toByteArray().toString(Charsets.ISO_8859_1)
    }

    fun readEncodedString(encoding: Int, max: Int, buffer_: BufferedSource = buffer): String {
        var charset = Charsets.UTF_8
        // Reads chars where the encoding uses 1 char per symbol.
        fun readEncodedString1(): String {
            val tempBuffer = Buffer()
            var bytesRead = 0
            while (bytesRead < max && !buffer_.exhausted()) {
                val b = buffer_.readByte()
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
            while (bytesRead + 1 < max && !buffer_.exhausted()) {
                val c1 = buffer_.readByte()
                val c2 = buffer_.readByte()
                if (c1.toInt() == 0 && c2.toInt() == 0) {
                    foundEnd = true
                    break
                }
                bytesRead += 2
                tempBuffer.writeByte(c1.toInt())
                tempBuffer.writeByte(c2.toInt())
            }
            if (!foundEnd && bytesRead < max && !buffer_.exhausted()) {
                val c = buffer_.readByte()
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
            val frameStart = position
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