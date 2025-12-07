package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.model.Episode
import io.github.xilinjia.krdb.query.Sort
import java.util.Date
import java.util.Locale
import kotlin.collections.iterator

enum class EpisodeSortOrder(val code: Int, val res: Int, val conditional: Boolean = false) {
    TIME_IN_QUEUE_ASC(21, R.string.time_in_queue, true),
    TIME_IN_QUEUE_DESC(22, R.string.time_in_queue, true),

    DATE_ASC(1, R.string.publish_date),
    DATE_DESC(2, R.string.publish_date),

    EPISODE_TITLE_ASC(3, R.string.episode_title),
    EPISODE_TITLE_DESC(4, R.string.episode_title),

    DURATION_ASC(5, R.string.duration),
    DURATION_DESC(6, R.string.duration),

    VIEWS_ASC(17, R.string.view_count, true),
    VIEWS_DESC(18, R.string.view_count, true),

    PLAYED_DATE_ASC(11, R.string.last_played_date),
    PLAYED_DATE_DESC(12, R.string.last_played_date),
    COMPLETED_DATE_ASC(13, R.string.completed_date),
    COMPLETED_DATE_DESC(14, R.string.completed_date),
    DOWNLOAD_DATE_ASC(15, R.string.download_date),
    DOWNLOAD_DATE_DESC(16, R.string.download_date),
    COMMENT_DATE_ASC(19, R.string.last_comment_date),
    COMMENT_DATE_DESC(20, R.string.last_comment_date),

    TIME_OUT_QUEUE_ASC(23, R.string.time_out_queue, true),
    TIME_OUT_QUEUE_DESC(24, R.string.time_out_queue, true),

    VIEWS_SPEED_ASC(31, R.string.view_speed, true),
    VIEWS_SPEED_DESC(32, R.string.view_speed, true),

    LIKES_ASC(33, R.string.like_count, true),
    LIKES_DESC(34, R.string.like_count, true),

    EPISODE_FILENAME_ASC(7, R.string.filename),
    EPISODE_FILENAME_DESC(8, R.string.filename),
    FEED_TITLE_ASC(101, R.string.feed_title),
    FEED_TITLE_DESC(102, R.string.feed_title),

    SIZE_ASC(9, R.string.size),
    SIZE_DESC(10, R.string.size),

    RANDOM(103, R.string.random, true),
    RANDOM1(104, R.string.random, true),
    SMART_SHUFFLE_ASC(105, R.string.smart_shuffle, true),
    SMART_SHUFFLE_DESC(106, R.string.smart_shuffle, true);

    companion object {
        /**
         * Converts the string representation to its enum value. If the string value is unknown,
         * the given default value is returned.
         */
        fun parseWithDefault(value: String, defaultValue: EpisodeSortOrder): EpisodeSortOrder {
            return try { valueOf(value) } catch (e: IllegalArgumentException) { defaultValue }
        }

        fun fromCodeString(codeStr: String?): EpisodeSortOrder {
            if (codeStr.isNullOrEmpty()) return EPISODE_TITLE_ASC
            val code = codeStr.toInt()
            for (sortOrder in entries) if (sortOrder.code == code) return sortOrder
            return EPISODE_TITLE_ASC
//            throw IllegalArgumentException("Unsupported code: $code")
        }

        fun fromCode(code: Int): EpisodeSortOrder = EpisodeSortOrder.entries.firstOrNull { it.code == code } ?: EPISODE_TITLE_ASC

        fun toCodeString(sortOrder: EpisodeSortOrder): String = sortOrder.code.toString()

        fun valuesOf(stringValues: Array<String?>): Array<EpisodeSortOrder?> {
            val values = arrayOfNulls<EpisodeSortOrder>(stringValues.size)
            for (i in stringValues.indices) values[i] = valueOf(stringValues[i]!!)
            return values
        }

        /**
         * Returns a Permutor that sorts a list appropriate to the given sort order.
         * @return Permutor that sorts a list appropriate to the given sort order.
         */

        fun getPermutor(sortOrder: EpisodeSortOrder): Permutor<Episode> {
            var comparator: java.util.Comparator<Episode>? = null
            var permutor: Permutor<Episode>? = null

            when (sortOrder) {
                EPISODE_TITLE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f1).compareTo(itemTitle(f2)) }
                EPISODE_TITLE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemTitle(f2).compareTo(itemTitle(f1)) }
                DATE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f1).compareTo(pubDate(f2)) }
                DATE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> pubDate(f2).compareTo(pubDate(f1)) }
                DURATION_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f1).compareTo(duration(f2)) }
                DURATION_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> duration(f2).compareTo(duration(f1)) }
                EPISODE_FILENAME_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f1).compareTo(itemLink(f2)) }
                EPISODE_FILENAME_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> itemLink(f2).compareTo(itemLink(f1)) }
                PLAYED_DATE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f1).compareTo(playDate(f2)) }
                PLAYED_DATE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> playDate(f2).compareTo(playDate(f1)) }
                COMPLETED_DATE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f1).compareTo(completeDate(f2)) }
                COMPLETED_DATE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> completeDate(f2).compareTo(completeDate(f1)) }
                DOWNLOAD_DATE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> downloadDate(f1).compareTo(downloadDate(f2)) }
                DOWNLOAD_DATE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> downloadDate(f2).compareTo(downloadDate(f1)) }
                VIEWS_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewCount(f1).compareTo(viewCount(f2)) }
                VIEWS_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewCount(f2).compareTo(viewCount(f1)) }
                LIKES_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> likeCount(f1).compareTo(likeCount(f2)) }
                LIKES_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> likeCount(f2).compareTo(likeCount(f1)) }
                VIEWS_SPEED_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewSpeed(f1).compareTo(viewSpeed(f2)) }
                VIEWS_SPEED_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> viewSpeed(f2).compareTo(viewSpeed(f1)) }
                COMMENT_DATE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> commentDate(f1).compareTo(commentDate(f2)) }
                COMMENT_DATE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> commentDate(f2).compareTo(commentDate(f1)) }

                TIME_IN_QUEUE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> timeInQueue(f1).compareTo(timeInQueue(f2)) }
                TIME_IN_QUEUE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> timeInQueue(f2).compareTo(timeInQueue(f1)) }

                TIME_OUT_QUEUE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> timeOutQueue(f1).compareTo(timeOutQueue(f2)) }
                TIME_OUT_QUEUE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> timeOutQueue(f2).compareTo(timeOutQueue(f1)) }

                FEED_TITLE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f1).compareTo(feedTitle(f2)) }
                FEED_TITLE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> feedTitle(f2).compareTo(feedTitle(f1)) }
                RANDOM, RANDOM1 -> permutor = object : Permutor<Episode> {
                    override fun reorder(episodes: MutableList<Episode>?) {
                        if (!episodes.isNullOrEmpty()) episodes.shuffle()
                    }
                }
                SMART_SHUFFLE_ASC -> permutor = object : Permutor<Episode> {
                    override fun reorder(episodes: MutableList<Episode>?) {
                        if (!episodes.isNullOrEmpty()) smartShuffle(episodes as MutableList<Episode?>, true)
                    }
                }
                SMART_SHUFFLE_DESC -> permutor = object : Permutor<Episode> {
                    override fun reorder(episodes: MutableList<Episode>?) {
                        if (!episodes.isNullOrEmpty()) smartShuffle(episodes as MutableList<Episode?>, false)
                    }
                }
                SIZE_ASC -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f1).compareTo(size(f2)) }
                SIZE_DESC -> comparator = Comparator { f1: Episode?, f2: Episode? -> size(f2).compareTo(size(f1)) }
            }
            if (comparator != null) {
                val comparator2: java.util.Comparator<Episode> = comparator
                permutor = object : Permutor<Episode> {
                    override fun reorder(episodes: MutableList<Episode>?) {if (!episodes.isNullOrEmpty()) episodes.sortWith(comparator2)}
                }
            }
            return permutor!!
        }

        fun queryStringOf(sortOrder: EpisodeSortOrder?): String {
            return when (sortOrder) {
                EPISODE_TITLE_ASC -> "title ASC"
                EPISODE_TITLE_DESC -> "title DESC"
                DATE_ASC -> "pubDate ASC"
                DATE_DESC -> "pubDate DESC"
                DURATION_ASC -> "duration ASC"
                DURATION_DESC -> "duration DESC"
                EPISODE_FILENAME_ASC -> "link ASC"
                EPISODE_FILENAME_DESC -> "link DESC"
                PLAYED_DATE_ASC -> "lastPlayedTime ASC"
                PLAYED_DATE_DESC -> "lastPlayedTime DESC"
                COMPLETED_DATE_ASC -> "playbackCompletionTime ASC"
                COMPLETED_DATE_DESC -> "playbackCompletionTime DESC"
                DOWNLOAD_DATE_ASC -> "downloadTime ASC"
                DOWNLOAD_DATE_DESC -> "downloadTime DESC"
                VIEWS_ASC -> "viewCount ASC"
                VIEWS_DESC -> "viewCount DESC"
                LIKES_ASC -> "likeCount ASC"
                LIKES_DESC -> "likeCount DESC"
                COMMENT_DATE_ASC -> "commentTime ASC"
                COMMENT_DATE_DESC -> "commentTime DESC"
                TIME_IN_QUEUE_ASC -> "timeInQueue ASC"
                TIME_IN_QUEUE_DESC -> "timeInQueue DESC"
                TIME_OUT_QUEUE_ASC -> "timeOutQueue ASC"
                TIME_OUT_QUEUE_DESC -> "timeOutQueue DESC"

                FEED_TITLE_ASC -> "feed.title ASC"
                FEED_TITLE_DESC -> "feed.title DESC"
                SIZE_ASC -> "size ASC"
                SIZE_DESC -> "size DESC"
                else -> "pubDate DESC"

//                VIEWS_SPEED_ASC -> ""
//                VIEWS_SPEED_DESC -> ""
//                RANDOM, RANDOM1 -> permutor = object : Permutor<Episode> {
//                    override fun reorder(queue: MutableList<Episode>?) {
//                        if (!queue.isNullOrEmpty()) queue.shuffle()
//                    }
//                }
//                SMART_SHUFFLE_ASC -> permutor = object : Permutor<Episode> {
//                    override fun reorder(queue: MutableList<Episode>?) {
//                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, true)
//                    }
//                }
//                SMART_SHUFFLE_DESC -> permutor = object : Permutor<Episode> {
//                    override fun reorder(queue: MutableList<Episode>?) {
//                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, false)
//                    }
//                }
            }
        }

        fun sortPairOf(sortOrder: EpisodeSortOrder?): Pair<String, Sort> {
            return when (sortOrder) {
                EPISODE_TITLE_ASC -> Pair("title", Sort.ASCENDING)
                EPISODE_TITLE_DESC -> Pair("title", Sort.DESCENDING)
                DATE_ASC -> Pair("pubDate", Sort.ASCENDING)
                DATE_DESC -> Pair("pubDate", Sort.DESCENDING)
                DURATION_ASC -> Pair("duration", Sort.ASCENDING)
                DURATION_DESC -> Pair("duration", Sort.DESCENDING)
                EPISODE_FILENAME_ASC -> Pair("link", Sort.ASCENDING)
                EPISODE_FILENAME_DESC -> Pair("link", Sort.DESCENDING)
                PLAYED_DATE_ASC -> Pair("lastPlayedTime", Sort.ASCENDING)
                PLAYED_DATE_DESC -> Pair("lastPlayedTime", Sort.DESCENDING)
                COMPLETED_DATE_ASC -> Pair("playbackCompletionTime", Sort.ASCENDING)
                COMPLETED_DATE_DESC -> Pair("playbackCompletionTime", Sort.DESCENDING)
                DOWNLOAD_DATE_ASC -> Pair("downloadTime", Sort.ASCENDING)
                DOWNLOAD_DATE_DESC -> Pair("downloadTime", Sort.DESCENDING)
                VIEWS_ASC -> Pair("viewCount", Sort.ASCENDING)
                VIEWS_DESC -> Pair("viewCount", Sort.DESCENDING)
                LIKES_ASC -> Pair("likeCount", Sort.ASCENDING)
                LIKES_DESC -> Pair("likeCount", Sort.DESCENDING)
                COMMENT_DATE_ASC -> Pair("commentTime", Sort.ASCENDING)
                COMMENT_DATE_DESC -> Pair("commentTime", Sort.DESCENDING)
                TIME_IN_QUEUE_ASC -> Pair("timeInQueue", Sort.ASCENDING)
                TIME_IN_QUEUE_DESC -> Pair("timeInQueue", Sort.DESCENDING)
                TIME_OUT_QUEUE_ASC -> Pair("timeOutQueue", Sort.ASCENDING)
                TIME_OUT_QUEUE_DESC -> Pair("timeOutQueue", Sort.DESCENDING)

                FEED_TITLE_ASC -> Pair("feed.title", Sort.ASCENDING)
                FEED_TITLE_DESC -> Pair("feed.title", Sort.DESCENDING)
                SIZE_ASC -> Pair("size", Sort.ASCENDING)
                SIZE_DESC -> Pair("size", Sort.DESCENDING)
                else -> Pair("pubDate", Sort.DESCENDING)

                //                VIEWS_SPEED_ASC -> ""
                //                VIEWS_SPEED_DESC -> ""
                //                RANDOM, RANDOM1 -> permutor = object : Permutor<Episode> {
                //                    override fun reorder(queue: MutableList<Episode>?) {
                //                        if (!queue.isNullOrEmpty()) queue.shuffle()
                //                    }
                //                }
                //                SMART_SHUFFLE_ASC -> permutor = object : Permutor<Episode> {
                //                    override fun reorder(queue: MutableList<Episode>?) {
                //                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, true)
                //                    }
                //                }
                //                SMART_SHUFFLE_DESC -> permutor = object : Permutor<Episode> {
                //                    override fun reorder(queue: MutableList<Episode>?) {
                //                        if (!queue.isNullOrEmpty()) smartShuffle(queue as MutableList<Episode?>, false)
                //                    }
                //                }
            }
        }


        private fun pubDate(item: Episode?): Date = if (item == null) Date() else Date(item.pubDate)

        private fun playDate(item: Episode?): Long = item?.lastPlayedTime ?: 0

        private fun commentDate(item: Episode?): Long = item?.commentTime ?: 0

        private fun timeInQueue(item: Episode?): Long = item?.timeInQueue ?: 0
        private fun timeOutQueue(item: Episode?): Long = item?.timeOutQueue ?: 0

        private fun downloadDate(item: Episode?): Long = item?.downloadTime ?: 0

        private fun completeDate(item: Episode?): Date = item?.playbackCompletionDate ?: Date(0)

        private fun itemTitle(item: Episode?): String = (item?.title ?: "").lowercase(Locale.getDefault())

        private fun duration(item: Episode?): Int = item?.duration ?: 0

        private fun size(item: Episode?): Long = item?.size ?: 0

        private fun itemLink(item: Episode?): String = (item?.link ?: "").lowercase(Locale.getDefault())

        private fun feedTitle(item: Episode?): String = (item?.feed?.title ?: "").lowercase(Locale.getDefault())

        private fun viewCount(item: Episode?): Int = item?.viewCount ?: 0

        private fun likeCount(item: Episode?): Int = item?.likeCount ?: 0

        // per minute
        private fun viewSpeed(item: Episode?): Double = 60000.0 * (item?.viewCount ?: 0) / (System.currentTimeMillis() - (item?.pubDate ?: 0L))

        /**
         * Implements a reordering by pubdate that avoids consecutive episodes from the same feed in the queue.
         * A listener might want to hear episodes from any given feed in pubdate order, but would
         * prefer a more balanced ordering that avoids having to listen to clusters of consecutive
         * episodes from the same feed. This is what "Smart Shuffle" tries to accomplish.
         * Assume the queue looks like this: `ABCDDEEEEEEEEEE`.
         * This method first starts with a queue of the final size, where each slot is empty (null).
         * It takes the podcast with most episodes (`E`) and places the episodes spread out in the queue: `EE_E_EE_E_EE_EE`.
         * The podcast with the second-most number of episodes (`D`) is then
         * placed spread-out in the *available* slots: `EE_EDEE_EDEE_EE`.
         * This continues, until we end up with: `EEBEDEECEDEEAEE`.
         * Note that episodes aren't strictly ordered in terms of pubdate, but episodes of each feed are.
         *
         * @param episodes A (modifiable) list of FeedItem elements to be reordered.
         * @param ascending `true` to use ascending pubdate in the reordering;
         * `false` for descending.
         */
        private fun smartShuffle(episodes: MutableList<Episode?>, ascending: Boolean) {
            // Divide FeedItems into lists by feed
            val map: MutableMap<Long, MutableList<Episode>> = mutableMapOf()
            for (item in episodes) {
                if (item == null) continue
                val id = item.feedId
                if (id != null) {
                    if (!map.containsKey(id)) map[id] = mutableListOf()
                    map[id]!!.add(item)
                }
            }

            // Sort each individual list by PubDate (ascending/descending)
            val itemComparator: java.util.Comparator<Episode> =
                if (ascending) Comparator { f1: Episode, f2: Episode -> f1.pubDate.compareTo(f2.pubDate) }
                else Comparator { f1: Episode, f2: Episode -> f2.pubDate.compareTo(f1.pubDate) }

            val feeds: MutableList<List<Episode>> = mutableListOf()
            for ((_, value) in map) {
                value.sortWith(itemComparator)
                feeds.add(value)
            }

            val emptySlots = mutableListOf<Int>()
            for (i in episodes.indices) {
                episodes[i] = null
                emptySlots.add(i)
            }

            // Starting with the largest feed, place items spread out through the empty slots in the queue
            feeds.sortWith { f1: List<Episode>, f2: List<Episode> -> f2.size.compareTo(f1.size) }
            for (feedItems in feeds) {
                val spread = emptySlots.size.toDouble() / (feedItems.size + 1)
                val emptySlotIterator = emptySlots.iterator()
                var skipped = 0
                var placed = 0
                while (emptySlotIterator.hasNext()) {
                    val nextEmptySlot = emptySlotIterator.next()
                    skipped++
                    if (skipped >= spread * (placed + 1)) {
                        if (episodes[nextEmptySlot] != null) throw RuntimeException("Slot to be placed in not empty")
                        episodes[nextEmptySlot] = feedItems[placed]
                        emptySlotIterator.remove()
                        placed++
                        if (placed == feedItems.size) break
                    }
                }
            }
        }

        /**
         * Interface for passing around list permutor method. This is used for cases where a simple comparator
         * won't work (e.g. Random, Smart Shuffle, etc)
         * @param <E> the type of elements in the list
        </E> */
        interface Permutor<E> {
            /**
             * Reorders the specified list.
             * @param episodes A (modifiable) list of elements to be reordered
             */
            fun reorder(episodes: MutableList<E>?)
        }
    }
}