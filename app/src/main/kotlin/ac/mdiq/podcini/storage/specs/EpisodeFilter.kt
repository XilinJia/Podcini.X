package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.R
import ac.mdiq.podcini.utils.Logd
import java.io.Serializable

class EpisodeFilter(vararg properties_: String, var andOr: String = "AND") : Serializable {
    val propertySet = properties_.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

    var durationFloor: Int = 0
    var durationCeiling: Int = Int.MAX_VALUE

    var titleText: String = ""

    fun add(vararg properties_: String) {
        propertySet.addAll(properties_.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun add(filter: EpisodeFilter): EpisodeFilter {
        propertySet.addAll(filter.propertySet)
        return this
    }

    fun remove(vararg properties_: String) {
        propertySet.removeAll(properties_.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }

    fun addTag(tag: String) {
        propertySet.add("${States.tags.name} $tag")
    }

    fun removeTag(tag: String) {
        propertySet.remove("${States.tags.name} $tag")
    }

    fun containsTag(tag: String): Boolean {
        return "${States.tags.name} $tag" in propertySet
    }

    fun addTextQuery(query: String) {
        propertySet.removeIf { it.startsWith(States.text.name) }
        if (query.isNotBlank()) propertySet.add("${States.text.name} $query")
    }

    fun queryString(): String {
        Logd(TAG, "queryString propertySet: ${propertySet.size} $propertySet")
        Logd(TAG, "actual type: ${propertySet::class}")
        propertySet.forEach { Logd(TAG, "element: [$it] hash=${it.hashCode()}") }

        val statements: MutableList<String> = mutableListOf()
        fun assembleSubQueries(qSet: List<String>) {
            if (qSet.isNotEmpty()) {
                val query = StringBuilder(" (" + qSet[0])
                if (qSet.size > 1) for (r in qSet.subList(1, qSet.size)) {
                    query.append(" OR ")
                    query.append(r)
                }
                query.append(") ")
                statements.add(query.toString())
            }
        }

        val mediaTypeQuerys = mutableListOf<String>()
        if (propertySet.contains(States.unknown.name)) mediaTypeQuerys.add(" mimeType == nil OR mimeType == '' ")
        if (propertySet.contains(States.audio.name)) mediaTypeQuerys.add(" mimeType BEGINSWITH 'audio' ")
        if (propertySet.contains(States.video.name)) mediaTypeQuerys.add(" mimeType BEGINSWITH 'video' ")
        if (propertySet.contains(States.audio_app.name)) mediaTypeQuerys.add(" mimeType IN {\"application/ogg\", \"application/opus\", \"application/x-flac\"} ")
        assembleSubQueries(mediaTypeQuerys)

        val ratingQuerys = mutableListOf<String>()
        if (propertySet.contains(States.unrated.name)) ratingQuerys.add(" rating == ${Rating.UNRATED.code} ")
        if (propertySet.contains(States.trash.name)) ratingQuerys.add(" rating == ${Rating.TRASH.code} ")
        if (propertySet.contains(States.bad.name)) ratingQuerys.add(" rating == ${Rating.BAD.code} ")
        if (propertySet.contains(States.neutral.name)) ratingQuerys.add(" rating == ${Rating.OK.code} ")
        if (propertySet.contains(States.good.name)) ratingQuerys.add(" rating == ${Rating.GOOD.code} ")
        if (propertySet.contains(States.superb.name)) ratingQuerys.add(" rating == ${Rating.SUPER.code} ")
        assembleSubQueries(ratingQuerys)

        val stateQuerys = mutableListOf<String>()
        if (propertySet.contains(States.UNSPECIFIED.name)) stateQuerys.add(" playState == ${EpisodeState.UNSPECIFIED.code} ")
        if (propertySet.contains(States.ERROR.name)) stateQuerys.add(" playState == ${EpisodeState.ERROR.code} ")
        if (propertySet.contains(States.BUILDING.name)) stateQuerys.add(" playState == ${EpisodeState.BUILDING.code} ")
        if (propertySet.contains(States.NEW.name)) stateQuerys.add(" playState == ${EpisodeState.NEW.code} ")
        if (propertySet.contains(States.UNPLAYED.name)) stateQuerys.add(" playState == ${EpisodeState.UNPLAYED.code} ")
        if (propertySet.contains(States.LATER.name)) stateQuerys.add(" playState == ${EpisodeState.LATER.code} ")
        if (propertySet.contains(States.SOON.name)) stateQuerys.add(" playState == ${EpisodeState.SOON.code} ")
        if (propertySet.contains(States.QUEUE.name)) stateQuerys.add(" playState == ${EpisodeState.QUEUE.code} ")
        if (propertySet.contains(States.PROGRESS.name)) stateQuerys.add(" playState == ${EpisodeState.PROGRESS.code} ")
        if (propertySet.contains(States.AGAIN.name)) stateQuerys.add(" playState == ${EpisodeState.AGAIN.code} ")
        if (propertySet.contains(States.FOREVER.name)) stateQuerys.add(" playState == ${EpisodeState.FOREVER.code} ")
        if (propertySet.contains(States.SKIPPED.name)) stateQuerys.add(" playState == ${EpisodeState.SKIPPED.code} ")
        if (propertySet.contains(States.PLAYED.name)) stateQuerys.add(" playState == ${EpisodeState.PLAYED.code} ")
        if (propertySet.contains(States.PASSED.name)) stateQuerys.add(" playState == ${EpisodeState.PASSED.code} ")
        if (propertySet.contains(States.IGNORED.name)) stateQuerys.add(" playState == ${EpisodeState.IGNORED.code} ")
        assembleSubQueries(stateQuerys)

        val tagsQuerys = mutableListOf<String>()
        val tags = propertySet.filter { it.startsWith(States.tags.name) }
            .mapNotNull { it.removePrefix("${States.tags.name} ").takeIf(String::isNotBlank) }
        for (t in tags) tagsQuerys.add(" ANY tags == '$t' ")
        assembleSubQueries(tagsQuerys)

        val textQuerys = mutableListOf<String>()
        val tqs = propertySet.filter { it.startsWith(States.text.name) }
            .mapNotNull { it.removePrefix("${States.text.name} ").takeIf(String::isNotBlank) }
        Logd(TAG, "tqs: ${tqs.size}")
        for (tq in tqs) {
            Logd(TAG, "tq: $tq")
            textQuerys.add(tq)
        }
        assembleSubQueries(textQuerys)
        Logd(TAG, "queryString statements after textQuerys: $statements")

        when {
            propertySet.contains(States.paused.name) -> statements.add(" position > 0 ")
            propertySet.contains(States.not_paused.name) -> statements.add(" position == 0 ")
        }
        when {
            propertySet.contains(States.downloaded.name) -> statements.add("downloaded == true ")
            propertySet.contains(States.not_downloaded.name) -> statements.add("downloaded == false ")
        }
        when {
            propertySet.contains(States.auto_downloadable.name) -> statements.add("isAutoDownloadEnabled == true ")
            propertySet.contains(States.not_auto_downloadable.name) -> statements.add("isAutoDownloadEnabled == false ")
        }
//        when {
//            properties.contains(States.has_media.name) -> statements.add("media != nil ")
//            properties.contains(States.no_media.name) -> statements.add("media == nil ")
//        }
        when {
            propertySet.contains(States.has_chapters.name) -> statements.add("chapters.@count > 0 ")
            propertySet.contains(States.no_chapters.name) -> statements.add("chapters.@count == 0 ")
        }

        when {
            propertySet.contains(States.has_clips.name) -> statements.add("clips.@count > 0 ")
            propertySet.contains(States.no_clips.name) -> statements.add("clips.@count == 0 ")
        }

        when {
            propertySet.contains(States.has_marks.name) -> statements.add("marks.@count > 0 ")
            propertySet.contains(States.no_marks.name) -> statements.add("marks.@count == 0 ")
        }

        Logd(TAG, "queryString titleText: $titleText")
        if (titleText.isNotBlank()) {
            when {
                propertySet.contains(States.title_off.name) -> {}
                propertySet.contains(States.title_include.name) -> statements.add("title CONTAINS[c] '$titleText' ")
                propertySet.contains(States.title_exclude.name) -> statements.add("NOT (title CONTAINS[c] '$titleText') ")
            }
        }

        val durationQuerys = mutableListOf<String>()
        if (propertySet.contains(States.lower.name)) durationQuerys.add("duration < $durationFloor ")
        if (propertySet.contains(States.middle.name)) durationQuerys.add("duration >= $durationFloor AND duration <= $durationCeiling ")
        if (propertySet.contains(States.higher.name)) durationQuerys.add("duration > $durationCeiling ")
        assembleSubQueries(durationQuerys)

        Logd(TAG, "queryString ${propertySet.contains(States.has_comments.name)} ${States.has_comments.name} $propertySet")
        when {
            propertySet.contains(States.has_comments.name) -> statements.add(" comment != '' ")
            propertySet.contains(States.no_comments.name) -> statements.add(" comment == '' ")
        }

        when {
            propertySet.contains(States.tagged.name) -> statements.add(" tags.@count > 0 ")
            propertySet.contains(States.untagged.name) -> statements.add(" tags.@count == 0 ")
        }

        if (statements.isEmpty()) return "id > 0"
        val query = StringBuilder(" (" + statements[0])
        if (statements.size > 1)  for (r in statements.subList(1, statements.size)) {
            query.append(" $andOr ")
            query.append(r)
        }
        query.append(") ")
        Logd(TAG, "queryString query: $query")
        return query.toString()
    }

    fun extractText(): String {
        val tqs = propertySet.filter { it.startsWith(States.text.name) }
            .mapNotNull { it.removePrefix("${States.text.name} ").takeIf(String::isNotBlank) }
        if (tqs.isEmpty()) return ""

        val regex = Regex("""(?i)(\bNOT\b)?\s*\(?\s*[^()]*?\bcontains\[[^\]]*]\s*'([^']+)'""")
        val termPositivity = LinkedHashMap<String, Boolean>()

        for (m in regex.findAll(tqs[0])) {
            val notGroup = m.groups[1]?.value
            val value = m.groups[2]!!.value.trim()
            val isNegated = notGroup != null && notGroup.isNotBlank()
            val isPositive = !isNegated

            val prev = termPositivity[value]
            termPositivity[value] = when {
                prev == null -> isPositive
                prev -> true
                else -> isPositive
            }
        }

        return termPositivity.map { (term, positive) -> if (positive) term else "-$term" }.joinToString(", ")
    }

    @Suppress("EnumEntryName")
    enum class States {
        UNSPECIFIED,
        ERROR,
        BUILDING,
        NEW,
        UNPLAYED,
        LATER,
        SOON,
        QUEUE,
        PROGRESS,
        AGAIN,
        FOREVER,
        SKIPPED,
        PLAYED,
        PASSED,
        IGNORED,

        has_chapters,
        no_chapters,

        audio,
        video,
        unknown,
        audio_app,

        paused,
        not_paused,

        has_media,
        no_media,

        has_comments,
        no_comments,

        tagged,
        untagged,

        has_clips,
        no_clips,

        has_marks,
        no_marks,

//        queued,
//        not_queued,

        downloaded,
        not_downloaded,

        auto_downloadable,
        not_auto_downloadable,

        unrated,
        trash,
        bad,
        neutral,
        good,
        superb,

        title,
        title_include,
        title_exclude,
        title_off,

        duration,
        lower,
        middle,
        higher,

        tags,
        text
    }

    enum class EpisodesFilterGroup(val nameRes: Int, vararg values_: FilterProperties, val exclusive: Boolean = false) {
        RATING(
            R.string.rating_label,
            FilterProperties(R.string.unrated, States.unrated.name),
            FilterProperties(R.string.trash, States.trash.name),
            FilterProperties(R.string.bad, States.bad.name),
            FilterProperties(R.string.OK, States.neutral.name),
            FilterProperties(R.string.good, States.good.name),
            FilterProperties(R.string.Super, States.superb.name),
        ),
        PLAY_STATE(
            R.string.playstate,
            FilterProperties(R.string.unspecified, States.UNSPECIFIED.name),
            FilterProperties(R.string.error_label, States.ERROR.name),
            FilterProperties(R.string.building, States.BUILDING.name),
            FilterProperties(R.string.new_label, States.NEW.name),
            FilterProperties(R.string.unplayed, States.UNPLAYED.name),
            FilterProperties(R.string.later, States.LATER.name),
            FilterProperties(R.string.soon, States.SOON.name),
            FilterProperties(R.string.in_queue, States.QUEUE.name),
            FilterProperties(R.string.in_progress, States.PROGRESS.name),
            FilterProperties(R.string.again, States.AGAIN.name),
            FilterProperties(R.string.forever, States.FOREVER.name),
            FilterProperties(R.string.skipped, States.SKIPPED.name),
            FilterProperties(R.string.played, States.PLAYED.name),
            FilterProperties(R.string.passed, States.PASSED.name),
            FilterProperties(R.string.ignored, States.IGNORED.name),
        ),
        OPINION(R.string.has_comments, FilterProperties(R.string.yes, States.has_comments.name), FilterProperties(R.string.no, States.no_comments.name), exclusive = true),

        TAGGED(R.string.tagged, FilterProperties(R.string.yes, States.tagged.name), FilterProperties(R.string.no, States.untagged.name), exclusive = true),

        CLIPPED(R.string.has_clips, FilterProperties(R.string.yes, States.has_clips.name), FilterProperties(R.string.no, States.no_clips.name),exclusive = true),

        MARKED(R.string.has_marks, FilterProperties(R.string.yes, States.has_marks.name), FilterProperties(R.string.no, States.no_marks.name),exclusive = true),

        //        MEDIA(R.string.has_media, ItemProperties(R.string.yes, States.has_media.name), ItemProperties(R.string.no, States.no_media.name)),
        DOWNLOADED(R.string.downloaded_label, FilterProperties(R.string.yes, States.downloaded.name), FilterProperties(R.string.no, States.not_downloaded.name), exclusive = true),

        DURATION(R.string.duration,
            FilterProperties(R.string.lower, States.lower.name),
            FilterProperties(R.string.middle, States.middle.name),
            FilterProperties(R.string.higher, States.higher.name)),

        TITLE_TEXT(R.string.title,
            FilterProperties(R.string.include, States.title_include.name),
            FilterProperties(R.string.off, States.title_off.name),
            FilterProperties(R.string.exclude, States.title_exclude.name), exclusive = true),

        CHAPTERS(R.string.has_chapters, FilterProperties(R.string.yes, States.has_chapters.name), FilterProperties(R.string.no, States.no_chapters.name), exclusive = true),
        MEDIA_TYPE(R.string.media_type,
            FilterProperties(R.string.unknown, States.unknown.name),
            FilterProperties(R.string.audio, States.audio.name),
            FilterProperties(R.string.video, States.video.name),
            FilterProperties(R.string.audio_app, States.audio_app.name)
        ),
        AUTO_DOWNLOADABLE(R.string.auto_downloadable_label, FilterProperties(R.string.yes, States.auto_downloadable.name), FilterProperties(R.string.no, States.not_auto_downloadable.name), exclusive = true);

//        val properties: Array<FilterProperties> = arrayOf(*values_)
        val properties: Array<out FilterProperties> = values_

        class FilterProperties(val displayName: Int, val filterId: String)
    }

    companion object {
        private const val TAG = "EpisodeFilter"

        fun unfiltered(): EpisodeFilter = EpisodeFilter("")
    }
}