package ac.mdiq.podcini

import ac.mdiq.podcini.config.ApplicationCallbacks
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.utils.error.CrashReportWriter
import ac.mdiq.podcini.utils.startTiming
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import android.util.Log
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PodciniApp : Application() {
//    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val networkMonitor: NetworkUtils.NetworkMonitor by lazy { NetworkUtils.NetworkMonitor() }
    var nmJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        podciniApp = this
        startTiming()

        Log.d("PodciniApp", "PodciniApp onCreate")

        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        ClientConfig.applicationCallbacks = ApplicationCallbacksImpl()
        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())
        if (BuildConfig.DEBUG) {
            val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder().detectAll().penaltyLog().penaltyDropBox()
            StrictMode.setVmPolicy(builder.build())
        }
        if (nmJob == null) nmJob = CoroutineScope(Dispatchers.IO).launch { networkMonitor.networkFlow.collect {} }

        ClientConfigurator.initialize()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        nmJob?.cancel()
        ClientConfigurator.destroy()
    }

    class ApplicationCallbacksImpl : ApplicationCallbacks {
        override fun getApplicationInstance(): Application {
            return getApp()
        }
    }

    companion object {
//        private const val PREF_HAS_QUERIED_SP_APPS = "prefSPAUtil.hasQueriedSPApps"
        private lateinit var podciniApp: PodciniApp

        fun getApp(): PodciniApp = podciniApp

        fun getAppContext(): Context = podciniApp.applicationContext

        fun forceRestart() {
            val intent = Intent(getApp(), MainActivity::class.java)
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            realm.close()
            getApp().startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
