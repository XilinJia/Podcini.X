package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class SleepPrefs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var LastValue: Long = 0L

    var Vibrate: Boolean = false

    var ShakeToReset: Boolean = false

    var AutoEnable: Boolean = false

    var AutoEnableFrom: Int = 0

    var AutoEnableTo: Int = 0
}