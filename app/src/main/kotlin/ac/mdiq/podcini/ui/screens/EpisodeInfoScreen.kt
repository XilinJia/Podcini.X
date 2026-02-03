package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.ui.compose.EpisodeScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.xilinjia.krdb.ext.query

private const val TAG: String = "EpisodeInfoScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeInfoScreen(episodeId: Long = 0L) {
    val episode = remember { realm.query<Episode>("id == $0", episodeId).first().find() }
    if (episode != null) EpisodeScreen(episode, allowOpenFeed = true)
}
