package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.R
import ac.mdiq.podcini.utils.Loge

enum class VolumeAdaptionSetting(val value: Int,  val adaptionFactor: Float, val resId: Int) {
    OFF(0, 1.0f, R.string.feed_volume_reduction_off),
    LIGHT_REDUCTION(1, 0.5f, R.string.feed_volume_reduction_light),
    HEAVY_REDUCTION(2, 0.2f, R.string.feed_volume_reduction_heavy),
    LIGHT_BOOST(3, 1.6f, R.string.feed_volume_boost_light),
    MEDIUM_BOOST(4, 2.4f, R.string.feed_volume_boost_medium),
    HEAVY_BOOST(5, 3.6f, R.string.feed_volume_boost_heavy);

    companion object {
        fun fromInteger(value: Int): VolumeAdaptionSetting {
            val vs = VolumeAdaptionSetting.entries.firstOrNull { it.value == value }
            if (vs == null) {
                Loge("VolumeAdaptionSetting", "Cannot map value to VolumeAdaptionSetting: $value resort to OFF")
                return OFF
            }
            return vs
        }
    }
}