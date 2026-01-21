package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun CommonDialogSurface(onDismissRequest: () -> Unit, content: @Composable (() -> Unit)) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            content()
        }
    }
}

@Composable
fun CommonPopupCard(onDismissRequest: () -> Unit, alignment: Alignment = Alignment.TopCenter, content: @Composable (() -> Unit)) {
    Popup(onDismissRequest = { onDismissRequest() }, alignment = alignment, properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true, clippingEnabled = true)) {
        Card(modifier = Modifier.wrapContentSize(align = Alignment.Center).padding(16.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            content()
        }
    }
}


@Composable
fun filterChipBorder(selected: Boolean): BorderStroke {
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
    return FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = selected,
        borderColor = buttonColor,
        selectedBorderColor = buttonAltColor,
        borderWidth = 1.dp,
        selectedBorderWidth = 2.dp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentSelectedItem by remember { mutableStateOf(selectedItem) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.large), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = currentSelectedItem, onValueChange = { currentSelectedItem = it},
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
            modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            decorationBox = { innerTextField ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    innerTextField()
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            })
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in items.indices) {
                DropdownMenuItem(text = { Text(items[i]) },
                    onClick = {
                        currentSelectedItem = items[i]
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MeasureLongestWidth(items: List<String>, textStyle: TextStyle, content: @Composable (maxWidth: Int) -> Unit) {
    SubcomposeLayout { constraints ->
        val placeables = items.map { label -> subcompose(label) { Text(label, style = textStyle) }.first().measure(constraints) }
        val maxWidth = placeables.maxOf { it.width }
        val placeable = subcompose("content") { content(maxWidth) }.first().measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

@Composable
fun CustomToast(message: String, durationMillis: Long = 3000L, onDismiss: () -> Unit) {
    var isForeground by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isForeground = when (event) {
                Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_PAUSE -> false
                else -> isForeground
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(message, isForeground) {
        if (message.isNotBlank() && isForeground) {
            delay(durationMillis)
            onDismiss()
        }
    }

    if (isForeground) {
        Popup(alignment = Alignment.Center, onDismissRequest = { onDismiss() }) {
            val color = if (message.contains("Error:")) Color.Red else MaterialTheme.colorScheme.onSecondary
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(text = message, color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun CommentEditingDialog(textState: TextFieldValue, autoSave: Boolean = true, onTextChange: (TextFieldValue) -> Unit, onDismissRequest: () -> Unit, onSave: () -> Unit) {
    Dialog(onDismissRequest = { onDismissRequest() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var textChanged by remember { mutableStateOf(false) }
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.add_comment), color = textColor, style = CustomTextStyles.titleCustom)
                Spacer(modifier = Modifier.height(16.dp))
                BasicTextField(value = textState, textStyle = TextStyle(fontSize = 16.sp, color = textColor), modifier = Modifier.fillMaxWidth().height(300.dp).padding(10.dp).border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                    onValueChange = {
                    textChanged = true
                    onTextChange(it)
                })
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) }
                    TextButton(onClick = {
                        onSave()
                        textChanged = false
                        onDismissRequest()
                    }) { Text("Save") }
                }
            }
        }
        LaunchedEffect(Unit) {
            while (autoSave) {
                delay(10000)
                if (textChanged) onSave()
                textChanged = false
            }
        }
    }
}

@Composable
fun NonlazyGrid(columns: Int, itemCount: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val rows = remember {
        var r = itemCount / columns
        if (itemCount.mod(columns) > 0) r += 1
        r
    }
    Column(modifier = modifier) {
        for (rowId in 0 until rows) {
            val firstIndex = remember { rowId * columns }
            Row {
                for (columnId in 0 until columns) {
                    val index = remember { firstIndex + columnId }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { if (index < itemCount) content(index) }
                }
            }
        }
    }
}

@Composable
fun ScrollRowGrid(columns: Int, itemCount: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val rows = remember {
        var r = itemCount / columns
        if (itemCount.mod(columns) > 0) r += 1
        r
    }
    Column(modifier = modifier) {
        for (rowId in 0 until rows) {
            val firstIndex = remember { rowId * columns }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (cid in 0 until columns) {
                    val index = remember { firstIndex + cid }
                    val mod = if (cid < columns-1) Modifier.padding(end = 10.dp) else Modifier
                    Box(modifier = mod) { if (index < itemCount) content(index) }
                }
            }
        }
    }
}

@Composable
fun SimpleSwitchDialog(title: String, text: String, onDismissRequest: ()->Unit, callback: (Boolean)-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    var isChecked by remember { mutableStateOf(false) }
    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { onDismissRequest() },
        title = { Text(title, style = CustomTextStyles.titleCustom) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(text, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = isChecked, onCheckedChange = { isChecked = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                callback(isChecked)
                onDismissRequest()
            }) { Text(text = "OK") }
        },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}

@Composable
fun IconTitleSummaryActionRow(vecRes: Int, titleRes: Int, summaryRes: Int, callback: ()-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
        Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
        Column(modifier = Modifier.weight(1f).clickable(onClick = { callback() })) {
            Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
            Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TitleSummaryActionColumn(titleRes: Int, summaryRes: Int, callback: ()-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = { callback() })) {
        if (titleRes != 0) Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
        if (summaryRes != 0) Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TitleSummarySwitchPrefRow(titleRes: Int, summaryRes: Int, pref: AppPrefs, cb: ((Boolean)->Unit)? = null) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
            Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        var isChecked by remember { mutableStateOf(getPref(pref, false)) }
        Switch(checked = isChecked, onCheckedChange = {
            isChecked = it
            if (cb != null) cb.invoke(it)
            else putPref(pref, it)
        })
    }
}

@Composable
fun TitleSummarySwitchRow(titleRes: Int, summaryRes: Int, initVal: Boolean, cb: ((Boolean)->Unit)) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
            Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        var isChecked by remember { mutableStateOf(initVal) }
        Switch(checked = isChecked, onCheckedChange = {
            isChecked = it
            cb.invoke(it)
        })
    }
}


var commonConfirm by mutableStateOf<CommonConfirmAttrib?>(null)
data class CommonConfirmAttrib(
    val title: String,
    val message: String,
    val confirmRes: Int,
    val onConfirm: ()->Unit,
    val cancelRes: Int,
    val onCancel: ()->Unit = { commonConfirm = null },
    val neutralRes: Int = 0,
    val onNeutral: (()->Unit)? = null
)

@Composable
fun CommonConfirmDialog(c: CommonConfirmAttrib) {
    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { commonConfirm = null },
        title = { Text(c.title) },
        text = {
            Column {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(c.message) }
                if (c.neutralRes > 0) TextButton(onClick = {
                    commonConfirm = null
                    c.onNeutral?.invoke()
                }) { Text(stringResource(c.neutralRes)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                commonConfirm = null
                c.onConfirm()
            }) { Text(stringResource(c.confirmRes)) }
        },
        dismissButton = { TextButton(onClick = {
            commonConfirm = null
            c.onCancel()
        }) { Text(stringResource(c.cancelRes)) } }
    )
}

var commonMessage by mutableStateOf<CommonMessageAttrib?>(null)
data class CommonMessageAttrib(
    val title: String,
    val message: String,
    val OKRes: Int,
    val onOK: ()->Unit
)

@Composable
fun LargePoster(c: CommonMessageAttrib) {
    AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { },
        title = { Text(c.title) },
        text = {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(c.message) }
        },
        confirmButton = {
            TextButton(onClick = {
                c.onOK()
            }) { Text(stringResource(c.OKRes)) }
        },
        dismissButton = { }
    )
}

@Composable
fun ComfirmDialog(titleRes: Int, message: String, showDialog: MutableState<Boolean>, cancellable: Boolean = true, onConfirm: () -> Unit) {
    if (showDialog.value) {
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showDialog.value = false },
            title = { if (titleRes != 0) Text(stringResource(titleRes)) },
            text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(message) } },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    showDialog.value = false
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { if (cancellable) TextButton(onClick = { showDialog.value = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
}

@Composable
fun SearchBarRow(hintTextRes: Int, defaultText: String, modifier: Modifier = Modifier, history: List<String> = listOf(), performSearch: (String) -> Unit) {
    val buttonColor = MaterialTheme.colorScheme.tertiary
    var showHistory by remember { mutableStateOf(false) }
    var queryText by remember { mutableStateOf(defaultText) }
    DropdownMenu(expanded = showHistory, onDismissRequest = { showHistory = false }) {
        for (i in history.indices) {
            DropdownMenuItem(text = { Text(history[i]) },
                onClick = {
                    queryText = history[i]
                    performSearch(queryText)
                    showHistory = false
                }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        TextField(value = queryText, onValueChange = { queryText = it }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            textStyle = TextStyle(fontSize = 16.sp), label = { Text(stringResource(hintTextRes)) },
            keyboardActions = KeyboardActions(onDone = { performSearch(queryText) }), modifier = Modifier.weight(1f),
            leadingIcon = if (history.isNotEmpty()) { { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_history), tint = buttonColor, contentDescription = "history",
                modifier = Modifier.width(40.dp).height(40.dp).padding(start = 5.dp).clickable(onClick = { showHistory = true })) } } else null,
            trailingIcon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), tint = buttonColor, contentDescription = "search",
                modifier = Modifier.width(40.dp).height(40.dp).padding(start = 5.dp).clickable(onClick = { performSearch(queryText) })) }
        )
    }
}

@Composable
fun NumberEditor(initVal: Int, label: String = "seconds", nz: Boolean = true, instant: Boolean = false, modifier: Modifier, cb: (Int)->Unit) {
    var inputVal by remember { mutableStateOf(initVal.toString()) }
    var showSet by remember { mutableStateOf(false) }
    fun set() {
        if (nz) {
            if (inputVal.isNotBlank()) {
                cb(inputVal.toInt())
                showSet = false
            }
        } else {
            val v = if (inputVal.isNotBlank()) inputVal.toInt() else 0
            cb(v)
            showSet = false
        }
    }
    if (instant)
        TextField(value = inputVal, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(label) }, singleLine = true, modifier = modifier,
            onValueChange = {
                if (it.isEmpty() || it.toIntOrNull() != null) inputVal = it
                if (it.toIntOrNull() != null) showSet = true
                if (instant && showSet) set()
            },
        )
    else
        TextField(value = inputVal, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(label) }, singleLine = true, modifier = modifier,
            onValueChange = {
                if (it.isEmpty() || it.toIntOrNull() != null) inputVal = it
                if (it.toIntOrNull() != null) showSet = true
                if (instant && showSet) set()
            },
            trailingIcon = { if (!instant && showSet) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = { set() })) }
        )
}

@Composable
fun SelectLowerAllUpper(selectedList: MutableList<MutableState<Boolean>>, lowerCB: (()->Unit)?, allCB: ()->Unit, upperCB: (()->Unit)?) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val buttonColor = MaterialTheme.colorScheme.tertiary
    val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
    var lowerSelected by remember { mutableStateOf(false) }
    var higherSelected by remember { mutableStateOf(false) }
    Spacer(Modifier.width(20.dp))
    if (lowerCB != null) {
        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (lowerSelected) buttonAltColor else buttonColor),
            onClick = {
                val hIndex = selectedList.indexOfLast { it.value }
                if (hIndex < 0) return@OutlinedButton
                if (!lowerSelected) {
                    for (i in 0..hIndex) selectedList[i].value = true
                } else {
                    for (i in 0..hIndex) selectedList[i].value = false
                    selectedList[hIndex].value = true
                }
                lowerSelected = !lowerSelected
                lowerCB()
            },
        ) { Text(text = "<<<", maxLines = 1, color = textColor) }
        Spacer(Modifier.width(20.dp))
    }
    OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (lowerSelected && higherSelected) buttonAltColor else buttonColor),
        onClick = {
            val selectAll = !(lowerSelected && higherSelected)
            lowerSelected = selectAll
            higherSelected = selectAll
            for (i in selectedList.indices) selectedList[i].value = selectAll
            allCB()
        },
    ) { Text(text = "A", maxLines = 1, color = textColor) }
    if (upperCB != null) {
        Spacer(Modifier.width(20.dp))
        OutlinedButton(modifier = Modifier.padding(0.dp).heightIn(min = 20.dp).widthIn(min = 20.dp).wrapContentWidth(), border = BorderStroke(2.dp, if (higherSelected) buttonAltColor else buttonColor),
            onClick = {
                val lIndex = selectedList.indexOfFirst { it.value }
                if (lIndex < 0) return@OutlinedButton
                if (!higherSelected) {
                    for (i in lIndex..<selectedList.size) selectedList[i].value = true
                } else {
                    for (i in lIndex..<selectedList.size) selectedList[i].value = false
                    selectedList[lIndex].value = true
                }
                higherSelected = !higherSelected
                upperCB()
            },
        ) { Text(text = ">>>", maxLines = 1, color = textColor) }
    }
}

enum class TagType { Feed, Episode }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagSettingDialog(tagType: TagType, existingTags: Set<String>, multiples: Boolean = false, onDismiss: () -> Unit, cb: (List<String>)->Unit) {
    CommonPopupCard(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tags_label), fontSize = MaterialTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            var text by remember { mutableStateOf("") }
            val allTags = remember { if (tagType == TagType.Feed) appAttribs.feedTagSet else appAttribs.episodeTagSet }
            var suggestedTags by remember { mutableStateOf(listOf<String>()) }
            var showSuggestions by remember { mutableStateOf(false) }
            val tags = remember { existingTags.toMutableStateList() }

            if (multiples) Text(stringResource(R.string.tagging_multiple_sum))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tags.forEach { FilterChip(onClick = {  }, label = { Text(it) }, selected = false, trailingIcon = { Icon(imageVector = Icons.Filled.Close, contentDescription = "Close icon",
                    modifier = Modifier.size(FilterChipDefaults.IconSize).clickable(onClick = { tags.remove(it) })) }) }
            }
            ExposedDropdownMenuBox(expanded = showSuggestions, onExpandedChange = { }) {
                TextField(value = text, placeholder = { Text("Type something...") }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
                    onValueChange = {
                        text = it
                        suggestedTags = tags.filter { item -> item.contains(text, ignoreCase = true) && item !in tags }
                        showSuggestions = text.isNotEmpty() && suggestedTags.isNotEmpty()
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (text.isNotBlank()) {
                                if (text !in tags) tags.add(text)
                                text = ""
                            }
                        }
                    ),
                    trailingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add icon",
                        modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = {
                            if (text.isNotBlank()) {
                                if (text !in tags) tags.add(text)
                                text = ""
                            }
                        })) }
                )
                ExposedDropdownMenu(expanded = showSuggestions, onDismissRequest = { showSuggestions = false }) {
                    for (i in suggestedTags.indices) {
                        DropdownMenuItem(text = { Text(suggestedTags[i]) },
                            onClick = {
                                text = suggestedTags[i]
                                showSuggestions = false
                            }
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                allTags.forEach { FilterChip(onClick = { tags.add(it) }, label = { Text(it) }, selected = false ) }
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
                Button(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    Logd("TagsSettingDialog", "tags: [${tags.joinToString()}] commonTags: [${existingTags.joinToString()}]")
                    cb(tags)
                    if (tagType == TagType.Feed) {
                        val tagsSet = appAttribs.feedTagSet.toMutableSet() + tags
                        upsertBlk(appAttribs) {
                            it.feedTagSet.clear()
                            it.feedTagSet.addAll(tagsSet)
                        }
                    } else {
                        val tagsSet = appAttribs.episodeTagSet.toMutableSet() + tags
                        upsertBlk(appAttribs) {
                            it.episodeTagSet.clear()
                            it.episodeTagSet.addAll(tagsSet)
                        }
                    }
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
            }
        }
    }
}
