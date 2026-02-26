package ac.mdiq.podcini

import ac.mdiq.podcini.config.ApplicationCallbacks
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.activity.MainActivity
import ac.mdiq.podcini.utils.error.CrashReportWriter
import ac.mdiq.podcini.utils.startTiming
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import android.util.Log
import androidx.media3.session.MediaController
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PodciniApp : Application() {
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

        ClientConfigurator.initialize()
        if (nmJob == null) nmJob = CoroutineScope(Dispatchers.IO).launch { networkMonitor.networkFlow.collect {} }

        DynamicColors.applyToActivitiesIfAvailable(this)
    }

    override fun onTerminate() {
        aCtrlFuture?.let { future ->
            aController = null
            MediaController.releaseFuture(future)
            aCtrlFuture = null
        }
        nmJob?.cancel()
        ClientConfigurator.destroy()
        super.onTerminate()
    }

    class ApplicationCallbacksImpl : ApplicationCallbacks {
        override fun getApplicationInstance(): Application {
            return getApp()
        }
    }

    companion object {
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
