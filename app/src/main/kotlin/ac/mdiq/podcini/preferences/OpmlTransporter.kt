package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.formatRfc822Date
import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.Xml
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.input.BOMInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.Writer
import java.util.*
import kotlin.Throws

class OpmlTransporter {

    /** Represents a single feed in an OPML file.  */
    class OpmlElement {
        @JvmField
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

    class OpmlWriter : ExportWriter {
        /**
         * Takes a list of feeds and a writer and writes those into an OPML document.
         */
        @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
        override fun writeDocument(feeds: List<Feed>, writer: Writer, context: Context) {
            val xs = Xml.newSerializer()
            xs.setFeature(OpmlSymbols.XML_FEATURE_INDENT_OUTPUT, true)
            xs.setOutput(writer)

            xs.startDocument(ENCODING, false)
            xs.startTag(null, OpmlSymbols.OPML)
            xs.attribute(null, OpmlSymbols.VERSION, OPML_VERSION)

            xs.startTag(null, OpmlSymbols.HEAD)
            xs.startTag(null, OpmlSymbols.TITLE)
            xs.text(OPML_TITLE)
            xs.endTag(null, OpmlSymbols.TITLE)
            xs.startTag(null, OpmlSymbols.DATE_CREATED)
            xs.text(formatRfc822Date(Date()))
            xs.endTag(null, OpmlSymbols.DATE_CREATED)
            xs.endTag(null, OpmlSymbols.HEAD)

            xs.startTag(null, OpmlSymbols.BODY)
            for (feed in feeds) {
                Logd(TAG, "writeDocument ${feed.title}")
                xs.startTag(null, OpmlSymbols.OUTLINE)
                xs.attribute(null, OpmlSymbols.TEXT, feed.title)
                xs.attribute(null, OpmlSymbols.TITLE, feed.title)
                if (feed.type != null) xs.attribute(null, OpmlSymbols.TYPE, feed.type)
                if (feed.downloadUrl != null) xs.attribute(null, OpmlSymbols.XMLURL, feed.downloadUrl)
                if (feed.link != null) xs.attribute(null, OpmlSymbols.HTMLURL, feed.link)
                xs.endTag(null, OpmlSymbols.OUTLINE)
            }
            xs.endTag(null, OpmlSymbols.BODY)
            xs.endTag(null, OpmlSymbols.OPML)
            xs.endDocument()
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
        private var elementList: ArrayList<OpmlElement>? = null

        /**
         * Reads an Opml document and returns a list of all OPML elements it can find
         * @throws IOException
         * @throws XmlPullParserException
         */
        @Throws(XmlPullParserException::class, IOException::class)
        fun readDocument(reader: Reader?): ArrayList<OpmlElement> {
            elementList = ArrayList()
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(reader)
            var eventType = xpp.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_DOCUMENT -> Logd(TAG, "Reached beginning of document")
                    XmlPullParser.START_TAG -> when {
                        xpp.name == OpmlSymbols.OPML -> {
                            isInOpml = true
                            Logd(TAG, "Reached beginning of OPML tree.")
                        }
                        isInOpml && xpp.name == OpmlSymbols.OUTLINE -> {
//                        TODO: check more about this, java.io.IOException: Underlying input stream returned zero bytes
                            val element = OpmlElement()
                            element.text = xpp.getAttributeValue(null, OpmlSymbols.TITLE) ?: xpp.getAttributeValue(null, OpmlSymbols.TEXT)
                            element.xmlUrl = xpp.getAttributeValue(null, OpmlSymbols.XMLURL)
                            element.htmlUrl = xpp.getAttributeValue(null, OpmlSymbols.HTMLURL)
                            element.type = xpp.getAttributeValue(null, OpmlSymbols.TYPE)
                            if (element.xmlUrl != null) {
                                if (element.text == null) element.text = element.xmlUrl
                                elementList!!.add(element)
                            } else Logd(TAG, "Skipping element because of missing xml url")
                        }
                    }
                }
//           TODO: on first install app: java.io.IOException: Underlying input stream returned zero bytes
                try { eventType = xpp.next()
                } catch(e: Exception) {
                    Log.e(TAG, "xpp.next() invalid: $e")
                    break
                }
            }
            return elementList!!
        }

        companion object {
            private val TAG: String = OpmlReader::class.simpleName ?: "Anonymous"
        }
    }

    companion object {
        fun startImport(context: Context, uri: Uri, CB: (List<OpmlElement>)->Unit) {
            val TAG = "OpmlTransporter"
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val opmlFileStream = context.contentResolver.openInputStream(uri)
                    val bomInputStream = BOMInputStream(opmlFileStream)
                    val bom = bomInputStream.bom
                    val charsetName = if (bom == null) "UTF-8" else bom.charsetName
                    val reader: Reader = InputStreamReader(bomInputStream, charsetName)
                    val opmlReader = OpmlReader()
                    val result = opmlReader.readDocument(reader)
                    reader.close()
                    withContext(Dispatchers.Main) { CB(result) }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Logd(TAG, Log.getStackTraceString(e))
                        val message = if (e.message == null) "" else e.message!!
                        if (message.lowercase().contains("permission")) {
                            val permission = ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                            if (permission != PackageManager.PERMISSION_GRANTED) {
//                                requestPermission()
                                CB(listOf())
                                return@withContext
                            }
                        }
                        val alert = MaterialAlertDialogBuilder(context)
                        alert.setTitle(R.string.error_label)
                        val userReadable = context.getString(R.string.opml_reader_error)
                        val details = e.message
                        val total = """
                    $userReadable
                    
                    $details
                    """.trimIndent()
                        val errorMessage = SpannableString(total)
                        errorMessage.setSpan(ForegroundColorSpan(-0x77777778), userReadable.length, total.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        alert.setMessage(errorMessage)
                        alert.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
//                            finish()
                        }
                        alert.show()
                        CB(listOf())
                    }
                }
            }
        }
    }
}