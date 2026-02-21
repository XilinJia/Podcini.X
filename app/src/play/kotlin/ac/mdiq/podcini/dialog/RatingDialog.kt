package ac.mdiq.podcini.ui.dialog

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.storage.database.syncPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.tasks.Task
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

object RatingDialog {
    private val TAG: String = RatingDialog::class.simpleName ?: "Anonymous"
    private const val AFTER_DAYS = 14

    private var mContext: WeakReference<Context>? = null

    private const val PREFS_NAME = "RatingPrefs"
    private const val KEY_RATED = "KEY_WAS_RATED"
    private const val KEY_FIRST_START_DATE = "KEY_FIRST_HIT_DATE"
    private const val KEY_NUMBER_OF_REVIEWS = "NUMBER_OF_REVIEW_ATTEMPTS"

    fun init(context: Context) {
        mContext = WeakReference(context)

        val firstDate: Long = syncPrefs.KEY_FIRST_START_DATE
        if (firstDate == 0L) resetStartDate()
    }

    fun check() {
        if (shouldShow()) try { showInAppReview() } catch (e: Exception) { Logs(TAG, e) }
    }

    private fun showInAppReview() {
        val context = mContext!!.get() ?: return
        val manager: ReviewManager = ReviewManagerFactory.create(context)
        val request: Task<ReviewInfo> = manager.requestReviewFlow()

        request.addOnCompleteListener { task: Task<ReviewInfo?> ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                val flow: Task<Void?> = manager.launchReviewFlow(context as Activity, reviewInfo)
                flow.addOnCompleteListener { task1: Task<Void?>? ->
                    val previousAttempts: Int = syncPrefs.KEY_NUMBER_OF_REVIEWS
                    if (previousAttempts >= 3) saveRated()
                    else {
                        resetStartDate()
                        upsertBlk(syncPrefs) { it.KEY_NUMBER_OF_REVIEWS = previousAttempts + 1 }
                    }
                    Logd("ReviewDialog", "Successfully finished in-app review")
                }.addOnFailureListener { error: Exception? -> Logd("ReviewDialog", "failed in reviewing process") }
            }
        }.addOnFailureListener { error: Exception? -> Logd("ReviewDialog", "failed to get in-app review request") }
    }

    private fun rated(): Boolean {
        return syncPrefs.KEY_RATED
    }

    fun saveRated() {
        upsertBlk(syncPrefs) { it.KEY_RATED = true }
    }

    private fun resetStartDate() {
        upsertBlk(syncPrefs) { it.KEY_FIRST_START_DATE = System.currentTimeMillis() }
    }

    private fun shouldShow(): Boolean {
        if (rated() || BuildConfig.DEBUG) return false

        val now = System.currentTimeMillis()
        val firstDate: Long = syncPrefs.KEY_FIRST_START_DATE.takeIf { it != 0L } ?: now
        val diff = now - firstDate
        val diffDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        return diffDays >= AFTER_DAYS
    }
}
