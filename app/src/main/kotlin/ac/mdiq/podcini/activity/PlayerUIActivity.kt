package ac.mdiq.podcini.activity

import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.ui.compose.AppThemes
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.compose.appTheme
import ac.mdiq.podcini.ui.compose.textColor
import ac.mdiq.podcini.ui.screens.AVPlayerVM
import ac.mdiq.podcini.ui.screens.ControlUI
import ac.mdiq.podcini.ui.screens.ProgressBar
import ac.mdiq.podcini.utils.Logd
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val TAG = "PlayerUIActivity"
class PlayerUIActivity : ComponentActivity() {
    private var lastTheme = appTheme

    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
        super.onCreate(savedInstanceState)
        Logd(TAG, "in onCreate")

        lastTheme = appTheme

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            PodciniTheme(AppThemes.BLACK) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
                    
                    val vm: AVPlayerVM = viewModel()
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp).border(1.dp, MaterialTheme.colorScheme.tertiary).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))) {
                        Column {
                            Text(curEpisode?.title ?: "No title", maxLines = 1, color = textColor, style = MaterialTheme.typography.bodyMedium)
                            ProgressBar(vm)
                            ControlUI(vm)
                        }
                    }
                }
            }
        }

        val params = window.attributes
        params.width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.BOTTOM
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        window.attributes = params
    }

    override fun onDestroy() {
        super.onDestroy()
        Logd(TAG, "onDestroy called")
    }
}