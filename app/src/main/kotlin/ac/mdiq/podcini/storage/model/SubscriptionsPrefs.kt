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
}