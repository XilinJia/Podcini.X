package ac.mdiq.podcini

import ac.mdiq.podcini.activity.MainActivity
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.utils.CrashReportWriter
import ac.mdiq.podcini.utils.startTiming
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import android.util.Log

class PodciniApp : Application() {

    override fun onCreate() {
        super.onCreate()
        podciniApp = this
        startTiming()
        Log.d("PodciniApp", "PodciniApp onCreate")

        ClientConfig.USER_AGENT = "Podcini/" + BuildConfig.VERSION_NAME
        Thread.setDefaultUncaughtExceptionHandler(CrashReportWriter())
        if (BuildConfig.DEBUG) {
            val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder().detectAll().penaltyLog().penaltyDropBox()
            StrictMode.setVmPolicy(builder.build())
        }
        ClientConfig.initialize()
    }

    override fun onTerminate() {
        ClientConfig.destroy()
        super.onTerminate()
    }

    companion object {
        private lateinit var podciniApp: PodciniApp

        fun getAppContext(): Context = podciniApp.applicationContext

        fun forceRestart() {
            val intent = Intent(podciniApp, MainActivity::class.java)
            val mainIntent = Intent.makeRestartActivityTask(intent.component)
            ClientConfig.destroy()
            realm.close()
            podciniApp.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
