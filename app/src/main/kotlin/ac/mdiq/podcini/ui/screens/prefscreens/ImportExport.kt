package ac.mdiq.podcini.ui.screens.prefscreens

import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.DatabaseTransporter
import ac.mdiq.podcini.config.settings.DocumentFileExportWorker
import ac.mdiq.podcini.config.settings.EpisodeProgressReader
import ac.mdiq.podcini.config.settings.EpisodesProgressWriter
import ac.mdiq.podcini.config.settings.ExportTypes
import ac.mdiq.podcini.config.settings.ExportWorker
import ac.mdiq.podcini.config.settings.ExportWriter
import ac.mdiq.podcini.config.settings.FavoritesWriter
import ac.mdiq.podcini.config.settings.HtmlWriter
import ac.mdiq.podcini.config.settings.MediaFilesTransporter
import ac.mdiq.podcini.config.settings.OpmlTransporter
import ac.mdiq.podcini.config.settings.OpmlTransporter.OpmlElement
import ac.mdiq.podcini.config.settings.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.config.settings.PreferencesTransporter
import ac.mdiq.podcini.config.settings.autoBackupDirName
import ac.mdiq.podcini.config.settings.importAP
import ac.mdiq.podcini.config.settings.importPA
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.OpmlImportSelectionDialog
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchRow
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.dateStampFilename
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

@Composable
fun ImportExportScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    val TAG = "ImportExportScreen"
    val backupDirName = "Podcini-Backups"
    val prefsDirName = "Podcini-Prefs"
    val mediaFilesDirName = "Podcini-MediaFiles"

    var showProgress by remember { mutableStateOf(false) }
    fun isJsonFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.endsWith(".json", ignoreCase = true)
    }
    fun isRealmFile(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.trim().endsWith(".realm", ignoreCase = true)
    }
    fun isComboDir(uri: Uri): Boolean {
        val fileName = uri.lastPathSegment ?: return false
        return fileName.contains(backupDirName, ignoreCase = true) || fileName.contains(autoBackupDirName, ignoreCase = true)
    }
    fun showExportSuccess(uri: Uri?, mimeType: String?) {
        commonConfirm = CommonConfirmAttrib(
            title = context.getString(R.string.export_success_title),
            message = "",
            confirmRes = R.string.share_label,
            cancelRes = R.string.no,
            onConfirm = { IntentBuilder(context).setType(mimeType).addStream(uri!!).setChooserTitle(R.string.share_label).startChooser() })
    }
    val showImporSuccessDialog = remember { mutableStateOf(false) }
    ComfirmDialog(titleRes = R.string.successful_import_label, message = stringResource(R.string.import_ok), showDialog = showImporSuccessDialog, cancellable = false) { forceRestart() }

    val showImporErrortDialog = remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    ComfirmDialog(titleRes = R.string.import_export_error_label, message = importErrorMessage, showDialog = showImporErrortDialog) {}

    fun exportWithWriter(exportWriter: ExportWriter, uri: Uri?, exportType: ExportTypes) {
        val context = getAppContext()
        showProgress = true
        if (uri == null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val output = ExportWorker(exportWriter).exportFile()
                    withContext(Dispatchers.Main) {
                        val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority), output!!)
                        showExportSuccess(fileUri, exportType.contentType)
                    }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val worker = DocumentFileExportWorker(exportWriter, uri)
                try {
                    val output = worker.exportFile()
                    withContext(Dispatchers.Main) { showExportSuccess(output.uri, exportType.contentType) }
                } catch (e: Exception) {
                    showProgress = false
                    importErrorMessage = e.message?:"Reason unknown"
                    showImporErrortDialog.value = true
                } finally { showProgress = false }
            }
        }
    }

    val chooseOpmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(OpmlWriter(), uri, ExportTypes.OPML)
    }
    val chooseHtmlExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(HtmlWriter(), uri, ExportTypes.HTML)
    }
    val chooseFavoritesExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(FavoritesWriter(), uri, ExportTypes.FAVORITES)
    }
    val chooseProgressExportPathLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        exportWithWriter(EpisodesProgressWriter(), uri, ExportTypes.PROGRESS)
    }
    val restoreProgressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data
        uri?.let {
            if (isJsonFile(uri)) {
                showProgress = true
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val inputStream: InputStream? = getAppContext().contentResolver.openInputStream(uri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            EpisodeProgressReader().readDocument(reader)
                            reader.close()
                        }
                        withContext(Dispatchers.Main) {
                            showImporSuccessDialog.value = true
//                                showImportSuccessDialog()
                            showProgress = false
                        }
                    } catch (e: Throwable) {
                        showProgress = false
                        importErrorMessage = e.message?:"Reason unknown"
                        showImporErrortDialog.value = true
                    }
                }
            } else {
                val message = context.getString(R.string.import_file_type_toast) + ".json"
                showProgress = false
                importErrorMessage = message
                showImporErrortDialog.value = true
            }
        }
    }
    var showOpmlImportSelectionDialog by remember { mutableStateOf(false) }
    val readElements = remember { mutableStateListOf<OpmlElement>() }
    val chooseOpmlImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Logd(TAG, "chooseOpmlImportPathResult: uri: $uri")
        OpmlTransporter.startImport(uri) {
            readElements.addAll(it)
            Logd(TAG, "readElements: ${readElements.size}")
        }
        showOpmlImportSelectionDialog = true
    }

    var comboRootUri by remember { mutableStateOf<Uri?>(null) }
    val comboDic = remember { mutableStateMapOf<String, Boolean>() }
    var showComboImportDialog by remember { mutableStateOf(false) }
    if (showComboImportDialog) {
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showComboImportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        if (option != "Media files" || comboDic["Database"] != true) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = {
                                comboDic[option] = it
                                if (option == "Database" && it) comboDic["Media files"] = false
                            })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (comboDic["Media files"] != null && comboDic["Database"] == true) Text(stringResource(R.string.pref_import_media_files_later), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val rootFile = DocumentFile.fromTreeUri(getAppContext(), uri)
                                if (rootFile != null && rootFile.isDirectory) {
                                    Logd(TAG, "comboDic[\"Preferences\"] ${comboDic["Preferences"]}")
                                    Logd(TAG, "comboDic[\"Media files\"] ${comboDic["Media files"]}")
                                    Logd(TAG, "comboDic[\"Database\"] ${comboDic["Database"]}")
                                    for (child in rootFile.listFiles()) {
                                        if (child.isDirectory) {
                                            if (child.name == prefsDirName) {
                                                if (comboDic["Preferences"] == true) PreferencesTransporter(prefsDirName).importBackup(child.uri)
                                            } else if (child.name == mediaFilesDirName) {
                                                if (comboDic["Media files"] == true) MediaFilesTransporter(mediaFilesDirName).importFromUri(child.uri)
                                            }
                                        } else if (isRealmFile(child.uri) && comboDic["Database"] == true) DatabaseTransporter().importBackup(child.uri)
                                    }
                                }
                            }
                            withContext(Dispatchers.Main) {
                                showImporSuccessDialog.value = true
                                showProgress = false
                            }
                        } catch (e: Throwable) {
                            showProgress = false
                            importErrorMessage = e.message?:"Reason unknown"
                            showImporErrortDialog.value = true
                        }
                    }
                    showComboImportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboImportDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }
    var showComboExportDialog by remember { mutableStateOf(false) }
    if (showComboExportDialog) {
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showComboExportDialog = false },
            title = { Text(stringResource(R.string.pref_select_properties), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    comboDic.keys.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = comboDic[option] == true, onCheckedChange = { comboDic[option] = it })
                            Text(option, modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = comboRootUri!!
                    showProgress = true
                    CoroutineScope(Dispatchers.IO).launch {
                        withContext(Dispatchers.IO) {
                            val chosenDir = DocumentFile.fromTreeUri(getAppContext(), uri) ?: throw IOException("Destination directory is not valid")
                            val exportSubDir = chosenDir.createDirectory(dateStampFilename("$backupDirName-%s")) ?: throw IOException("Error creating subdirectory $backupDirName")
                            val subUri: Uri = exportSubDir.uri
                            if (comboDic["Preferences"] == true) PreferencesTransporter(prefsDirName).exportToDocument(subUri)
                            if (comboDic["Media files"] == true) MediaFilesTransporter(mediaFilesDirName).exportToUri(subUri)
                            if (comboDic["Database"] == true) {
                                val realmFile = exportSubDir.createFile("application/octet-stream", "backup.realm")
                                if (realmFile != null) DatabaseTransporter().exportToDocument(realmFile.uri)
                            }
                        }
                        withContext(Dispatchers.Main) { showProgress = false }
                    }
                    showComboExportDialog = false
                }) { Text(text = "OK") }
            },
            dismissButton = { TextButton(onClick = { showComboExportDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var backupFolder by remember { mutableStateOf(appPrefs.autoBackupFolder ?: context.getString(R.string.pref_auto_backup_folder_sum)) }
    val selectAutoBackupDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                getAppContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                backupFolder = uri.toString()
                upsertBlk(appPrefs) { p-> p.autoBackupFolder = uri.toString() }
            }
        }
    }

    val restoreComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK || result.data?.data == null) return@rememberLauncherForActivityResult
        val uri = result.data!!.data!!
        if (isComboDir(uri)) {
            val rootFile = DocumentFile.fromTreeUri(getAppContext(), uri)
            if (rootFile != null && rootFile.isDirectory) {
                comboDic.clear()
                for (child in rootFile.listFiles()) {
                    Logd(TAG, "restoreComboLauncher child: ${child.isDirectory} ${child.name} ${child.uri} ")
                    if (child.isDirectory) {
                        if (child.name == prefsDirName) comboDic["Preferences"] = true
                        else if (child.name == mediaFilesDirName) comboDic["Media files"] = false
                    } else if (isRealmFile(child.uri)) comboDic["Database"] = true
                }
            }
            comboRootUri = uri
            showComboImportDialog = true
        } else {
            val message = context.getString(R.string.import_directory_toast) + backupDirName + " or " + autoBackupDirName
            showProgress = false
            importErrorMessage = message
            showImporErrortDialog.value = true
        }
    }
    val backupComboLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                comboDic.clear()
                comboDic["Database"] = true
                comboDic["Preferences"] = true
                comboDic["Media files"] = true
                comboRootUri = uri
                showComboExportDialog = true
            }
        }
    }

    val chooseAPImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            showProgress = true
            importAP(uri) {
                showImporSuccessDialog.value = true
                showProgress = false
            }
        } }

    var importPADB by remember { mutableStateOf(false) }
    var importPADirectory by remember { mutableStateOf(false) }
    val choosePAImportPathLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            showProgress = true
            CoroutineScope(Dispatchers.IO).launch {
                if (importPADB) importPA(uri, true, importPADirectory) {}
                showImporSuccessDialog.value = true
                showProgress = false
            }
        } }

    fun launchExportCombos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        backupComboLauncher.launch(intent)
    }

    fun openExportPathPicker(exportType: ExportTypes, result: ActivityResultLauncher<Intent>, writer: ExportWriter) {
        val title = dateStampFilename(exportType.outputNameTemplate)
        val intentPickAction = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(exportType.contentType)
            .putExtra(Intent.EXTRA_TITLE, title)
        try {
            result.launch(intentPickAction)
            return
        } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
        // If we are using an SDK lower than API 21 or the implicit intent failed fallback to the legacy export process
        exportWithWriter(writer, null, exportType)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    if (showProgress) {
        CommonPopupCard(onDismissRequest = { showProgress = false }) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.TopCenter))
                Text("Loading...", color = textColor, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        TitleSummarySwitchRow(R.string.pref_backup_on_google_title, R.string.pref_backup_on_google_sum, appPrefs.OPMLBackup) {
            upsertBlk(appPrefs) { p -> p.OPMLBackup = it}
            val intent = context.packageManager?.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        var isAutoBackup by remember { mutableStateOf(appPrefs.autoBackup) }
        TitleSummarySwitchRow(R.string.pref_auto_backup_title, R.string.pref_auto_backup_sum, appPrefs.autoBackup) {
            isAutoBackup = it
            upsertBlk(appPrefs) { p -> p.autoBackup = it}
        }
        if (isAutoBackup) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(stringResource(R.string.pref_auto_backup_interval), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var interval by remember { mutableStateOf(appPrefs.autoBackupIntervall.toString()) }
                NumberEditor(interval.toInt(), label = "hours", nz = false, modifier = Modifier.weight(0.5f)) {
                    interval = it.toString()
                    upsertBlk(appPrefs) { p-> p.autoBackupIntervall = interval.toIntOrNull()?:0 }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Text(stringResource(R.string.pref_auto_backup_limit), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                var count by remember { mutableStateOf(appPrefs.autoBackupLimit.toString()) }
                var showIcon by remember { mutableStateOf(false) }
                TextField(value = count, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.weight(0.4f),  label = { Text("1 - 9") },
                    onValueChange = {
                        val intVal = it.toIntOrNull()
                        if (it.isEmpty() || (intVal != null && intVal>0 && intVal<10)) {
                            count = it
                            showIcon = true
                        }
                    },
                    trailingIcon = {
                        if (showIcon) Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings icon",
                            modifier = Modifier.size(30.dp).padding(start = 5.dp).clickable(onClick = {
                                if (count.isEmpty()) count = "0"
                                upsertBlk(appPrefs) { p-> p.autoBackupLimit = count.toIntOrNull()?:0 }
                                showIcon = false
                            }))
                    })
            }
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp).clickable(onClick = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addCategory(Intent.CATEGORY_DEFAULT)
                selectAutoBackupDirLauncher.launch(intent)
            })) {
                Text(stringResource(R.string.pref_auto_backup_folder), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(backupFolder, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.combo_export_label, R.string.combo_export_summary) { launchExportCombos() }
        val showComboImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.combo_import_label, message = stringResource(R.string.combo_import_warning), showDialog = showComboImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            restoreComboLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.combo_import_label, R.string.combo_import_summary) { showComboImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        val showAPImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.import_AP_label, message = stringResource(R.string.import_SQLite_message), showDialog = showAPImportDialog) {
            try { chooseAPImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
        }
        TitleSummaryActionColumn(R.string.import_AP_label, 0) { showAPImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        val showPAImportDialog = remember { mutableStateOf(false) }
        if (showPAImportDialog.value) {
            AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showPAImportDialog.value = false },
                title = { Text(stringResource(R.string.import_PA_label)) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importPADB, onCheckedChange = { importPADB = it })
                            Text(text = stringResource(R.string.import_PA_DB_label), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                        }
                        Text(stringResource(R.string.import_PA_DB_message), color = textColor, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importPADirectory, onCheckedChange = { importPADirectory = it })
                            Text(text = stringResource(R.string.import_PA_directory_label), style = MaterialTheme.typography.bodyLarge.merge(), modifier = Modifier.padding(start = 10.dp))
                        }
                        Text(stringResource(R.string.import_PA_directory_message), color = textColor, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.import_PA_message), color = textColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        try { choosePAImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") }
                        showPAImportDialog.value = false
                    }) { Text(stringResource(R.string.confirm_label)) }
                },
                dismissButton = { TextButton(onClick = { showPAImportDialog.value = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        TitleSummaryActionColumn(R.string.import_PA_label, 0) { showPAImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.opml_export_label, R.string.opml_export_summary) { openExportPathPicker(ExportTypes.OPML, chooseOpmlExportPathLauncher, OpmlWriter()) }
        if (showOpmlImportSelectionDialog) OpmlImportSelectionDialog(readElements) { showOpmlImportSelectionDialog = false }
        TitleSummaryActionColumn(R.string.opml_import_label, R.string.opml_import_summary) {
            try { chooseOpmlImportPathLauncher.launch("*/*") } catch (e: ActivityNotFoundException) { Logs(TAG, e, "No activity found. Should never happen...") } }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.progress_export_label, R.string.progress_export_summary) { openExportPathPicker(ExportTypes.PROGRESS, chooseProgressExportPathLauncher, EpisodesProgressWriter()) }
        val showProgressImportDialog = remember { mutableStateOf(false) }
        ComfirmDialog(titleRes = R.string.progress_import_label, message = stringResource(R.string.progress_import_warning), showDialog = showProgressImportDialog) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            restoreProgressLauncher.launch(intent)
        }
        TitleSummaryActionColumn(R.string.progress_import_label, R.string.progress_import_summary) { showProgressImportDialog.value = true }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        TitleSummaryActionColumn(R.string.html_export_label, R.string.html_export_summary) { openExportPathPicker(ExportTypes.HTML, chooseHtmlExportPathLauncher, HtmlWriter()) }
        TitleSummaryActionColumn(R.string.favorites_export_label, R.string.favorites_export_summary) { openExportPathPicker(ExportTypes.FAVORITES, chooseFavoritesExportPathLauncher, FavoritesWriter()) }
    }
}
