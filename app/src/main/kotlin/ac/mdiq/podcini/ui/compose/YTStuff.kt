package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.screens.AudioPlayerVM
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

fun buildCleanedNotes(curItem_: Episode, shownotesCleaner: ShownotesCleaner?): Pair<Episode, String?> {
    var curItem = curItem_
    var cleanedNotes: String? = null
    cleanedNotes = shownotesCleaner?.processShownotes(curItem.description ?: "", curItem.duration)
    return Pair(curItem, cleanedNotes)
}

@Composable
fun PlayerDetailedYTPanel(vm: AudioPlayerVM) {}

@Composable
fun VistaSearchText() {}