package ac.mdiq.podcini.ui.compose

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.util.Logd
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedItem: String, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var currentSelectedItem by remember { mutableStateOf(selectedItem) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Spinner(items: List<String>, selectedIndex: Int, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var curIndex by remember { mutableIntStateOf(selectedIndex) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = items.getOrNull(curIndex) ?: "Select Item", onValueChange = { },
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize, fontWeight = FontWeight.Bold),
            modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true), // Material3 requirement
            decorationBox = { innerTextField ->
                Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                    innerTextField()
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            })
        ExposedDropdownMenu(modifier = Modifier.heightIn(max = 340.dp), expanded = expanded, onDismissRequest = { expanded = false }) {
            for (i in items.indices) {
                DropdownMenuItem(text = { Text(items[i]) },
                    onClick = {
                        curIndex = i
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerExternalSet(items: List<String>, selectedIndex: Int, modifier: Modifier = Modifier, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onExpandedChange = { expanded = it }) {
        BasicTextField(readOnly = true, value = items.getOrNull(selectedIndex) ?: "Select Item", onValueChange = { },
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
                        onItemSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

//@Composable
//fun CustomToast(message: String, durationMillis: Long = 2000L, onDismiss: () -> Unit) {
//    LaunchedEffect(message) {
//        delay(durationMillis)
//        onDismiss()
//    }
//    Popup(alignment = Alignment.Center, onDismissRequest = { onDismiss() }) {
//        val color = if (message.contains("Error", ignoreCase = true)) Color.Red else MaterialTheme.colorScheme.onSecondary
//        Box(modifier = Modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp)).padding(8.dp)) {
//            Text(text = message, color = color, style = MaterialTheme.typography.bodyMedium)
//        }
//    }
//}

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
            val color = if (message.contains("Error", ignoreCase = true)) Color.Red else MaterialTheme.colorScheme.onSecondary
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
    Column(modifier = modifier) {
        var rows = (itemCount / columns)
        if (itemCount.mod(columns) > 0) rows += 1
        for (rowId in 0 until rows) {
            val firstIndex = rowId * columns
            Row {
                for (columnId in 0 until columns) {
                    val index = firstIndex + columnId
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { if (index < itemCount) content(index) }
                }
            }
        }
    }
}

@Composable
fun SimpleSwitchDialog(title: String, text: String, onDismissRequest: ()->Unit, callback: (Boolean)-> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    var isChecked by remember { mutableStateOf(false) }
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
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
    AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { commonConfirm = null },
        title = { Text(c.title) },
        text = {
            Column {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) { Text(c.message) }
                if (c.neutralRes > 0) TextButton(onClick = {
                    c.onNeutral?.invoke()
                    commonConfirm = null
                }) { Text(stringResource(c.neutralRes)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.onConfirm()
                commonConfirm = null
            }) { Text(stringResource(c.confirmRes)) }
        },
        dismissButton = { TextButton(onClick = {
            c.onCancel()
            commonConfirm = null
        }) { Text(stringResource(c.cancelRes)) } }
    )
}

@Composable
fun ComfirmDialog(titleRes: Int, message: String, showDialog: MutableState<Boolean>, cancellable: Boolean = true, onConfirm: () -> Unit) {
    if (showDialog.value) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showDialog.value = false },
            title = { if (titleRes != 0) Text(stringResource(titleRes)) },
            text = {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) { Text(message) }
            },
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
fun SearchBarRow(hintTextRes: Int, defaultText: String, performSearch: (String) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Logd("SearchBarRow", "defaultText: $defaultText")
        var queryText by remember { mutableStateOf(defaultText) }
        TextField(value = queryText, onValueChange = { queryText = it },
            textStyle = TextStyle(fontSize = 16.sp), label = { Text(stringResource(hintTextRes)) },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { performSearch(queryText) }), modifier = Modifier.weight(1f))
        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_search), tint = textColor, contentDescription = "right_action_icon",
            modifier = Modifier.width(40.dp).height(40.dp).padding(start = 5.dp).clickable(onClick = { performSearch(queryText) }))
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
    TextField(value = inputVal, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), label = { Text(label) }, singleLine = true, modifier = modifier,
        onValueChange = {
            if (it.isEmpty() || it.toIntOrNull() != null) inputVal = it
            if (it.toIntOrNull() != null) showSet = true
            if (instant && showSet) set()
        },
        trailingIcon = { if (!instant && showSet) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon", modifier = Modifier.size(30.dp).padding(start = 10.dp).clickable(onClick = { set() })) })
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
