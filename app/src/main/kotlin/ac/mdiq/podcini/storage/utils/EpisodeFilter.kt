package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.R
import ac.mdiq.podcini.util.Logd
import java.io.Serializable

class EpisodeFilter(vararg properties_: String) : Serializable {
    val propertySet: HashSet<String> = setOf(*properties_).filter { it.isNotEmpty() }.map {it.trim()}.toHashSet()

    var durationFloor: Int = 0
    var durationCeiling: Int = Int.MAX_VALUE

    var titleText: String = ""

    constructor(properties: String) : this(*(properties.split(",").toTypedArray()))

    fun add(vararg properties_: String) {
        propertySet.addAll(setOf(*properties_).filter { it.isNotEmpty() }.map {it.trim()})
    }

    fun queryString(): String {
        val statements: MutableList<String> = mutableListOf()
        val mediaTypeQuerys = mutableListOf<String>()
        if (propertySet.contains(States.unknown.name)) mediaTypeQuerys.add(" mimeType == nil OR mimeType == '' ")
        if (propertySet.contains(States.audio.name)) mediaTypeQuerys.add(" mimeType BEGINSWITH 'audio' ")
        if (propertySet.contains(States.video.name)) mediaTypeQuerys.add(" mimeType BEGINSWITH 'video' ")
        if (propertySet.contains(States.audio_app.name)) mediaTypeQuerys.add(" mimeType IN {\"application/ogg\", \"application/opus\", \"application/x-flac\"} ")
        if (mediaTypeQuerys.isNotEmpty()) {
            val query = StringBuilder(" (" + mediaTypeQuerys[0])
            if (mediaTypeQuerys.size > 1) for (r in mediaTypeQuerys.subList(1, mediaTypeQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            statements.add(query.toString())
        }

        val ratingQuerys = mutableListOf<String>()
        if (propertySet.contains(States.unrated.name)) ratingQuerys.add(" rating == ${Rating.UNRATED.code} ")
        if (propertySet.contains(States.trash.name)) ratingQuerys.add(" rating == ${Rating.TRASH.code} ")
        if (propertySet.contains(States.bad.name)) ratingQuerys.add(" rating == ${Rating.BAD.code} ")
        if (propertySet.contains(States.neutral.name)) ratingQuerys.add(" rating == ${Rating.OK.code} ")
        if (propertySet.contains(States.good.name)) ratingQuerys.add(" rating == ${Rating.GOOD.code} ")
        if (propertySet.contains(States.superb.name)) ratingQuerys.add(" rating == ${Rating.SUPER.code} ")
        if (ratingQuerys.isNotEmpty()) {
            val query = StringBuilder(" (" + ratingQuerys[0])
            if (ratingQuerys.size > 1) for (r in ratingQuerys.subList(1, ratingQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            statements.add(query.toString())
        }

        val stateQuerys = mutableListOf<String>()
        if (propertySet.contains(States.unspecified.name)) stateQuerys.add(" playState == ${EpisodeState.UNSPECIFIED.code} ")
        if (propertySet.contains(States.building.name)) stateQuerys.add(" playState == ${EpisodeState.BUILDING.code} ")
        if (propertySet.contains(States.new.name)) stateQuerys.add(" playState == ${EpisodeState.NEW.code} ")
        if (propertySet.contains(States.unplayed.name)) stateQuerys.add(" playState == ${EpisodeState.UNPLAYED.code} ")
        if (propertySet.contains(States.later.name)) stateQuerys.add(" playState == ${EpisodeState.LATER.code} ")
        if (propertySet.contains(States.soon.name)) stateQuerys.add(" playState == ${EpisodeState.SOON.code} ")
        if (propertySet.contains(States.inQueue.name)) stateQuerys.add(" playState == ${EpisodeState.QUEUE.code} ")
        if (propertySet.contains(States.inProgress.name)) stateQuerys.add(" playState == ${EpisodeState.PROGRESS.code} ")
        if (propertySet.contains(States.again.name)) stateQuerys.add(" playState == ${EpisodeState.AGAIN.code} ")
        if (propertySet.contains(States.forever.name)) stateQuerys.add(" playState == ${EpisodeState.FOREVER.code} ")
        if (propertySet.contains(States.skipped.name)) stateQuerys.add(" playState == ${EpisodeState.SKIPPED.code} ")
        if (propertySet.contains(States.played.name)) stateQuerys.add(" playState == ${EpisodeState.PLAYED.code} ")
        if (propertySet.contains(States.passed.name)) stateQuerys.add(" playState == ${EpisodeState.PASSED.code} ")
        if (propertySet.contains(States.ignored.name)) stateQuerys.add(" playState == ${EpisodeState.IGNORED.code} ")
        if (stateQuerys.isNotEmpty()) {
            val query = StringBuilder(" (" + stateQuerys[0])
            if (stateQuerys.size > 1) for (r in stateQuerys.subList(1, stateQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            statements.add(query.toString())
        }

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

        Logd("EpisodeFilter", "titleText: $titleText")
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
        if (durationQuerys.isNotEmpty()) {
            Logd("EpisodeFilter", "durationFloor: $durationFloor durationCeiling: $durationCeiling")
            val query = StringBuilder(" (" + durationQuerys[0])
            if (durationQuerys.size > 1) for (r in durationQuerys.subList(1, durationQuerys.size)) {
                query.append(" OR ")
                query.append(r)
            }
            query.append(") ")
            statements.add(query.toString())
        }

        when {
            propertySet.contains(States.has_comments.name) -> statements.add(" comment != '' ")
            propertySet.contains(States.no_comments.name) -> statements.add(" comment == '' ")
        }

        if (statements.isEmpty()) return "id > 0"
        val query = StringBuilder(" (" + statements[0])
        if (statements.size > 1)  for (r in statements.subList(1, statements.size)) {
            query.append(" AND ")
            query.append(r)
        }
        query.append(") ")
        Logd("EpisodeFilter", "queryString: $query")
        return query.toString()
    }

    @Suppress("EnumEntryName")
    enum class States {
        unspecified,
        building,
        new,
        unplayed,
        later,
        soon,
        inQueue,
        inProgress,
        again,
        forever,
        skipped,
        played,
        passed,
        ignored,

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
        higher
    }

    enum class EpisodesFilterGroup(val nameRes: Int, vararg values_: FilterProperties, val exclusive: Boolean = false) {
        RATING(R.string.rating_label,
            FilterProperties(R.string.unrated, States.unrated.name),
            FilterProperties(R.string.trash, States.trash.name),
            FilterProperties(R.string.bad, States.bad.name),
            FilterProperties(R.string.OK, States.neutral.name),
            FilterProperties(R.string.good, States.good.name),
            FilterProperties(R.string.Super, States.superb.name),
        ),
        PLAY_STATE(R.string.playstate,
            FilterProperties(R.string.unspecified, States.unspecified.name),
            FilterProperties(R.string.building, States.building.name),
            FilterProperties(R.string.new_label, States.new.name),
            FilterProperties(R.string.unplayed, States.unplayed.name),
            FilterProperties(R.string.later, States.later.name),
            FilterProperties(R.string.soon, States.soon.name),
            FilterProperties(R.string.in_queue, States.inQueue.name),
            FilterProperties(R.string.in_progress, States.inProgress.name),
            FilterProperties(R.string.again, States.again.name),
            FilterProperties(R.string.forever, States.forever.name),
            FilterProperties(R.string.skipped, States.skipped.name),
            FilterProperties(R.string.played, States.played.name),
            FilterProperties(R.string.passed, States.passed.name),
            FilterProperties(R.string.ignored, States.ignored.name),
        ),
        OPINION(R.string.has_comments, FilterProperties(R.string.yes, States.has_comments.name), FilterProperties(R.string.no, States.no_comments.name),exclusive = true),
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

        val properties: Array<FilterProperties> = arrayOf(*values_)

        class FilterProperties(val displayName: Int, val filterId: String)
    }

    companion object {
        fun unfiltered(): EpisodeFilter = EpisodeFilter("")
    }
}
