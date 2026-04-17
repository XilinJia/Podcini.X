package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext

object CastMediaPlayer {
        private const val TAG = "CastMediaPlayer"

        fun buildCastPlayer(exoPlayer: Player): Player {
            return CastPlayer.Builder(getAppContext()).setLocalPlayer(exoPlayer).build()
        }
}
