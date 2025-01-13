package ac.mdiq.podcini.util.config

import android.app.Application

/**
 * Callbacks related to the application in general
 */
interface ApplicationCallbacks {
    /**
     * Returns a non-null instance of the application class
     */
    fun getApplicationInstance(): Application?
}
