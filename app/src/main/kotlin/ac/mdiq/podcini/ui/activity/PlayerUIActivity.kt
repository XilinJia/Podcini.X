package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.screens.AudioPlayerUIScreen
import ac.mdiq.podcini.ui.compose.AppNavigator
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.LargePoster
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.toastMassege
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController

private const val TAG = "PlayerUIActivity"
class PlayerUIActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Logd(TAG, "in onCreate")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            PodciniTheme {
                val navController = rememberNavController()
                val navigator = remember { AppNavigator(navController) { route -> Logd(TAG, "Navigated to: $route") } }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                    if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                    if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                    if (commonMessage != null) LargePoster(commonMessage!!)
//                    CompositionLocalProvider(LocalNavController provides navigator) {
                        AudioPlayerUIScreen(Modifier, navigator)
//                        Navigate(navController, "${Screens.EpisodeInfo.name}?episodeId=$episodeId")
//                    }
                }
            }
        }

        val window = window
        val params = window.attributes
        params.width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.BOTTOM
        window.attributes = params
    }

    override fun onDestroy() {
        super.onDestroy()
        Logd(TAG, "onDestroy called")
    }
}