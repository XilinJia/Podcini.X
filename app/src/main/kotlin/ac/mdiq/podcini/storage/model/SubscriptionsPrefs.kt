package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class SubscriptionsPrefs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var prefFeedGridLayout: Boolean = false

    var showArchived: Boolean = false

    var feedsFilter: String = ""

    var langsSel: RealmSet<String> = realmSetOf()

    var tagsSel: RealmSet<String> = realmSetOf()

    var queueSelIds: RealmSet<Long> = realmSetOf()

    var sortIndex: Int = 0

    var propertyAscending: Boolean = true
    var propertySortIndex: Int = 0

    var dateAscending: Boolean = false
    var dateSortIndex: Int = 0

    var timeAscending: Boolean = false
    var timeSortIndex: Int = 0

    var countAscending: Boolean = false
    var downlaodedSortIndex: Int = 0
    var commentedSortIndex: Int = 0

    var playStateCodeSet: RealmSet<String> = realmSetOf()

    var ratingCodeSet: RealmSet<String> = realmSetOf()

    var sortProperty: String = ""

    var sortDirCode: Int = 0

    var feedsSorted: Int = 0

    var feedsFiltered: Int = 0

    var positionIndex: Int = 0

    var positionOffset: Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SubscriptionsPrefs

        if (id != other.id) return false
        if (prefFeedGridLayout != other.prefFeedGridLayout) return false
        if (showArchived != other.showArchived) return false
        if (sortIndex != other.sortIndex) return false
        if (propertyAscending != other.propertyAscending) return false
        if (propertySortIndex != other.propertySortIndex) return false
        if (dateAscending != other.dateAscending) return false
        if (dateSortIndex != other.dateSortIndex) return false
        if (timeAscending != other.timeAscending) return false
        if (timeSortIndex != other.timeSortIndex) return false
        if (countAscending != other.countAscending) return false
        if (downlaodedSortIndex != other.downlaodedSortIndex) return false
        if (commentedSortIndex != other.commentedSortIndex) return false
        if (sortDirCode != other.sortDirCode) return false
        if (feedsSorted != other.feedsSorted) return false
        if (feedsFiltered != other.feedsFiltered) return false
        if (positionIndex != other.positionIndex) return false
        if (positionOffset != other.positionOffset) return false
        if (feedsFilter != other.feedsFilter) return false
        if (langsSel.size != other.langsSel.size) return false
        if (tagsSel.size != other.tagsSel.size) return false
        if (queueSelIds.size != other.queueSelIds.size) return false
        if (playStateCodeSet.size != other.playStateCodeSet.size) return false
        if (ratingCodeSet.size != other.ratingCodeSet.size) return false
        if (sortProperty != other.sortProperty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + prefFeedGridLayout.hashCode()
        result = 31 * result + showArchived.hashCode()
        result = 31 * result + sortIndex
        result = 31 * result + propertyAscending.hashCode()
        result = 31 * result + propertySortIndex
        result = 31 * result + dateAscending.hashCode()
        result = 31 * result + dateSortIndex
        result = 31 * result + timeAscending.hashCode()
        result = 31 * result + timeSortIndex
        result = 31 * result + countAscending.hashCode()
        result = 31 * result + downlaodedSortIndex
        result = 31 * result + commentedSortIndex
        result = 31 * result + sortDirCode
        result = 31 * result + feedsSorted
        result = 31 * result + feedsFiltered
        result = 31 * result + positionIndex
        result = 31 * result + positionOffset
        result = 31 * result + feedsFilter.hashCode()
        result = 31 * result + langsSel.size
        result = 31 * result + tagsSel.size
        result = 31 * result + queueSelIds.size
        result = 31 * result + playStateCodeSet.size
        result = 31 * result + ratingCodeSet.size
        result = 31 * result + sortProperty.hashCode()
        return result
    }
}