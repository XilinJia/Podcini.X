package ac.mdiq.podcini.storage.parser

import ac.mdiq.podcini.storage.model.Chapter
import ac.mdiq.podcini.storage.specs.EmbeddedChapterImage
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.CountingInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException

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
open class ID3Reader(private val inputStream: CountingInputStream) {
    private var tagHeader: TagHeader? = null
    val position: Int
        get() = inputStream.count

    @Throws(IOException::class, ID3ReaderException::class)
    fun readInputStream() {
        tagHeader = readTagHeader() ?: return
        val tagContentStartPosition = position
        while (position < tagContentStartPosition + tagHeader!!.size) {
            val frameHeader = readFrameHeader()
            if (frameHeader.id[0] !in '0'..'z') {
                Logd(TAG, "Stopping because of invalid frame: $frameHeader")
                return
            }
            readFrame(frameHeader)
        }
    }

    @Throws(IOException::class, ID3ReaderException::class)
    protected open fun readFrame(frameHeader: FrameHeader) {
//        Logd(TAG, "Skipping frame: " + frameHeader.id + ", size: " + frameHeader.size)
        skipBytes(frameHeader.size)
    }

    /**
     * Skip a certain number of bytes on the given input stream.
     */
    @Throws(IOException::class, ID3ReaderException::class)
    fun skipBytes(number: Int) {
        if (number < 0) throw ID3ReaderException("Trying to read a negative number of bytes")
        IOUtils.skipFully(inputStream, number.toLong())
    }

    @Throws(IOException::class)
    fun readByte(): Byte {
        return inputStream.read().toByte()
    }

    @Throws(IOException::class)
    fun readShort(): Short {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        return ((firstByte.code shl 8) or secondByte.code).toShort()
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        val firstByte = inputStream.read().toChar()
        val secondByte = inputStream.read().toChar()
        val thirdByte = inputStream.read().toChar()
        val fourthByte = inputStream.read().toChar()
        return (firstByte.code shl 24) or (secondByte.code shl 16) or (thirdByte.code shl 8) or fourthByte.code
    }

    fun expectChar(expected: Char): Boolean {
        val read = inputStream.read().toChar()
//        if (read != expected) throw ID3ReaderException("Expected $expected and got $read")
        if (read != expected) Loge("ID3Reader", "Expected $expected and got $read")
        return read == expected
    }

    @Throws(ID3ReaderException::class, IOException::class)
    fun readTagHeader(): TagHeader? {
        if (!expectChar('I') || !expectChar('D') || !expectChar('3')) return null
        val version = readShort()
        val flags = readByte()
        val size = unsynchsafe(readInt())
        if ((flags.toInt() and 64) != 0) {
            val extendedHeaderSize = readInt()
            skipBytes(extendedHeaderSize - 4)
        }
        return TagHeader("ID3", size, version, flags)
    }

    @Throws(IOException::class)
    fun readFrameHeader(): FrameHeader {
        val id = readPlainBytesToString(FRAME_ID_LENGTH)
        var size = readInt()
        if (tagHeader != null && tagHeader!!.version >= 0x0400) size = unsynchsafe(size)
        val flags = readShort()
        return FrameHeader(id, size, flags)
    }

    private fun unsynchsafe(inVal: Int): Int {
        var out = 0
        var mask = 0x7F000000
        while (mask != 0) {
            out = out shr 1
            out = out or (inVal and mask)
            mask = mask shr 8
        }
        return out
    }

    /**
     * Reads a null-terminated string with encoding.
     */
    @Throws(IOException::class)
    fun readEncodingAndString(max: Int): String {
        val encoding = readByte()
        return readEncodedString(encoding.toInt(), max - 1)
    }

    @Throws(IOException::class)
    protected fun readPlainBytesToString(length: Int): String {
        val stringBuilder = StringBuilder()
        var bytesRead = 0
        while (bytesRead < length) {
            stringBuilder.append(Char(readByte().toUShort()))
            bytesRead++
        }
        return stringBuilder.toString()
    }

    @Throws(IOException::class)
    protected fun readIsoStringNullTerminated(max: Int): String {
        return readEncodedString(ENCODING_ISO.toInt(), max)
    }

    @Throws(IOException::class)
    fun readEncodedString(encoding: Int, max: Int): String {
        return when (encoding) {
            ENCODING_UTF16_WITH_BOM.toInt(), ENCODING_UTF16_WITHOUT_BOM.toInt() -> readEncodedString2(Charset.forName("UTF-16"), max)
            ENCODING_UTF8.toInt() -> readEncodedString2(Charset.forName("UTF-8"), max)
            else -> readEncodedString1(Charset.forName("ISO-8859-1"), max)
        }
    }

    /**
     * Reads chars where the encoding uses 1 char per symbol.
     */
    @Throws(IOException::class)
    private fun readEncodedString1(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        while (bytesRead < max) {
            val c = readByte()
            bytesRead++
            if (c.toInt() == 0) break
            bytes.write(c.toInt())
        }
        return charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    }

    /**
     * Reads chars where the encoding uses 2 chars per symbol.
     */
    @Throws(IOException::class)
    private fun readEncodedString2(charset: Charset, max: Int): String {
        val bytes = ByteArrayOutputStream()
        var bytesRead = 0
        var foundEnd = false
        while (bytesRead + 1 < max) {
            val c1 = readByte()
            val c2 = readByte()
            if (c1.toInt() == 0 && c2.toInt() == 0) {
                foundEnd = true
                break
            }
            bytesRead += 2
            bytes.write(c1.toInt())
            bytes.write(c2.toInt())
        }
        if (!foundEnd && bytesRead < max) {
            // Last character
            val c = readByte()
            if (c.toInt() != 0) bytes.write(c.toInt())
        }
        return try { charset.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray())).toString() }
        catch (e: MalformedInputException) {
            Logs(TAG, e, "readEncodedString2 failed")
            ""
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

class Id3MetadataReader(input: CountingInputStream) : ID3Reader(input) {
    var comment: String? = null
        private set

    @Throws(IOException::class, ID3ReaderException::class)
    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_COMMENT == frameHeader.id) {
            val frameStart = position.toLong()
            val encoding = readByte().toInt()
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

/**
 * Reads ID3 chapters.
 * See https://id3.org/id3v2-chapters-1.0
 */
class ChapterReader(input: CountingInputStream) : ID3Reader(input) {
    private val chapters: MutableList<Chapter> = mutableListOf()

    @Throws(IOException::class, ID3ReaderException::class)
    override fun readFrame(frameHeader: FrameHeader) {
        if (FRAME_ID_CHAPTER == frameHeader.id) {
            Logd(TAG, "Handling frame: $frameHeader")
            val chapter = readChapter(frameHeader)
            Logd(TAG, "Chapter done: $chapter")
            chapters.add(chapter)
        } else super.readFrame(frameHeader)
    }

    @Throws(IOException::class, ID3ReaderException::class)
    fun readChapter(frameHeader: FrameHeader): Chapter {
        val chapterStartedPosition = position
        val elementId = readIsoStringNullTerminated(100)
        val startTime = readInt().toLong()
        skipBytes(12) // Ignore end time, start offset, end offset

        val chapter = Chapter()
        chapter.start = startTime
        chapter.chapterId = elementId

        // Read sub-frames
        while (position < chapterStartedPosition + frameHeader.size) {
            val subFrameHeader = readFrameHeader()
            readChapterSubFrame(subFrameHeader, chapter)
        }
        return chapter
    }

    @Throws(IOException::class, ID3ReaderException::class)
    fun readChapterSubFrame(frameHeader: FrameHeader, chapter: Chapter) {
        Logd(TAG, "Handling subframe: $frameHeader")
        val frameStartPosition = position
        when (frameHeader.id) {
            FRAME_ID_TITLE -> {
                chapter.title = readEncodingAndString(frameHeader.size)
                Logd(TAG, "Found title: " + chapter.title)
            }
            FRAME_ID_LINK -> {
                readEncodingAndString(frameHeader.size) // skip description
                val url = readIsoStringNullTerminated(frameStartPosition + frameHeader.size - position)
                try {
                    val decodedLink = URLDecoder.decode(url, "ISO-8859-1")
                    chapter.link = decodedLink
                    Logd(TAG, "Found link: " + chapter.link)
                } catch (e: IllegalArgumentException) {
                    Logs(TAG, e, "Bad URL found in ID3 data")
                }
            }
            FRAME_ID_PICTURE -> {
                val encoding = readByte()
                val mime = readIsoStringNullTerminated(frameHeader.size)
                val type = readByte()
                val description = readEncodedString(encoding.toInt(), frameHeader.size)
                Logd(TAG, "Found apic: $mime,$description")
                if (MIME_IMAGE_URL == mime) {
                    val link = readIsoStringNullTerminated(frameHeader.size)
                    Logd(TAG, "Link: $link")
                    if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) chapter.imageUrl = link
                } else {
                    val alreadyConsumed = position - frameStartPosition
                    val rawImageDataLength = frameHeader.size - alreadyConsumed
                    if (chapter.imageUrl.isNullOrEmpty() || type.toInt() == IMAGE_TYPE_COVER) chapter.imageUrl = EmbeddedChapterImage.Companion.makeUrl(position, rawImageDataLength)
                }
            }
            else -> Logd(TAG, "Unknown chapter sub-frame.")
        }
        // Skip garbage to fill frame completely
        // This also asserts that we are not reading too many bytes from this frame.
        val alreadyConsumed = position - frameStartPosition
        skipBytes(frameHeader.size - alreadyConsumed)
    }

    fun getChapters(): List<Chapter> {
        return chapters
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

class ID3ReaderException(message: String?) : Exception(message)