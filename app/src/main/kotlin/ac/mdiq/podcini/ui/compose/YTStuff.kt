package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import androidx.compose.runtime.Composable
import java.net.URL

@Composable
fun ConfirmAddYTEpisode(sharedUrls: List<String>, showDialog: Boolean, onDismissRequest: () -> Unit) {}

fun isYTUrl(url: URL): Boolean = false

fun isYTServiceUrl(url: URL): Boolean = false

fun clearYTData() {}
fun buildYTWebviewData(episode_: Episode,  shownotesCleaner: ShownotesCleaner): Pair<Episode, String> {
    return Pair(episode_, "")
}