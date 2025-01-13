package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.storage.model.Feed
import android.content.Context
import androidx.work.WorkerParameters

class FeedUpdateWorker(context: Context, params: WorkerParameters) : FeedUpdateWorkerBase(context, params) {
    override fun refreshYTFeed(feed: Feed, fullUpdate: Boolean) {}
}
