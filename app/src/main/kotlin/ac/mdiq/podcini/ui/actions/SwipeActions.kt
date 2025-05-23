package ac.mdiq.podcini.ui.actions

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesWarnLocalRepeat
import ac.mdiq.podcini.storage.database.Episodes.hasAlmostEnded
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.Queues.addToActiveQueue
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.smartRemoveFromQueue
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.ChooseRatingDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.compose.EraseEpisodesDialog
import ac.mdiq.podcini.ui.compose.IgnoreEpisodesDialog
import ac.mdiq.podcini.ui.compose.LargeTextEditingDialog
import ac.mdiq.podcini.ui.compose.PlayStateDialog
import ac.mdiq.podcini.ui.compose.PutToQueueDialog
import ac.mdiq.podcini.ui.compose.ShelveDialog
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.setSearchTerms
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.Date

abstract class SwipeAction {
    abstract val id: String
    abstract val title: String
    open fun enabled(): Boolean = true
    abstract val iconRes: Int
    abstract val colorRes: Int
    @Composable
    open fun ActionOptions() {}
    open fun performAction(vm: EpisodeVM) {
        onEVM = vm
    }
    companion object {
        internal var onEVM by mutableStateOf<EpisodeVM?>(null)
    }
}

class SwipeActions(private val context: Context, private val tag: String) : DefaultLifecycleObserver {
    val actionsList: List<SwipeAction> = listOf(
        NoAction(), Combo(),
        SetPlaybackState(),
        AddToAssociatedQueue(), AddToActiveQueue(), PutToQueue(),
        RemoveFromQueue(),
        SetRating(), AddComment(),
        SearchSelected(),
        Download(),
        Delete(), RemoveFromHistory(),
        Shelve(), Erase())

    enum class ActionTypes {
        NO_ACTION,
        COMBO,
        RATING,
        COMMENT,
        SET_PLAY_STATE,
        SEARCH_SELECTED,
        ADD_TO_QUEUE,
        ADD_TO_ASSOCIATED,
        PUT_TO_QUEUE,
        REMOVE_FROM_QUEUE,
        START_DOWNLOAD,
        DELETE,
        REMOVE_FROM_HISTORY,
        SHELVE,
        ERASE
    }

    var actions by mutableStateOf(getPrefs(tag, ""))

    override fun onStart(owner: LifecycleOwner) {
        actions = getPrefs(tag, "")
    }

    @Composable
    fun ActionOptionsDialog() {
        actions.left[0].ActionOptions()
        actions.right[0].ActionOptions()
    }

    private fun getPrefs(tag: String, defaultActions: String): RightLeftActions {
        val prefsString = prefs.getString(KEY_PREFIX_SWIPEACTIONS + tag, defaultActions) ?: defaultActions
        return RightLeftActions(prefsString)
    }

    inner class RightLeftActions(actions_: String) {
        @JvmField
        var right: MutableList<SwipeAction> = mutableListOf(NoAction(), NoAction())
        @JvmField
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

    inner class SetPlaybackState : SwipeAction() {
        private var showPlayStateDialog by mutableStateOf(false)
        override val id: String
            get() = ActionTypes.SET_PLAY_STATE.name
        override val title: String
            get() = getAppContext().getString(R.string.set_play_state_label)

        override val iconRes:  Int = R.drawable.ic_mark_played
        override val colorRes:  Int = R.attr.icon_gray

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showPlayStateDialog = true
        }
        @Composable
        override fun ActionOptions() {
            var showIgnoreDialog by remember { mutableStateOf(false) }
            if (showIgnoreDialog) IgnoreEpisodesDialog(listOf(onEVM!!), onDismissRequest = { showIgnoreDialog = false })
            if (showPlayStateDialog && onEVM != null) PlayStateDialog(listOf(onEVM!!), onDismissRequest = { showPlayStateDialog = false }) { showIgnoreDialog = true }
        }
    }

    inner class AddToActiveQueue : SwipeAction() {
        override val id: String
            get() = ActionTypes.ADD_TO_QUEUE.name
        override val title: String
            get() = getAppContext().getString(R.string.add_to_queue_label)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean {
            if (onEVM?.episode?.feed?.queue != null) return false
            return onEVM != null && !curQueue.contains(onEVM!!.episode)
        }

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            addToActiveQueue(vm.episode)
        }
    }

    inner class AddToAssociatedQueue : SwipeAction() {
        override val id: String
            get() = ActionTypes.ADD_TO_ASSOCIATED.name
        override val title: String
            get() = getAppContext().getString(R.string.add_to_associated_queue)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean {
            val q = onEVM?.episode?.feed?.queue ?: return false
            return !q.contains(onEVM!!.episode)
        }

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            if (vm.episode.feed?.queue != null) runOnIOScope { addToQueueSync(vm.episode) }
        }
    }

    inner class PutToQueue : SwipeAction() {
        private var showPutToQueueDialog by mutableStateOf(false)
        override val id: String
            get() = ActionTypes.PUT_TO_QUEUE.name
        override val title: String
            get() = getAppContext().getString(R.string.put_in_queue_label)

        override val iconRes:  Int = R.drawable.ic_playlist_play
        override val colorRes:  Int = R.attr.icon_gray

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showPutToQueueDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showPutToQueueDialog && onEVM != null) PutToQueueDialog(listOf(onEVM!!)) { showPutToQueueDialog = false }
        }
    }

    inner class Combo : SwipeAction() {
        var showDialog by mutableStateOf(false)
        private var useAction by mutableStateOf<SwipeAction?>(null)
        override val id: String
            get() = ActionTypes.COMBO.name
        override val title: String
            get() = getAppContext().getString(R.string.combo_action)

        override val iconRes:  Int = R.drawable.baseline_category_24
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showDialog = true
        }
        @Composable
        override fun ActionOptions() {
            useAction?.ActionOptions()
            if (showDialog && onEVM != null) Dialog(onDismissRequest = { showDialog = false }) {
                val context = LocalContext.current
                Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (action in actionsList) {
                            if (action is NoAction || action is Combo) continue
                            if (!action.enabled()) continue
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    useAction = action
                                    action.performAction(onEVM!!)
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
    }

    inner class Delete : SwipeAction() {
        override val id: String
            get() = ActionTypes.DELETE.name
        override val title: String
            get() = getAppContext().getString(R.string.delete_episode_label)

        override val iconRes:  Int = R.drawable.ic_delete
        override val colorRes:  Int = R.attr.icon_red

        override fun enabled(): Boolean = onEVM?.episode?.downloaded == true

        override fun performAction(vm: EpisodeVM) {
            var item_ = vm.episode
            super.performAction(vm)
            if (!item_.downloaded && item_.feed?.isLocalFeed != true) return
            runOnIOScope {
                val almostEnded = hasAlmostEnded(item_)
                if (almostEnded && item_.playState < EpisodeState.PLAYED.code) item_ = setPlayStateSync(EpisodeState.PLAYED.code, item_, resetMediaPosition = true, removeFromQueue = false)
                if (almostEnded) item_ = upsertBlk(item_) { it.playbackCompletionDate = Date() }
                deleteEpisodesWarnLocalRepeat(context, listOf(item_))
                vm.updateVMFromDB()
                vm.actionButton = vm.actionButton.update(vm.episode)
            }
        }
    }

    inner class SetRating : SwipeAction() {
        private var showChooseRatingDialog by mutableStateOf(false)
        override val id: String
            get() = ActionTypes.RATING.name
        override val title: String
            get() = getAppContext().getString(R.string.set_rating_label)

        override val iconRes:  Int = R.drawable.ic_star
        override val colorRes:  Int = R.attr.icon_yellow


        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showChooseRatingDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showChooseRatingDialog && onEVM != null) ChooseRatingDialog(listOf(onEVM!!)) { showChooseRatingDialog = false }
        }
    }

    inner class AddComment : SwipeAction() {
        private var showEditComment by mutableStateOf(false)
        private var localTime by mutableLongStateOf(System.currentTimeMillis())
        private var editCommentText by mutableStateOf(TextFieldValue(""))
        override val id: String
            get() = ActionTypes.COMMENT.name
        override val title: String
            get() = getAppContext().getString(R.string.add_opinion_label)

        override val iconRes:  Int = R.drawable.baseline_comment_24
        override val colorRes:  Int = R.attr.icon_yellow

        override fun performAction(vm: EpisodeVM) {
            val e = (if (isCurMedia(vm.episode)) curEpisode else realm.query(Episode::class).query("id == ${vm.episode.id}").first().find()) ?: return
            onEVM?.episode = e
            localTime = System.currentTimeMillis()
            editCommentText = TextFieldValue((if (onEVM?.episode?.comment.isNullOrBlank()) "" else onEVM!!.episode.comment + "\n") + fullDateTimeString(localTime) + ":\n")
            showEditComment = true
        }
        @Composable
        override fun ActionOptions() {
            if (showEditComment) {
                LargeTextEditingDialog(textState = editCommentText, onTextChange = { editCommentText = it }, onDismissRequest = { showEditComment = false },
                    onSave = {
                        if (onEVM != null) runOnIOScope {
                            onEVM!!.episode = upsert(onEVM!!.episode) {
                                it.comment = editCommentText.text
                                it.commentTime = localTime
                            }
//                            if (isCurMedia(onEpisode)) setCurEpisode(onEpisode!!)
//                            onEpisode = null    // this is needed, otherwise the realm.query clause does not update onEpisode for some reason
                        }
                    })
            }
        }
    }

    inner class SearchSelected : SwipeAction() {
        private var showSearchDialog by mutableStateOf(false)
        override val id: String
            get() = ActionTypes.SEARCH_SELECTED.name
        override val title: String
            get() = getAppContext().getString(R.string.search_selected)

        override val iconRes:  Int = R.drawable.ic_search
        override val colorRes:  Int = R.attr.icon_yellow

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showSearchDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showSearchDialog && onEVM?.episode?.title != null) {
                var textFieldValue by remember { mutableStateOf(TextFieldValue(onEVM!!.episode.title!!)) }
                val selectedText by remember(textFieldValue.selection) { mutableStateOf(
                    if (textFieldValue.selection.collapsed) ""
                    else {
                        val start = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
                        val end = textFieldValue.selection.end.coerceIn(start, textFieldValue.text.length)
                        textFieldValue.text.substring(startIndex = start, endIndex = end)
                    }) }
                AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showSearchDialog = false },
                    title = { Text(stringResource(R.string.select_text_to_search), style = CustomTextStyles.titleCustom) },
                    text = { TextField(value = textFieldValue, onValueChange = { textFieldValue = it }, readOnly = true, textStyle = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary)) },
                    confirmButton = {
                        if (selectedText.isNotEmpty()) {
                            Button(modifier = Modifier.padding(top = 8.dp), onClick = {
                                setSearchTerms("$selectedText,")
                                mainNavController.navigate(Screens.Search.name)
                            }) { Text(stringResource(R.string.search_label)) }
                        }
                    })
            }
        }
    }

    class NoAction : SwipeAction() {
        override val id: String
            get() = ActionTypes.NO_ACTION.name
        override val title: String
            get() = getAppContext().getString(R.string.no_action_label)

        override val iconRes:  Int = R.drawable.ic_questionmark
        override val colorRes:  Int = R.attr.icon_red

        override fun enabled(): Boolean = false
    }

    inner class RemoveFromHistory : SwipeAction() {
        val TAG = this::class.simpleName ?: "Anonymous"

        override val id: String
            get() = ActionTypes.REMOVE_FROM_HISTORY.name
        override val title: String
            get() = getAppContext().getString(R.string.remove_history_label)

        override val iconRes:  Int = R.drawable.ic_history_remove
        override val colorRes:  Int = R.attr.icon_purple

        override fun enabled(): Boolean = (onEVM?.episode?.lastPlayedTime ?: 0L) > 0L || (onEVM?.episode?.playbackCompletionTime ?: 0L) > 0L

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            val playbackCompletionDate = vm.episode.playbackCompletionDate
            val lastPlayedDate = vm.episode.lastPlayedTime
            setHistoryDates(vm.episode)
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.removed_history_label),
                message = "",
                confirmRes = R.string.undo,
                cancelRes = R.string.no,
                onConfirm = {  if (playbackCompletionDate != null) setHistoryDates(vm.episode, lastPlayedDate, playbackCompletionDate) })
        }
        private fun setHistoryDates(episode: Episode, lastPlayed: Long = 0, completed: Date = Date(0)) {
            runOnIOScope {
                val episode_ = realm.query(Episode::class).query("id == $0", episode.id).first().find()
                if (episode_ != null) {
                    upsert(episode_) {
                        it.lastPlayedTime = lastPlayed
                        it.playbackCompletionDate = completed
                    }
                    EventFlow.postEvent(FlowEvent.HistoryEvent())
                }
            }
        }
    }

    inner class RemoveFromQueue : SwipeAction() {
        override val id: String
            get() = ActionTypes.REMOVE_FROM_QUEUE.name
        override val title: String
            get() = getAppContext().getString(R.string.remove_from_queue_label)

        override val iconRes:  Int = R.drawable.ic_playlist_remove
        override val colorRes:  Int = android.R.attr.colorAccent

        override fun enabled(): Boolean = onEVM != null && curQueue.contains(onEVM!!.episode)

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            runOnIOScope { smartRemoveFromQueue(vm.episode) }
        }
    }

    inner class Download : SwipeAction() {
        override val id: String
            get() = ActionTypes.START_DOWNLOAD.name
        override val title: String
            get() = getAppContext().getString(R.string.download_label)

        override val iconRes:  Int = R.drawable.ic_download
        override val colorRes:  Int = R.attr.icon_green

        override fun enabled(): Boolean = onEVM?.episode?.downloaded == false && onEVM?.episode?.feed != null && !onEVM!!.episode.feed!!.isLocalFeed

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            if (!vm.episode.downloaded && vm.episode.feed != null && !vm.episode.feed!!.isLocalFeed) DownloadActionButton(vm.episode).onClick(context)
        }
    }

    inner class Shelve : SwipeAction() {
        private var showShelveDialog by mutableStateOf(false)
        override val id: String
            get() = ActionTypes.SHELVE.name
        override val title: String
            get() = getAppContext().getString(R.string.shelve_label)

        override val iconRes:  Int = R.drawable.baseline_shelves_24
        override val colorRes:  Int = R.attr.icon_gray

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showShelveDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showShelveDialog && onEVM != null) ShelveDialog(listOf(onEVM!!)) { showShelveDialog = false }
        }
    }

    inner class Erase : SwipeAction() {
        private var showEraseDialog by mutableStateOf(false)
        override val id: String
            get() =  ActionTypes.ERASE.name
        override val title: String
            get() = getAppContext().getString(R.string.erase_episodes_label)

        override val iconRes:  Int = R.drawable.baseline_delete_forever_24
        override val colorRes:  Int = R.attr.icon_gray

        override fun enabled(): Boolean = onEVM?.episode?.feed?.isSynthetic() == true

        override fun performAction(vm: EpisodeVM) {
            super.performAction(vm)
            showEraseDialog = true
        }
        @Composable
        override fun ActionOptions() {
            if (showEraseDialog && onEVM != null) EraseEpisodesDialog(listOf(onEVM!!), onEVM!!.episode.feed) { showEraseDialog = false }
        }
    }

    companion object {
        private const val SWIPE_ACTIONS_PREF_NAME: String = "SwipeActionsPrefs"
        private const val KEY_PREFIX_SWIPEACTIONS: String = "PrefSwipeActions"
        private const val KEY_PREFIX_NO_ACTION: String = "PrefNoSwipeAction"

        val prefs: SharedPreferences by lazy { getAppContext().getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE) }
//        fun getSharedPrefs(context: Context) {
//            if (prefs == null) prefs = getAppContext().getSharedPreferences(SWIPE_ACTIONS_PREF_NAME, Context.MODE_PRIVATE)
//        }

        @Composable
        fun SwipeActionsSettingDialog(sa: SwipeActions, onDismissRequest: () -> Unit, callback: (RightLeftActions)->Unit) {
            val context = LocalContext.current
            val textColor = MaterialTheme.colorScheme.onSurface

            var actions = remember { sa.actions }
            val leftAction = remember { mutableStateOf(actions.left) }
            val rightAction = remember { mutableStateOf(actions.right) }
            var keys by remember { mutableStateOf(sa.actionsList) }

            var direction by remember { mutableIntStateOf(0) }
            var showPickerDialog by remember { mutableStateOf(false) }
            if (showPickerDialog) {
                Dialog(onDismissRequest = { showPickerDialog = false }) {
                    Card(modifier = Modifier
                        .wrapContentSize(align = Alignment.Center)
                        .fillMaxWidth()
                        .padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.padding(16.dp)) {
                            items(keys.size) { index ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                                    .padding(16.dp)
                                    .clickable {
                                        when (direction) {
                                            -1 -> leftAction.value[0] = keys[index]
                                            1 -> rightAction.value[0] = keys[index]
                                            else -> {}
                                        }
                                        showPickerDialog = false
                                    }) {
                                    Icon(imageVector = ImageVector.vectorResource(keys[index].iconRes), tint = textColor, contentDescription = null, modifier = Modifier
                                        .width(35.dp)
                                        .height(35.dp))
                                    Text(keys[index].title, color = textColor, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            if (!showPickerDialog) Dialog(onDismissRequest = { onDismissRequest() }) {
                Logd("SwipeActions", "SwipeActions tag: ${sa.tag}")
                val forFragment = remember(sa.tag) {
                    if (sa.tag != Screens.Queues.name) keys = keys.filter { a: SwipeAction -> a !is RemoveFromQueue }
                    when (sa.tag) {
                        Screens.Facets.name -> context.getString(R.string.facets)
                        Screens.OnlineEpisodes.name -> context.getString(R.string.online_episodes_label)
                        Screens.Search.name -> context.getString(R.string.search_label)
                        Screens.FeedDetails.name -> {
                            keys = keys.filter { a: SwipeAction -> a !is RemoveFromHistory }
                            context.getString(R.string.subscription)
                        }
                        Screens.Queues.name -> {
                            keys = keys.filter { a: SwipeAction -> (a !is AddToActiveQueue && a !is RemoveFromHistory) }
                            context.getString(R.string.queue_label)
                        }
                        else -> { "" }
                } }
                Card(modifier = Modifier
                    .wrapContentSize(align = Alignment.Center)
                    .fillMaxWidth()
                    .padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.swipeactions_label) + " - " + forFragment)
                        Text(stringResource(R.string.swipe_left))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(leftAction.value[0].iconRes), tint = textColor, contentDescription = null, modifier = Modifier
                                .width(35.dp)
                                .height(35.dp)
                                .clickable(onClick = {
                                    direction = -1
                                    showPickerDialog = true
                                }))
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_left_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier
                                .width(50.dp)
                                .height(35.dp))
                            Spacer(Modifier.weight(0.5f))
                        }
                        Text(stringResource(R.string.swipe_right))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                            Spacer(Modifier.weight(0.5f))
                            Icon(imageVector = ImageVector.vectorResource(R.drawable.baseline_arrow_right_alt_24), tint = textColor, contentDescription = "right_arrow", modifier = Modifier
                                .width(50.dp)
                                .height(35.dp))
                            Spacer(Modifier.weight(0.1f))
                            Icon(imageVector = ImageVector.vectorResource(rightAction.value[0].iconRes), tint = textColor, contentDescription = null, modifier = Modifier
                                .width(35.dp)
                                .height(35.dp)
                                .clickable(onClick = {
                                    direction = 1
                                    showPickerDialog = true
                                }))
                            Spacer(Modifier.weight(0.1f))
                        }
                        Button(onClick = {
                            actions = sa.RightLeftActions("${rightAction.value[0].id},${leftAction.value[0].id}")
                            prefs.edit { putString(KEY_PREFIX_SWIPEACTIONS + sa.tag, "${rightAction.value[0].id},${leftAction.value[0].id}") }
                            prefs.edit { putBoolean(KEY_PREFIX_NO_ACTION + sa.tag, true) }
                            callback(actions)
                            onDismissRequest()
                        }) { Text(stringResource(R.string.confirm_label)) }
                    }
                }
            }
        }
    }
}
