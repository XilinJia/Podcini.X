package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.PreferenceUpgrader.githubAddress
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.storage.utils.StorageUtils.getDataFolder
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.error.CrashReportWriter
import ac.mdiq.podcini.util.toastMassege
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset

class BugReportActivity : ComponentActivity() {
    private var crashDetailsTextView by mutableStateOf("")
    private var showConfirmExport = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getNoTitleTheme(this))
        super.onCreate(savedInstanceState)

        var stacktrace = "No crash report recorded"
        try {
            val crashFile = CrashReportWriter.file
            if (crashFile.exists()) stacktrace = IOUtils.toString(FileInputStream(crashFile), Charset.forName("UTF-8"))
            else Logd(TAG, stacktrace)
        } catch (e: IOException) { Logs(TAG, e) }

        crashDetailsTextView = """
            ${CrashReportWriter.systemInfo}
            
            $stacktrace
            """.trimIndent()

        setContent { CustomTheme(this) { MainView() } }
    }

    @Composable
    fun MainView() {
        val textColor = MaterialTheme.colorScheme.onSurface
        Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 10.dp).verticalScroll(scrollState)) {
                if (toastMassege.isNotEmpty()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                ComfirmDialog(0, stringResource(R.string.confirm_export_log_dialog_message), showConfirmExport) {
                    exportLog()
                    showConfirmExport.value = false
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = { openInBrowser(this@BugReportActivity, "${githubAddress}/issues") }) { Text(stringResource(R.string.open_bug_tracker)) }
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = { Text(stringResource(R.string.bug_report_title)) }, navigationIcon = { IconButton(onClick = { finish() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } }, actions = {
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

    private fun exportLog() {
        try {
            val filename = File(getDataFolder(null), "full-logs.txt")
            val cmd = "logcat -d -f " + filename.absolutePath
            Runtime.getRuntime().exec(cmd)
            //share file
            try {
                val authority = getString(R.string.provider_authority)
                val fileUri = FileProvider.getUriForFile(this, authority, filename)
                IntentBuilder(this).setType("text/*").addStream(fileUri).setChooserTitle(R.string.share_file_label).startChooser()
            } catch (e: Exception) { Logs(TAG, e, getString(R.string.log_file_share_exception)) }
        } catch (e: IOException) { Logs(TAG, e, "Can't export logcat") }
    }

    private fun sendEmail() {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf("xilin.vw@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Podcini issue")
            putExtra(Intent.EXTRA_TEXT, crashDetailsTextView)
            setType("message/rfc822")
        }
        if (emailIntent.resolveActivity(packageManager) != null) startActivity(emailIntent)
        else Logt(TAG, getString(R.string.need_email_client))
    }
    companion object {
        private val TAG: String = BugReportActivity::class.simpleName ?: "Anonymous"
    }
}
