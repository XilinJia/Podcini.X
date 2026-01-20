package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.storage.database.addToAssQueue
import ac.mdiq.podcini.storage.database.addToQueue
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.smartRemoveFromAllQueues
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.AddTimerDialog
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommentEditingDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EraseEpisodesDialog
import ac.mdiq.podcini.ui.compose.FutureStateDialog
import ac.mdiq.podcini.ui.compose.IgnoreEpisodesDialog
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.compose.PutToQueueDialog
import ac.mdiq.podcini.ui.compose.ShelveDialog
import ac.mdiq.podcini.ui.compose.TagSettingDialog
import ac.mdiq.podcini.ui.compose.TagType
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.screens.setSearchTerms
import ac.mdiq.podcini.utils.Logd
import android.util.TypedValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import java.util.Date

abstract class SwipeAction {
    abstract val id: String
    abstract val title: String
    open fun enabled(): Boolean = true
    abstract val iconRes: Int
    abstract val colorRes: Int
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

class SwipeActions(private val tag: String) : DefaultLifecycleObserver {
    val actionsList: List<SwipeAction> = listOf(
        NoAction(),
        Combo(),
        SetPlaybackState(),
        AddToAssociatedQueue(),
        AddToActiveQueue(),
        PutToQueue(),
        RemoveFromQueue(),
        SetRating(),
        AddComment(),
        AddTag(),
        SearchSelected(),
        Download(),
        Delete(),
        RemoveFromHistory(),
        Shelve(),
        Erase(),
        Alarm())

    var actions by mutableStateOf(RightLeftActions(appAttribs.swipeActionsMap[tag] ?: ""))

    override fun onStart(owner: LifecycleOwner) {
        actions = RightLeftActions(appAttribs.swipeActionsMap[tag] ?: "")
    }

    @Composable
    fun ActionOptionsDialog() {
        actions.left[0].ActionOptions()
        actions.right[0].ActionOptions()
    }

    inner class RightLeftActions(actions_: String) {
        
        var right: MutableList<SwipeAction> = mutableListOf(NoAction(), NoAction())
        
        var left: MutableList<SwipeAction> = mutableListOf(NoAction(), NoAction())

        init {
            val actions = actions_.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (actions.size == 2) {
                val rActs = actionsList.filter { a: SwipeAction -> a.id == actions[0] }
                this.right[0] = if (rActs.isEmpty()) actionsList[0] else rActs[0]
                val lActs = actionsList.filter { a: SwipeAction -> a.id == actions[1] }
                this.left[0] = if (lActs.isEmpty()) actionsList[0] else lActs[0]
            }
        }
    }

    class SetPlaybackState : SwipeAction() {
        override val id: String
            get() = "SET_PLAY_STATE"
        private var showPlayStateDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.set_play_state_label)

        override val iconRes:  Int = R.drawable.ic_mark_played
        override val colorRes:  Int = R.attr.icon_gray

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

    class AddToActiveQueue : SwipeAction() {
        override val id: String
            get() = "ADD_TO_QUEUE"
        override val title: String
            get() = getAppContext().getString(R.string.add_to_active_queue)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean {
            if (onEpisode?.feed?.queue != null) return false
            return onEpisode != null && !actQueue.contains(onEpisode!!)
        }

        override fun performAction(e: Episode) {
            super.performAction(e)
            runOnIOScope { addToQueue(listOf(e), actQueue) }
        }
    }

    class AddToAssociatedQueue : SwipeAction() {
        override val id: String
            get() = "ADD_TO_ASSOCIATED"
        override val title: String
            get() = getAppContext().getString(R.string.add_to_associated_queue)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean {
            val q = onEpisode?.feed?.queue ?: return false
            return !q.contains(onEpisode!!)
        }

        override fun performAction(e: Episode) {
            super.performAction(e)
            if (e.feed?.queue != null) runOnIOScope { addToAssQueue(listOf(e)) }
        }
    }

    class PutToQueue : SwipeAction() {
        override val id: String
            get() = "PUT_TO_QUEUE"
        private var showPutToQueueDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.add_to_queue)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = R.attr.icon_gray

        override fun performAction(e: Episode) {
            super.performAction(e)
            showPutToQueueDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showPutToQueueDialog && onEpisode != null) PutToQueueDialog(listOf(onEpisode!!)) { showPutToQueueDialog = false }
        }
    }

    inner class Combo : SwipeAction() {
        override val id: String
            get() = "COMBO"
        override val title: String
            get() = getAppContext().getString(R.string.combo_action)
        override val iconRes:  Int = R.drawable.baseline_category_24
        override val colorRes:  Int = android.R.attr.colorAccent

        var showDialog by mutableStateOf(false)
        private var useAction by mutableStateOf<SwipeAction?>(null)

        override fun performAction(e: Episode) {
            super.performAction(e)
            showDialog = true
        }
        @Composable
        override fun ActionOptions() {
            useAction?.ActionOptions()
            if (showDialog && onEpisode != null) CommonPopupCard(onDismissRequest = { showDialog = false }) {
                val context = LocalContext.current
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (action in actionsList) {
                        if (action is NoAction || action is Combo) continue
                        if (!action.enabled()) continue
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)
                            .clickable {
                                useAction = action
                                action.performAction(onEpisode!!)
                                showDialog = false
                            }) {
                            val colorAccent = remember {
                                val typedValue = TypedValue()
                                context.theme.resolveAttribute(action.colorRes, typedValue, true)
                                Color(typedValue.data)
                            }
                            Icon(imageVector = ImageVector.vectorResource(id = action.iconRes),  tint = colorAccent, contentDescription = action.title)
                            Text(action.title, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }

    inner class Delete() : SwipeAction() {
        override val id: String
            get() = "DELETE"
        override val title: String
            get() = getAppContext().getString(R.string.delete_episode_label)

        override val iconRes:  Int = R.drawable.ic_delete
        override val colorRes:  Int = R.attr.icon_red

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

    class SetRating : SwipeAction() {
        override val id: String
            get() = "RATING"
        private var showChooseRatingDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.set_rating_label)

        override val iconRes:  Int = R.drawable.ic_star
        override val colorRes:  Int = R.attr.icon_yellow

        override fun performAction(e: Episode) {
            super.performAction(e)
            showChooseRatingDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showChooseRatingDialog && onEpisode != null) ChooseRatingDialog(listOf(onEpisode!!)) { showChooseRatingDialog = false }
        }
    }

    class AddComment : SwipeAction() {
        override val id: String
            get() = "COMMENT"
        private var showEditComment by mutableStateOf(false)
        private var editCommentText by mutableStateOf(TextFieldValue(""))
        override val title: String
            get() = getAppContext().getString(R.string.add_opinion_label)

        override val iconRes:  Int = R.drawable.baseline_comment_24
        override val colorRes:  Int = R.attr.icon_yellow

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

    class AddTag : SwipeAction() {
        override val id: String
            get() = "TAG"
        private var showTagDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.tags_label)

        override val iconRes:  Int = R.drawable.baseline_label_24
        override val colorRes:  Int = R.attr.icon_yellow

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

    class SearchSelected : SwipeAction() {
        override val id: String
            get() = "SEARCH_SELECTED"
        private var showSearchDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.search_selected)

        override val iconRes:  Int = R.drawable.ic_search
        override val colorRes:  Int = R.attr.icon_yellow

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

    class NoAction : SwipeAction() {
        override val id: String
            get() = "NO_ACTION"
        override val title: String
            get() = getAppContext().getString(R.string.no_action_label)

        override val iconRes:  Int = R.drawable.ic_questionmark
        override val colorRes:  Int = R.attr.icon_red

        override fun enabled(): Boolean = false
    }

    inner class RemoveFromHistory() : SwipeAction() {
        override val id: String
            get() = "REMOVE_FROM_HISTORY"
        val TAG = this::class.simpleName ?: "Anonymous"

        override val title: String
            get() = getAppContext().getString(R.string.remove_history_label)

        override val iconRes:  Int = R.drawable.ic_history_remove
        override val colorRes:  Int = R.attr.icon_purple

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

    class RemoveFromQueue : SwipeAction() {
        override val id: String
            get() = "REMOVE_FROM_QUEUE"
        override val title: String
            get() = getAppContext().getString(R.string.remove_from_all_queues)

        override val iconRes:  Int = R.drawable.ic_playlist_remove
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean = onEpisode != null && actQueue.contains(onEpisode!!)

        override fun performAction(e: Episode) {
            super.performAction(e)
            runOnIOScope { smartRemoveFromAllQueues(e) }
        }
    }

    inner class Download() : SwipeAction() {
        override val id: String
            get() = "START_DOWNLOAD"
        override val title: String
            get() = getAppContext().getString(R.string.download_label)

        override val iconRes:  Int = R.drawable.ic_download
        override val colorRes:  Int = R.attr.icon_green

        override fun enabled(): Boolean = onEpisode?.downloaded == false && onEpisode?.feed != null && !onEpisode!!.feed!!.isLocalFeed

        @UnstableApi
        override fun performAction(e: Episode) {
            super.performAction(e)
            if (!e.downloaded && e.feed != null && !e.feed!!.isLocalFeed) EpisodeActionButton(e, ButtonTypes.DOWNLOAD).onClick()
        }
    }

    class Shelve : SwipeAction() {
        override val id: String
            get() = "SHELVE"
        private var showShelveDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.shelve_label)

        override val iconRes:  Int = R.drawable.baseline_shelves_24
        override val colorRes:  Int = R.attr.icon_gray

        override fun performAction(e: Episode) {
            super.performAction(e)
            showShelveDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showShelveDialog && onEpisode != null) ShelveDialog(listOf(onEpisode!!)) { showShelveDialog = false }
        }
    }

    class Erase : SwipeAction() {
        override val id: String
            get() = "ERASE"
        private var showEraseDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.erase_episodes_label)

        override val iconRes: Int = R.drawable.baseline_delete_forever_24
        override val colorRes: Int = R.attr.icon_gray

        override fun performAction(e: Episode) {
            super.performAction(e)
            showEraseDialog = true
        }

        @Composable
        override fun ActionOptions() {
            if (showEraseDialog && onEpisode != null) EraseEpisodesDialog(listOf(onEpisode!!), onEpisode!!.feed) { showEraseDialog = false }
        }
    }

    class Alarm : SwipeAction() {
        override val id: String
            get() = "ALARM"
        private var showTimerDialog by mutableStateOf(false)
        override val title: String
            get() = getAppContext().getString(R.string.alarm_episodes_label)

        override val iconRes:  Int = R.drawable.baseline_access_alarms_24
        override val colorRes:  Int = R.attr.icon_red

        override fun performAction(e: Episode) {
            super.performAction(e)
            showTimerDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showTimerDialog && onEpisode != null) AddTimerDialog(onEpisode!!) { showTimerDialog = false }
        }
    }

    companion object {
        @Composable
        fun SwipeActionsSettingDialog(sa: SwipeActions, onDismissRequest: () -> Unit, callback: (RightLeftActions)->Unit) {
            val context by rememberUpdatedState(LocalContext.current)
            val textColor = MaterialTheme.colorScheme.onSurface

            var actions by remember { mutableStateOf(sa.actions) }
            val leftAction = remember { mutableStateOf(actions.left[0]) }
            val rightAction = remember { mutableStateOf(actions.right[0]) }
            var keys by remember { mutableStateOf(sa.actionsList) }

            var direction by remember { mutableIntStateOf(0) }
            var showPickerDialog by remember { mutableStateOf(false) }
            if (showPickerDialog) {
                CommonPopupCard(onDismissRequest = { showPickerDialog = false }) {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(16.dp)) {
                        items(keys.size) { index ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)
                                .clickable {
                                    when (direction) {
                                        -1 -> leftAction.value = keys[index]
                                        1 -> rightAction.value = keys[index]
                                        else -> {}
                                    }
                                    showPickerDialog = false
                                }) {
                                Icon(imageVector = ImageVector.vectorResource(keys[index].iconRes), tint = textColor, contentDescription = null, modifier = Modifier.width(35.dp).height(35.dp))
                                Text(keys[index].title, color = textColor, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            if (!showPickerDialog) CommonPopupCard(onDismissRequest = { onDismissRequest() }) {
                Logd("SwipeActions", "SwipeActions tag: ${sa.tag}")
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    val forFragment = remember(sa.tag) {
                        if (sa.tag != Screens.Queues.name) keys = keys.filter { a: SwipeAction -> a !is RemoveFromQueue }
                        when (sa.tag) {
                            Screens.Facets.name -> context.getString(R.string.facets)
                            Screens.OnlineFeed.name -> context.getString(R.string.online_episodes_label)
                            Screens.Search.name -> context.getString(R.string.search_label)
                            Screens.FeedDetails.name -> {
                                keys = keys.filter { a: SwipeAction -> a !is RemoveFromHistory }
                                context.getString(R.string.subscription)
                            }
                            Screens.Queues.name -> {
                                keys = keys.filter { a: SwipeAction -> (a !is AddToActiveQueue && a !is RemoveFromHistory) }
                                context.getString(R.string.queue_label)
                            }
                            else -> { sa.tag }
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
                        actions = sa.RightLeftActions("${rightAction.value.id},${leftAction.value.id}")
                        runOnIOScope { upsert(appAttribs) { it.swipeActionsMap[sa.tag] = "${rightAction.value.id},${leftAction.value.id}" } }
                        callback(actions)
                        onDismissRequest()
                    }) { Text(stringResource(R.string.confirm_label)) }
                }
            }
        }
    }
}
