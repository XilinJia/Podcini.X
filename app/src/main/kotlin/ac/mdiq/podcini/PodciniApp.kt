package ac.mdiq.podcini

import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.PreferenceUpgrader
import ac.mdiq.podcini.receiver.SPAReceiver
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.ui.activity.SplashActivity
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.config.ApplicationCallbacks
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.util.error.CrashReportWriter
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PodciniApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        ClientConfig.applicationCallbacks = ApplicationCallbacksImpl()
        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())
        if (BuildConfig.DEBUG) {
            val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder().detectAll().penaltyLog().penaltyDropBox()
            StrictMode.setVmPolicy(builder.build())
        }

        podciniApp = this
        ClientConfigurator.initialize(this@PodciniApp)

        applicationScope.launch(Dispatchers.IO) { PreferenceUpgrader.checkUpgrades(this@PodciniApp) }

        gearbox.init()
        sendSPAppsQueryFeedsIntent(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    /**
     * Sends an ACTION_SP_APPS_QUERY_FEEDS intent to all Podcini Single Purpose apps.
     * The receiving single purpose apps will then send their feeds back to Podcini via an
     * ACTION_SP_APPS_QUERY_FEEDS_RESPONSE intent.
     * This intent will only be sent once.
     * @return True if an intent was sent, false otherwise (for example if the intent has already been
     * sent before.
     */
    @Synchronized
    fun sendSPAppsQueryFeedsIntent(context: Context): Boolean {
        val appContext = context.applicationContext
        if (appContext == null) {
            Loge("App", "Unable to get application context")
            return false
        }
        if (!getPref(PREF_HAS_QUERIED_SP_APPS, false)) {
            appContext.sendBroadcast(Intent(SPAReceiver.ACTION_SP_APPS_QUERY_FEEDS))
            Logd("App", "Sending SP_APPS_QUERY_FEEDS intent")
            putPref(PREF_HAS_QUERIED_SP_APPS, true)
            return true
        } else return false
    }

    class ApplicationCallbacksImpl : ApplicationCallbacks {
        override fun getApplicationInstance(): Application {
            return getApp()
        }
    }

    companion object {
        private const val PREF_HAS_QUERIED_SP_APPS = "prefSPAUtil.hasQueriedSPApps"
        private lateinit var podciniApp: PodciniApp

        fun getApp(): PodciniApp = podciniApp

        fun getAppContext(): Context = podciniApp.applicationContext

        
        fun forceRestart() {
            val intent = Intent(getApp(), SplashActivity::class.java)
            val cn: ComponentName? = intent.component
            val mainIntent: Intent = Intent.makeRestartActivityTask(cn)
            realm.close()
            getApp().startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
