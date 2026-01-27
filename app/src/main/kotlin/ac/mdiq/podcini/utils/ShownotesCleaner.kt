package ac.mdiq.podcini.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.utils.durationStringLongToMs
import ac.mdiq.podcini.storage.utils.durationStringShortToMs
import ac.mdiq.podcini.ui.compose.isLightTheme
import android.util.TypedValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.apache.commons.io.IOUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.Locale
import java.util.regex.Pattern

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
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, getAppContext().resources.displayMetrics).toInt()
        var styleString: String? = ""
        try {
            val templateStream = getAppContext().assets.open("shownotes-style.css")
            styleString = IOUtils.toString(templateStream, "UTF-8")
            templateStream.close()
        } catch (e: IOException) { Logs(TAG, e) }
        webviewStyle = String.format(Locale.US, styleString!!, colorPrimary, colorAccent, margin, margin, margin, margin)
        timeIt("$TAG end of init")
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
        if (!LINE_BREAK_REGEX.matcher(shownotes).find() && !shownotes.contains("<p>")) shownotes = shownotes.replace("\n", "<br />")
        val document = Jsoup.parse(shownotes)   // TODO: this is time consuming
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

            for (element in elementsWithTimeCodes) {
                val matcherForElement = TIMECODE_REGEX.matcher(element.html())
                while (matcherForElement.find()) {
                    // We only want short timecodes right now.
                    if (matcherForElement.group(1) == null) {
                        val time = durationStringShortToMs(matcherForElement.group(0)!!, true)
                        // If the parsed timecode is greater then the duration then we know we need to
                        // use the minute format so we are done.
                        if (time > playableDuration) {
                            useHourFormat = false
                            break
                        }
                    }
                }
                if (!useHourFormat) break
            }
        }

        for (element in elementsWithTimeCodes) {
            val matcherForElement = TIMECODE_REGEX.matcher(element.html())
            val buffer = StringBuffer()

            while (matcherForElement.find()) {
                val group = matcherForElement.group(0) ?: continue
                val time = if (matcherForElement.group(1) != null) durationStringLongToMs(group)
                else durationStringShortToMs(group, useHourFormat)
                var replacementText = group
                if (time < playableDuration) replacementText = String.format(Locale.US, TIMECODE_LINK, time, group)
                matcherForElement.appendReplacement(buffer, replacementText)
            }

            matcherForElement.appendTail(buffer)
            element.html(buffer.toString())
        }
    }

    private fun cleanCss(document: Document) {
        Logd(TAG, "cleanCss number of elements: ${document.allElements.size}")
        for (element in document.allElements) {
            when {
                element.hasAttr("style") -> element.attr("style", element.attr("style").replace(CSS_COLOR.toRegex(), ""))
                element.tagName() == "style" -> element.html(cleanStyleTag(element.html()))
            }
        }
    }

    companion object {
        private val TAG: String = ShownotesCleaner::class.simpleName ?: "Anonymous"

        private val TIMECODE_LINK_REGEX: Pattern = Pattern.compile("podcini://timecode/(\\d+)")

        private val HTTP_TIMECODE_LINK_REGEX: Pattern = Pattern.compile("^https?://[^\\s]+[?&]t=(\\d+)")

        private const val TIMECODE_LINK = "<a class=\"timecode\" href=\"podcini://timecode/%d\">%s</a>"
        private val TIMECODE_REGEX: Pattern = Pattern.compile("\\b((\\d+):)?(\\d+):(\\d{2})\\b")
        private val LINE_BREAK_REGEX: Pattern = Pattern.compile("<br */?>")
        private const val CSS_COLOR = "(?<=(\\s|;|^))color\\s*:([^;])*;"
        private const val CSS_COMMENT = "/\\*.*?\\*/"

        fun isTimecodeLink(link: String?): Boolean {
            if (link == null) return false
            if(link.matches(TIMECODE_LINK_REGEX.pattern().toRegex())) return true
            return false
        }

        fun isHTTPTimecodeLink(link: String?): Boolean {
            if (link == null) return false
            if (link.matches(HTTP_TIMECODE_LINK_REGEX.pattern().toRegex())) return true
            return false
        }

        /**
         * Returns the time in milliseconds that is attached to this link or -1
         * if the link is no valid timecode link.
         */
        fun getTimecodeLinkTime(link: String?): Int {
            if (isTimecodeLink(link)) {
                val m = TIMECODE_LINK_REGEX.matcher(link!!)
                try { if (m.find()) return m.group(1)?.toInt()?:0 } catch (e: NumberFormatException) { Logs(TAG, e) }
            }
            if (isHTTPTimecodeLink(link)) {
                val m = HTTP_TIMECODE_LINK_REGEX.matcher(link!!)
                try { if (m.find()) return (m.group(1)?.toInt()?:0) * 1000 } catch (e: NumberFormatException) { Logs(TAG, e) }
            }
            return -1
        }

        fun cleanStyleTag(oldCss: String): String {
            return oldCss.replace(CSS_COMMENT.toRegex(), "").replace(CSS_COLOR.toRegex(), "")
        }
    }
}