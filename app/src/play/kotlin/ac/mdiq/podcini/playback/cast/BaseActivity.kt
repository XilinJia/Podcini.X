package ac.mdiq.podcini.playback.cast

import ac.mdiq.podcini.ui.compose.isLightTheme
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Activity that allows for showing the MediaRouter button whenever there's a cast device in the network.
 */
abstract class BaseActivity : AppCompatActivity() {
    private var canCast by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        canCast = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
    }

    @Composable
    fun CastIconButton() {
        val isLight = isLightTheme()
        if (canCast) AndroidView(modifier = Modifier.size(24.dp),
            factory = { ctx ->
                val themedContext = ContextThemeWrapper(ctx, if (!isLight) androidx.appcompat.R.style.Theme_AppCompat
                else androidx.appcompat.R.style.Theme_AppCompat_Light)
                MediaRouteButton(themedContext).apply { CastButtonFactory.setUpMediaRouteButton(ctx, this) }
            }
        )
    }
}
