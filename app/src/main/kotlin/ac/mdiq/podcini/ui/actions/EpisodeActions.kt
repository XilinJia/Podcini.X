package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.storage.database.addToAssQueue
import ac.mdiq.podcini.storage.database.addToQueue
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.smartRemoveFromAllQueues
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.ui.compose.AddTimerDialog
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommentEditingDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EraseEpisodesDialog
import ac.mdiq.podcini.ui.compose.FutureStateDialog
import ac.mdiq.podcini.ui.compose.IgnoreEpisodesDialog
import ac.mdiq.podcini.ui.compose.LocalNavController
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.compose.PutToQueueDialog
import ac.mdiq.podcini.ui.compose.Screens
import ac.mdiq.podcini.ui.compose.ShelveDialog
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.setSearchTerms
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

abstract class EpisodeAction {
    abstract val id: String
    abstract val title: String
    open fun enabled(): Boolean = true
    abstract val iconRes: Int
    abstract val color: Color
    @Composable
    open fun ActionOptions() {}
    open fun performAction(e: Episode) {
        Logd("SwipeAction", "performAction: $title ${e.title}")
        onEpisode = e
    }
    companion object {
        internal var onEpisode by mutableStateOf<Episode?>(null)
    }
}

val episodeActions: List<EpisodeAction> = listOf(
    NoAction(),
    Combo(),
    SetPlaybackState(),

    AddToAssociatedQueue(),
    AddToActiveQueue(),
    AddToQueue(),
    RemoveFromQueue(),

    SetRating(),
    AddComment(),
    AddTag(),

    SearchSelected(),

    Download(),
    Shelve(),

    Delete(),
    RemoveFromHistory(),
    Erase(),

    Alarm())

class NoAction : EpisodeAction() {
    override val id: String
        get() = "NO_ACTION"
    override val title: String
        get() = getAppContext().getString(R.string.no_action_label)

    override val iconRes:  Int = R.drawable.ic_questionmark
    override val color: Color = Color.Red

    override fun enabled(): Boolean = false
}

class Combo : EpisodeAction() {
    override val id: String
        get() = "COMBO"
    override val title: String
        get() = getAppContext().getString(R.string.combo_action)
    override val iconRes:  Int = R.drawable.baseline_category_24
    override val color: Color = Color.Cyan

    var showDialog by mutableStateOf(false)
    private var useAction by mutableStateOf<EpisodeAction?>(null)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showDialog = true
    }
    @Composable
    override fun ActionOptions() {
        useAction?.ActionOptions()
        if (showDialog && onEpisode != null) CommonPopupCard(onDismissRequest = { showDialog = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (action in episodeActions) {
                    if (action is NoAction || action is Combo) continue
                    if (!action.enabled()) continue
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                        .clickable {
                            useAction = action
                            action.performAction(onEpisode!!)
                            showDialog = false
                        }) {
                        Icon(imageVector = ImageVector.vectorResource(id = action.iconRes),  tint = action.color, contentDescription = action.title)
                        Text(action.title, Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}


class SetPlaybackState : EpisodeAction() {
    override val id: String
        get() = "SET_PLAY_STATE"
    private var showPlayStateDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.set_play_state_label)

    override val iconRes:  Int = R.drawable.ic_mark_played
    override val color: Color = Color(0xFF66FFAA)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showPlayStateDialog = true
    }
    @Composable
    override fun ActionOptions() {
        var showIgnoreDialog by remember { mutableStateOf(false) }
        if (showIgnoreDialog) IgnoreEpisodesDialog(listOf(onEpisode!!), onDismissRequest = { showIgnoreDialog = false })
        var futureState by remember { mutableStateOf(EpisodeState.UNSPECIFIED) }
        if (futureState in listOf(EpisodeState.AGAIN, EpisodeState.LATER)) FutureStateDialog(listOf(onEpisode!!), futureState, onDismissRequest = { futureState = EpisodeState.UNSPECIFIED })
        if (showPlayStateDialog && onEpisode != null) PlayStateDialog(listOf(onEpisode!!), onDismissRequest = { showPlayStateDialog = false }, futureCB = { futureState = it }, ignoreCB = { showIgnoreDialog = true })
    }
}

class AddToAssociatedQueue : EpisodeAction() {
    override val id: String
        get() = "ADD_TO_ASSOCIATED"
    override val title: String
        get() = getAppContext().getString(R.string.add_to_associated_queue)

    override val iconRes:  Int = R.drawable.ic_playlist_play
    override val color: Color = Color(0xFF5599FF)

    override fun enabled(): Boolean {
        val q = onEpisode?.feed?.queue ?: return false
        return !q.contains(onEpisode!!)
    }

    override fun performAction(e: Episode) {
        super.performAction(e)
        if (e.feed?.queue != null) runOnIOScope { addToAssQueue(listOf(e)) }
    }
}

class AddToActiveQueue : EpisodeAction() {
    override val id: String
        get() = "ADD_TO_ACT_QUEUE"
    override val title: String
        get() = getAppContext().getString(R.string.add_to_active_queue)

    override val iconRes:  Int = R.drawable.ic_playlist_play
    override val color: Color = Color(0xFF55BBFF)

    override fun enabled(): Boolean {
        if (onEpisode?.feed?.queue != null) return false
        return onEpisode != null && !actQueue.contains(onEpisode!!)
    }

    override fun performAction(e: Episode) {
        super.performAction(e)
        runOnIOScope { addToQueue(listOf(e), actQueue) }
    }
}

class AddToQueue : EpisodeAction() {
    override val id: String
        get() = "ADD_TO_QUEUE"
    private var showPutToQueueDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.add_to_queue)

    override val iconRes:  Int = R.drawable.ic_playlist_play
    override val color: Color = Color(0xFF55DDFF)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showPutToQueueDialog = true
        Logd("AddToQueue", "performAction $showPutToQueueDialog")
    }
    @Composable
    override fun ActionOptions() {
        Logd("AddToQueue", "ActionOptions $showPutToQueueDialog")
        if (showPutToQueueDialog && onEpisode != null) PutToQueueDialog(listOf(onEpisode!!)) { showPutToQueueDialog = false }
    }
}

class RemoveFromQueue : EpisodeAction() {
    override val id: String
        get() = "REMOVE_FROM_QUEUE"
    override val title: String
        get() = getAppContext().getString(R.string.remove_from_all_queues)

    override val iconRes:  Int = R.drawable.ic_playlist_remove
    override val color: Color = Color(0xFFDDAAFF)

    override fun enabled(): Boolean = onEpisode != null && actQueue.contains(onEpisode!!)

    override fun performAction(e: Episode) {
        super.performAction(e)
        runOnIOScope { smartRemoveFromAllQueues(e) }
    }
}

class SetRating : EpisodeAction() {
    override val id: String
        get() = "RATING"
    private var showChooseRatingDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.set_rating_label)

    override val iconRes:  Int = R.drawable.ic_star
    override val color: Color = Color(0xFFFFAA88)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showChooseRatingDialog = true
    }
    @Composable
    override fun ActionOptions() {
        if (showChooseRatingDialog && onEpisode != null) ChooseRatingDialog(listOf(onEpisode!!)) { showChooseRatingDialog = false }
    }
}

class AddComment : EpisodeAction() {
    override val id: String
        get() = "COMMENT"
    private var showEditComment by mutableStateOf(false)
    private var editCommentText by mutableStateOf(TextFieldValue(""))
    override val title: String
        get() = getAppContext().getString(R.string.add_comments)

    override val iconRes:  Int = R.drawable.baseline_comment_24
    override val color: Color = Color(0xFFFFCC00)

    override fun performAction(e: Episode) {
        //            val e_ = (if (isCurMedia(e)) curEpisode else realm.query(Episode::class).query("id == ${e.id}").first().find()) ?: return
        onEpisode = e
        editCommentText = TextFieldValue(onEpisode?.compileCommentText() ?: "")
        showEditComment = true
    }
    @Composable
    override fun ActionOptions() {
        if (showEditComment) {
            CommentEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = { showEditComment = false },
                onSave = { if (onEpisode != null) runOnIOScope { onEpisode = upsert(onEpisode!!) { it.addComment(editCommentText.text, false) } } })
        }
    }
}

class AddTag : EpisodeAction() {
    override val id: String
        get() = "TAG"
    private var showTagDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.tags_label)

    override val iconRes:  Int = R.drawable.baseline_label_24
    override val color: Color = Color(0xFFFFCC33)

    override fun performAction(e: Episode) {
        onEpisode = e
        showTagDialog = true
    }
    @Composable
    override fun ActionOptions() {
        if (showTagDialog) {
            TagSettingDialog(TagType.Episode, onEpisode!!.tags, onDismiss = { showTagDialog = false }) { tags ->
                runOnIOScope {
                    upsert(onEpisode!!) {
                        it.tags.clear()
                        it.tags.addAll(tags)
                    }
                }
            }
        }
    }
}

class SearchSelected : EpisodeAction() {
    override val id: String
        get() = "SEARCH_SELECTED"
    private var showSearchDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.search_selected)

    override val iconRes:  Int = R.drawable.ic_search
    override val color: Color = Color(0xFF55AA55)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showSearchDialog = true
    }
    @Composable
    override fun ActionOptions() {
        val navController = LocalNavController.current
        if (showSearchDialog && onEpisode?.title != null) {
            var textFieldValue by remember { mutableStateOf(TextFieldValue(onEpisode!!.title!!)) }
            val selectedText by remember(textFieldValue.selection) { mutableStateOf(
                if (textFieldValue.selection.collapsed) ""
                else {
                    val start = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
                    val end = textFieldValue.selection.end.coerceIn(start, textFieldValue.text.length)
                    textFieldValue.text.substring(startIndex = start, endIndex = end)
                }) }
            AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showSearchDialog = false },
                title = { Text(stringResource(R.string.select_text_to_search), style = CustomTextStyles.titleCustom) },
                text = { TextField(value = textFieldValue, onValueChange = { textFieldValue = it }, readOnly = true, textStyle = TextStyle(fontSize = 18.sp), modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, MaterialTheme.colorScheme.primary)) },
                confirmButton = {
                    if (selectedText.isNotEmpty()) {
                        Button(modifier = Modifier.padding(top = 8.dp), onClick = {
                            setSearchTerms("$selectedText,")
                            navController.navigate(Screens.Search.name)
                        }) { Text(stringResource(R.string.search_label)) }
                    }
                })
        }
    }
}

class Download() : EpisodeAction() {
    override val id: String
        get() = "START_DOWNLOAD"
    override val title: String
        get() = getAppContext().getString(R.string.download_label)

    override val iconRes:  Int = R.drawable.ic_download
    override val color: Color = Color(0xFF55FF00)

    override fun enabled(): Boolean = onEpisode?.downloaded == false && onEpisode?.feed != null && !onEpisode!!.feed!!.isLocalFeed


    override fun performAction(e: Episode) {
        super.performAction(e)
        if (!e.downloaded && e.feed != null && !e.feed!!.isLocalFeed) EpisodeActionButton(e, ButtonTypes.DOWNLOAD).onClick()
    }
}

class Shelve : EpisodeAction() {
    override val id: String
        get() = "SHELVE"
    private var showShelveDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.shelve_label)

    override val iconRes:  Int = R.drawable.baseline_shelves_24
    override val color: Color = Color(0xFF88FF99)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showShelveDialog = true
    }
    @Composable
    override fun ActionOptions() {
        if (showShelveDialog && onEpisode != null) ShelveDialog(listOf(onEpisode!!)) { showShelveDialog = false }
    }
}

class Delete() : EpisodeAction() {
    override val id: String
        get() = "DELETE"
    override val title: String
        get() = getAppContext().getString(R.string.delete_episode_label)

    override val iconRes:  Int = R.drawable.ic_delete
    override val color: Color = Color(0xFFFF3388)

    override fun enabled(): Boolean = onEpisode?.downloaded == true

    override fun performAction(e: Episode) {
        var item_ = e
        super.performAction(e)
        if (!item_.downloaded && item_.feed?.isLocalFeed != true) return
        runOnIOScope {
            val almostEnded = item_.hasAlmostEnded()
            if (almostEnded) {
                item_ = upsert(item_) {
                    if (it.playState < EpisodeState.PLAYED.code) it.setPlayState(EpisodeState.PLAYED)
                    it.playbackCompletionDate = Date()
                }
            }
            deleteEpisodesWarnLocalRepeat(listOf(item_))
            //                withContext(Dispatchers.Main) {
            ////                    vm.actionButton.update(vm.episode)
            //                }
        }
    }
}

class RemoveFromHistory() : EpisodeAction() {
    override val id: String
        get() = "REMOVE_FROM_HISTORY"
    val TAG = this::class.simpleName ?: "Anonymous"

    override val title: String
        get() = getAppContext().getString(R.string.remove_history_label)

    override val iconRes:  Int = R.drawable.ic_history_remove
    override val color: Color = Color(0xFFFF55FF)

    override fun enabled(): Boolean = (onEpisode?.lastPlayedTime ?: 0L) > 0L || (onEpisode?.playbackCompletionTime ?: 0L) > 0L

    override fun performAction(e: Episode) {
        super.performAction(e)

        fun setHistoryDates(lastPlayed: Long = 0, completed: Date = Date(0)) {
            runOnIOScope {
                val episode_ = realm.query(Episode::class).query("id == $0", e.id).first().find()
                if (episode_ != null) {
                    upsert(episode_) {
                        it.lastPlayedTime = lastPlayed
                        it.playbackCompletionDate = completed
                    }
                }
            }
        }

        val playbackCompletionDate = e.playbackCompletionDate
        val lastPlayedDate = e.lastPlayedTime
        setHistoryDates()
        commonConfirm = CommonConfirmAttrib(
            title = getAppContext().getString(R.string.removed_history_label),
            message = "",
            confirmRes = R.string.undo,
            cancelRes = R.string.no,
            onConfirm = {  if (playbackCompletionDate != null) setHistoryDates(lastPlayedDate, playbackCompletionDate) })
    }
}

class Erase : EpisodeAction() {
    override val id: String
        get() = "ERASE"
    private var showEraseDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.erase_episodes_label)

    override val iconRes: Int = R.drawable.baseline_delete_forever_24
    override val color: Color = Color(0xFFFF0099)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showEraseDialog = true
    }

    @Composable
    override fun ActionOptions() {
        if (showEraseDialog && onEpisode != null) EraseEpisodesDialog(listOf(onEpisode!!), onEpisode!!.feed) { showEraseDialog = false }
    }
}

class Alarm : EpisodeAction() {
    override val id: String
        get() = "ALARM"
    private var showTimerDialog by mutableStateOf(false)
    override val title: String
        get() = getAppContext().getString(R.string.alarm_episodes_label)

    override val iconRes:  Int = R.drawable.baseline_access_alarms_24
    override val color: Color = Color(0xFFB9BE33)

    override fun performAction(e: Episode) {
        super.performAction(e)
        showTimerDialog = true
    }
    @Composable
    override fun ActionOptions() {
        if (showTimerDialog && onEpisode != null) AddTimerDialog(onEpisode!!) { showTimerDialog = false }
    }
}
