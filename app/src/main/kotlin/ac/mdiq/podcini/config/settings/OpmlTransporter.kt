package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import okio.buffer
import kotlin.time.Clock


class OpmlTransporter {

    /** Represents a single feed in an OPML file.  */
    class OpmlElement {
        
        var text: String? = null
        var xmlUrl: String? = null
        var htmlUrl: String? = null
        var type: String? = null
    }

    /** Contains symbols for reading and writing OPML documents.  */
    private object OpmlSymbols {
        const val OPML: String = "opml"
        const val OUTLINE: String = "outline"
        const val TEXT: String = "text"
        const val XMLURL: String = "xmlUrl"
        const val HTMLURL: String = "htmlUrl"
        const val TYPE: String = "type"
        const val VERSION: String = "version"
        const val DATE_CREATED: String = "dateCreated"
        const val HEAD: String = "head"
        const val BODY: String = "body"
        const val TITLE: String = "title"
        const val XML_FEATURE_INDENT_OUTPUT: String = "http://xmlpull.org/v1/doc/features.html#indent-output"
    }

    @Serializable
    @XmlSerialName("opml", "", "")
    data class Opml(
        @XmlElement(false) val version: String,
        val head: Head,
        val body: Body
    )

    @Serializable
    data class Head(
        val title: String,
        @XmlSerialName("dateCreated", "", "")
        val dateCreated: String
    )

    @Serializable
    data class Body(
        @XmlElement(true)
        val outlines: List<Outline>
    )

    @Serializable
    @XmlSerialName("outline", "", "")
    data class Outline(
        @XmlElement(false) val text: String,
        @XmlElement(false) val title: String,
        @XmlElement(false) val type: String? = null,
        @XmlSerialName("xmlUrl", "", "")
        @XmlElement(false) val xmlUrl: String? = null,
        @XmlSerialName("htmlUrl", "", "")
        @XmlElement(false) val htmlUrl: String? = null
    )

    class OpmlWriter : ExportWriter {
        /**
         * Takes a list of feeds and a writer and writes those into an OPML document.
         */
        override suspend fun writeDocument(feeds: List<Feed>, unifiedFile: UnifiedFile) {
            val outlines = feeds.asSequence().filterNot { it.isSynthetic() }
                .map { feed -> Outline(text = feed.title?:"", title = feed.title?:"", type = feed.type, xmlUrl = feed.downloadUrl, htmlUrl = feed.link) }.toList()
            val formatter = LocalDateTime.Format {
                day(); char(' '); monthNumber(); char(' '); yearTwoDigits(1970)
                char(' '); hour(); char(':'); minute(); char(':'); second()
            }
            val dateCreated = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).format(formatter)
            val opml = Opml(version = OPML_VERSION, head = Head(title = OPML_TITLE, dateCreated = dateCreated), body = Body(outlines = outlines))

            val xml = XML {
                indentString = "  "
                xmlDeclMode = XmlDeclMode.Charset
            }
            val outputString = xml.encodeToString(opml)
            unifiedFile.writeString(outputString)
        }

        override fun fileExtension(): String {
            return "opml"
        }

        companion object {
            private val TAG: String = OpmlWriter::class.simpleName ?: "Anonymous"
            private const val ENCODING = "UTF-8"
            private const val OPML_VERSION = "2.0"
            private const val OPML_TITLE = "Podcini Subscriptions"
        }
    }

    class OpmlReader {
        // ATTRIBUTES
        private var isInOpml = false
        private val elementList: MutableList<OpmlElement> = mutableListOf()

        /**
         * Reads an Opml document and returns a list of all OPML elements it can find
         */
        suspend fun readDocument(reader: UnifiedFile): MutableList<OpmlElement> {
            val xmlString = reader.source().buffer().use { it.readString(Charsets.UTF_8) }
            val xml = XML { defaultPolicy { ignoreUnknownChildren() } }

            try {
                val opmlData: Opml = xml.decodeFromString(Opml.serializer(), xmlString)
                val elements = opmlData.body.outlines.map { outline ->
                    OpmlElement().apply {
                        text = outline.title
                        xmlUrl = outline.xmlUrl
                        htmlUrl = outline.htmlUrl
                        type = outline.type
                    }
                }
                Logd(TAG, "readDocument elements: ${elements.size}")
                elementList.addAll(elements)
            } catch (e: Exception) { Logs(TAG, e, "Failed to parse OPML") }
            return elementList
        }

        companion object {
            private val TAG: String = OpmlReader::class.simpleName ?: "Anonymous"
        }
    }

    companion object {
        fun startImport(uri: Uri, CB: (List<OpmlElement>)->Unit) {
            val TAG = "OpmlTransporter"
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val docFile = uri.toUF()
                    if (docFile.exists()) {
                        val opmlElements = OpmlReader().readDocument(docFile)
                        withContext(Dispatchers.Main) { CB(opmlElements) }
                    } else Loge(TAG, "OPML file doesn't exist: $uri")
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Logs(TAG, e)
                        val message = if (e.message == null) "" else e.message!!
                        if (message.lowercase().contains("permission")) {
                            val permission = ActivityCompat.checkSelfPermission(getAppContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                            if (permission != PackageManager.PERMISSION_GRANTED) {
//                                requestPermission()
                                CB(listOf())
                                return@withContext
                            }
                        }

                        val userReadable = getAppContext().getString(R.string.opml_reader_error)
                        val details = e.message
                        val total = """
                            $userReadable
                            
                            $details
                            """.trimIndent()
                        val errorMessage = SpannableString(total)
                        errorMessage.setSpan(ForegroundColorSpan(-0x77777778), userReadable.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        commonConfirm = CommonConfirmAttrib(
                            title = getAppContext().getString(R.string.error_label),
                            message = errorMessage.toString(),
                            confirmRes = android.R.string.ok,
                            cancelRes = R.string.cancel_label,
                            onConfirm = {})
                        CB(listOf())
                    }
                }
            }
        }
    }
}