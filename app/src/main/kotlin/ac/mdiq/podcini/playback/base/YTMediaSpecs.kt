package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.storage.model.Episode
import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.MediaSource

class YTMediaSpecs(val media: Episode) {
    companion object {
        fun setYTMediaSource(metadata: MediaMetadata, media: Episode, context: Context): MediaSource? = null

        fun setCastYTMediaSource(media: Episode): Boolean = false
    }
}