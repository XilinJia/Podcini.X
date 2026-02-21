package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.config.settings.AppPreferences
import ac.mdiq.podcini.ui.compose.AppNavigator
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.compose.AppThemes
import ac.mdiq.podcini.ui.compose.appTheme
import ac.mdiq.podcini.ui.screens.AudioPlayerUIScreen
import ac.mdiq.podcini.utils.Logd
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController

private const val TAG = "PlayerUIActivity"
class PlayerUIActivity : ComponentActivity() {
    private var lastTheme = appTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
                val navController = rememberNavController()
                val navigator = remember { AppNavigator(navController) { route -> Logd(TAG, "Navigated to: $route") } }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
                    AudioPlayerUIScreen(Modifier, navigator)
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