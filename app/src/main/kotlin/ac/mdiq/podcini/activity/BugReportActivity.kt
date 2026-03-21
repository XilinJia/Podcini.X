package ac.mdiq.podcini.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.developerEmail
import ac.mdiq.podcini.config.settings.githubAddress
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.internalDir
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.error.CrashReportWriter
import ac.mdiq.podcini.utils.openInBrowser
import ac.mdiq.podcini.utils.toastMassege
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat.enableEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class BugReportActivity : ComponentActivity() {
    private var crashDetailsTextView by mutableStateOf("")
    private var showConfirmExport = mutableStateOf(false)
    private val systemInfo: String
        get() = """
                 ## Environment
                 Android version: ${Build.VERSION.RELEASE}
                 OS version: ${System.getProperty("os.version")}
                 Podcini version: ${BuildConfig.VERSION_NAME}
                 Model: ${Build.MODEL}
                 Device: ${Build.DEVICE}
                 Product: ${Build.PRODUCT}
                 """.trimIndent()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.requestFeature(Window.FEATURE_ACTION_MODE_OVERLAY)
        enableEdgeToEdge(window)

        var stacktrace = "No crash report recorded"
        val crashFile = CrashReportWriter.crashLogFile1
        runBlocking {
            if (crashFile.exists()) stacktrace = crashFile.readString()
            else Logd(TAG, stacktrace)
        }

        crashDetailsTextView = """
            $systemInfo
            
            $stacktrace
            """.trimIndent()

        setContent { PodciniTheme { MainView() } }
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 5.dp).verticalScroll(rememberScrollState())) {
                if (toastMassege.isNotEmpty()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                ComfirmDialog(0, stringResource(R.string.confirm_export_log_dialog_message), showConfirmExport) {
                    runOnIOScope { exportLog() }
                    showConfirmExport.value = false
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { openInBrowser("${githubAddress}/issues") }) { Text(stringResource(R.string.open_bug_tracker)) }
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.bug_report_title), crashDetailsTextView)
                    clipboard.setPrimaryClip(clip)
                    Logd(TAG, "Build.VERSION.SDK_INT: ${Build.VERSION.SDK_INT}")
                    Logt(TAG, getString(R.string.copied_to_clipboard))
                }) { Text(stringResource(R.string.copy_to_clipboard)) }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { sendEmail() }) { Text(stringResource(R.string.email_developer)) }
                Text(crashDetailsTextView, color = textColor)
            }
        }
    }

    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = { Text(stringResource(R.string.bug_report_title)) }, navigationIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "", modifier = Modifier.padding(7.dp).clickable { finish() })  },
                actions = {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_logs_menu_title)) }, onClick = {
                            showConfirmExport.value = true
                            expanded = false
                        })
                    }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    private suspend fun exportLog() {
        withContext(Dispatchers.IO) {
            try {
                val logfile = internalDir / "full-logs.txt"
                logfile.sink().use { }
                val process = Runtime.getRuntime().exec("logcat -d -f ${logfile.absPath}")
                process.waitFor()
                val authority = getString(R.string.provider_authority)
                val fileUri = FileProvider.getUriForFile(this@BugReportActivity, authority, java.io.File(logfile.absPath))
                IntentBuilder(this@BugReportActivity).setType("text/*").addStream(fileUri).setChooserTitle(R.string.share_file_label).startChooser()
            } catch (e: Throwable) { Logs(TAG, e, "Can't export logcat") }
        }
    }

    private fun sendEmail() {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Podcini issue")
            putExtra(Intent.EXTRA_TEXT, crashDetailsTextView)
            type = "message/rfc822"
        }
        if (emailIntent.resolveActivity(packageManager) != null) startActivity(emailIntent)
        else Logt(TAG, getString(R.string.need_email_client))
    }
    companion object {
        private val TAG: String = BugReportActivity::class.simpleName ?: "Anonymous"
    }
}
