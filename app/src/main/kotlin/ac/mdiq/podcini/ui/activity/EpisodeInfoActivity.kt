package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.ThemePreference
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.compose.AppNavigator
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.LargePoster
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.Navigate
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.compose.commonMessage
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.toastMassege
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "EpisodeInfoActivity"

class EpisodeInfoActivity : ComponentActivity() {
    private val currentEpisodeId = MutableStateFlow<Long?>(null)
    private var lastTheme = AppPreferences.theme

    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
        super.onCreate(savedInstanceState)
        Logd(TAG, "in onCreate")

        lastTheme = AppPreferences.theme

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            PodciniTheme() {
                val navController = rememberNavController()
                val navigator = remember { AppNavigator(navController) { route -> Logd(TAG, "Navigated to: $route") } }
                val episodeId by currentEpisodeId.collectAsStateWithLifecycle()
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                    if (toastMassege.isNotBlank()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                    if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                    if (commonMessage != null) LargePoster(commonMessage!!)
                    CompositionLocalProvider(LocalNavController provides navigator) {
                        episodeId?.let { Navigate(navController, "${Screens.EpisodeInfo.name}?episodeId=$episodeId") } }
                }
            }
        }

        currentEpisodeId.value = intent.getLongExtra("episode_info_id", -1L)

        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.95).toInt(),
            (resources.displayMetrics.heightPixels * 0.90).toInt()
        )
    }

//    private fun notifyWidget() {
//        lifecycleScope.launch {
//            val gidString = intent.getStringExtra("WidgetGlanceId") ?: return@launch
//            val manager = GlanceAppWidgetManager(this@EpisodeInfoActivity)
//            val glanceId = manager.getGlanceIds(PodciniWidget::class.java).find { it.toString() == gidString } ?: return@launch
//            updateAppWidgetState(this@EpisodeInfoActivity, glanceId) { prefs -> prefs[IS_LOADING_KEY] = false }
//            PodciniWidget().update(this@EpisodeInfoActivity, glanceId)
//        }
//    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logd(TAG, "onNewIntent")
        setIntent(intent)
        currentEpisodeId.value = intent.getLongExtra("episode_info_id", -1L)
    }

//    override fun onResume() {
//        super.onResume()
//        if (lastTheme != AppPreferences.theme) {
//            finish()
//            startActivity(Intent(this, MainActivity::class.java))
//        }
//    }

    override fun onDestroy() {
        super.onDestroy()
        Logd(TAG, "onDestroy called")
    }
}