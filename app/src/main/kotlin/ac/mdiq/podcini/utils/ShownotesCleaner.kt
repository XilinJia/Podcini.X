package ac.mdiq.podcini.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.utils.durationStringLongToMs
import ac.mdiq.podcini.storage.utils.durationStringShortToMs
import ac.mdiq.podcini.ui.compose.isLightTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document

/**
 * Cleans up and prepares shownotes:
 * - Guesses time stamps to make them clickable
 * - Removes some formatting
 */
class ShownotesCleaner {
    private val noShownotesLabel = getAppContext().getString(R.string.no_shownotes_label)
    private val webviewStyle: String

    init {
        timeIt("$TAG start of init")
        val isLightMode = isLightTheme()

        val colorPrimary = (if (isLightMode) Color(0xFF000000) else Color(0xFFFFFFFF) ).toHtmlRgba()
        val colorAccent = (if (isLightMode) Color(0xFF6200EE) else Color(0xFFBB86FC) ).toHtmlRgba()
        webviewStyle = getHtmlStyle(colorPrimary, colorAccent, 0)
        timeIt("$TAG end of init")
    }

    fun getHtmlStyle(colorText: String, colorLink: String, padding: Int): String {
        return """
            * {
                 color: $colorText;
                 overflow-wrap: break-word;
            }
            a {
                 font-style: normal;
                 text-decoration: none;
                 font-weight: normal;
                 color: $colorLink;
            }
            a.timecode {
                 color: #669900;
            }
            img, iframe {
                 display: block;
                 margin: 10 auto;
                 max-width: 100%;
                 height: auto;
            }
            body {
                 padding: 0.5rem;
            }
            p#apNoShownotes {
                position: fixed;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                text-align: center;
                -webkit-text-size-adjust: none;
            }
        """.trimIndent()
    }

    fun Color.toHtmlRgba(): String {
        val argb = this.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val a = (argb shr 24) and 0xFF

        return "rgba($r, $g, $b, ${a / 255.0})"
    }

    /**
     * Applies an app-specific CSS stylesheet and adds timecode links (optional).
     * This method does NOT change the original shownotes string of the shownotesProvider object and it should
     * also not be changed by the caller.
     * @return The processed HTML string.
     */
    fun processShownotes(rawShownotes: String, playableDuration: Int): String {
        Logd(TAG, "processShownotes called")

        var shownotes = rawShownotes
        if (shownotes.isEmpty()) {
            Logd(TAG, "shownotesProvider contained no shownotes. Returning 'no shownotes' message")
            shownotes = "<html><head></head><body><p id='apNoShownotes'>$noShownotesLabel</p></body></html>"
        }

        // replace ASCII line breaks with HTML ones if shownotes don't contain HTML line breaks already
        if (!LINE_BREAK_REGEX.containsMatchIn(shownotes) && !shownotes.contains("<p>")) shownotes = shownotes.replace("\n", "<br />")
        val document = Ksoup.parse(shownotes)   // TODO: this is time consuming
        cleanCss(document)
        document.head().appendElement("style").attr("type", "text/css").text(webviewStyle)
        addTimecodes(document, playableDuration)
        return document.toString()
    }

    private fun addTimecodes(document: Document, playableDuration: Int) {
        val elementsWithTimeCodes = document.body().getElementsMatchingOwnText(TIMECODE_REGEX)
        Logd(TAG, "Recognized " + elementsWithTimeCodes.size + " timecodes")
        if (elementsWithTimeCodes.isEmpty()) return  // No elements with timecodes

        var useHourFormat = true
        if (playableDuration != Int.MAX_VALUE) {
            // We need to decide if we are going to treat short timecodes as HH:MM or MM:SS. To do
            // so we will parse all the short timecodes and see if they fit in the duration. If one
            // does not we will use MM:SS, otherwise all will be parsed as HH:MM.
            elementsWithTimeCodes.forEach { element ->
                val matches = TIMECODE_REGEX.findAll(element.html())
                for (match in matches) {
                    if (match.groups[1] == null) {
                        val time = durationStringShortToMs(match.value, true)
                        if (time > playableDuration) {
                            useHourFormat = false
                            break
                        }
                    }
                }
                if (!useHourFormat) return@forEach
            }
        }
        for (element in elementsWithTimeCodes) {
            val originalHtml = element.html()
            val newHtml = TIMECODE_REGEX.replace(originalHtml) { match ->
                val group = match.value
                val time = if (match.groups[1] != null) durationStringLongToMs(group) else durationStringShortToMs(group, useHourFormat)
                if (time < playableDuration) """<a class="timecode" href="podcini://timecode/$time">$group</a>""" else group
            }
            element.html(newHtml)
        }
    }

    private fun cleanCss(document: Document) {
        Logd(TAG, "cleanCss number of elements: ${document.getAllElements().size}")
        for (element in document.getAllElements()) {
            when {
                element.hasAttr("style") -> element.attr("style", element.attr("style").replace(CSS_COLOR.toRegex(), ""))
                element.tagName() == "style" -> element.html(cleanStyleTag(element.html()))
            }
        }
    }

    companion object {
        private val TAG: String = ShownotesCleaner::class.simpleName ?: "Anonymous"

        private val TIMECODE_LINK_REGEX = Regex("podcini://timecode/(\\d+)")

        private val HTTP_TIMECODE_LINK_REGEX = Regex("^https?://[^\\s]+[?&]t=(\\d+)")

        private const val TIMECODE_LINK = "<a class=\"timecode\" href=\"podcini://timecode/%d\">%s</a>"
        private val TIMECODE_REGEX = Regex("\\b((\\d+):)?(\\d+):(\\d{2})\\b")
        private val LINE_BREAK_REGEX = Regex("<br */?>")
        private const val CSS_COLOR = "(?<=(\\s|;|^))color\\s*:([^;])*;"
        private const val CSS_COMMENT = "/\\*.*?\\*/"

        fun isTimecodeLink(link: String?): Boolean {
            if (link == null) return false
            if(TIMECODE_LINK_REGEX.matches(link)) return true
            return false
        }

        fun isHTTPTimecodeLink(link: String?): Boolean {
            if (link == null) return false
            if (HTTP_TIMECODE_LINK_REGEX.matches(link)) return true
            return false
        }

        /**
         * Returns the time in milliseconds that is attached to this link or -1
         * if the link is no valid timecode link.
         */
        fun getTimecodeLinkTime(link: String?): Int {
            if (isTimecodeLink(link)) {
                val match = TIMECODE_LINK_REGEX.find(link!!)
                if (match != null) try { return match.groupValues[1].toInt() } catch (e: Exception) { Logs(TAG, e) }
            }
            if (isHTTPTimecodeLink(link)) {
                val match = HTTP_TIMECODE_LINK_REGEX.find(link!!)
                if (match != null) try { return match.groupValues[1].toInt() * 1000 } catch (e: Exception) { Logs(TAG, e) }
            }
            return -1
        }

        fun cleanStyleTag(oldCss: String): String {
            return oldCss.replace(CSS_COMMENT.toRegex(), "").replace(CSS_COLOR.toRegex(), "")
        }
    }
}