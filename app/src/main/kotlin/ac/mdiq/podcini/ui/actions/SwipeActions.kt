package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class SwipeActions(private val tag: String) {
    var right by mutableStateOf<EpisodeAction>(NoAction())
    var left by mutableStateOf<EpisodeAction>(NoAction())

    fun initActions(actions_: String) {
        val actions = actions_.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (actions.size == 2) {
            this.right = episodeActions.firstOrNull { a: EpisodeAction -> a.id == actions[0] } ?: episodeActions[0]
            this.left = episodeActions.firstOrNull { a: EpisodeAction -> a.id == actions[1] } ?: episodeActions[0]
        }
    }

    init {
        initActions((appAttribs.swipeActionsMap[tag] ?: ""))
    }

    @Composable
    fun ActionOptionsDialog() {
        left.ActionOptions()
        right.ActionOptions()
    }

    @Composable
    fun SwipeActionsSettingDialog(onDismissRequest: () -> Unit) {
        val context by rememberUpdatedState(LocalContext.current)
        val textColor = MaterialTheme.colorScheme.onSurface

        val leftAction = remember { mutableStateOf(left) }
        val rightAction = remember { mutableStateOf(right) }
        var keys by remember { mutableStateOf(episodeActions) }

        var direction by remember { mutableIntStateOf(0) }
        var showPickerDialog by remember { mutableStateOf(false) }
        if (showPickerDialog) {
            CommonPopupCard(onDismissRequest = { showPickerDialog = false }) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(16.dp)) {
                    items(keys) { key ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
                            .clickable {
                                when (direction) {
                                    -1 -> leftAction.value = key
                                    1 -> rightAction.value = key
                                    else -> {}
                                }
                                showPickerDialog = false
                            }) {
                            Icon(imageVector = ImageVector.vectorResource(key.iconRes), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp))
                            Text(key.title, color = textColor, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        if (!showPickerDialog) CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
            Logd("SwipeActions", "SwipeActions tag: $tag")
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val forFragment = remember(tag) {
                    if (tag != Screens.Queues.name) keys = keys.filter { a: EpisodeAction -> a !is RemoveFromQueue }
                    when (tag) {
                        Screens.Facets.name -> context.getString(R.string.facets)
                        Screens.OnlineFeed.name -> context.getString(R.string.online_episodes_label)
                        Screens.Search.name -> context.getString(R.string.search_label)
                        Screens.FeedDetails.name -> {
                            keys = keys.filter { a: EpisodeAction -> a !is RemoveFromHistory }
                            context.getString(R.string.subscription)
                        }
                        Screens.Queues.name -> {
                            keys = keys.filter { a: EpisodeAction -> (a !is AddToActiveQueue && a !is RemoveFromHistory) }
                            context.getString(R.string.queue_label)
                        }
                        else -> { tag }
                    } }
                Text(stringResource(R.string.swipeactions_label) + " - " + forFragment)
                Text(stringResource(R.string.swipe_left))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                    Spacer(Modifier.weight(0.1f))
                    Icon(imageVector = ImageVector.vectorResource(leftAction.value.iconRes), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                        .clickable(onClick = {
                            direction = -1
                            showPickerDialog = true
                        }))
                    Spacer(Modifier.weight(0.1f))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                    Spacer(Modifier.weight(0.5f))
                }
                Text(stringResource(R.string.swipe_right))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                    Spacer(Modifier.weight(0.5f))
                    Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier.width(50.dp).height(35.dp))
                    Spacer(Modifier.weight(0.1f))
                    Icon(imageVector = ImageVector.vectorResource(rightAction.value.iconRes), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp)
                        .clickable(onClick = {
                            direction = 1
                            showPickerDialog = true
                        }))
                    Spacer(Modifier.weight(0.1f))
                }
                Button(onClick = {
                    initActions("${rightAction.value.id},${leftAction.value.id}")
                    runOnIOScope { upsert(appAttribs) { it.swipeActionsMap[tag] = "${rightAction.value.id},${leftAction.value.id}" } }
                    onDismissRequest()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}
