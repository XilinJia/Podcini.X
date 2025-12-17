package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.getCopyrightNoticeText
import ac.mdiq.podcini.preferences.githubAddress
import ac.mdiq.podcini.preferences.screens.ImportExportPreferencesScreen
import ac.mdiq.podcini.preferences.screens.NetworkScreen
import ac.mdiq.podcini.preferences.screens.PlaybackPreferencesScreen
import ac.mdiq.podcini.preferences.screens.UIPreferencesScreen
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.openInBrowser
import ac.mdiq.podcini.utils.toastMassege
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

class PreferenceActivity : ComponentActivity() {
    val TAG = "PreferenceActivity"
    private var copyrightNoticeText by mutableStateOf("")
    private var topAppBarTitle by mutableStateOf("Home")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(getNoTitleTheme(this))
        super.onCreate(savedInstanceState)
        Logd("PreferenceActivity", "onCreate")

        copyrightNoticeText = getCopyrightNoticeText(this)
        setContent {
            val navController = rememberNavController()
            CustomTheme(this) {
                if (toastMassege.isNotEmpty()) CustomToast(message = toastMassege, onDismiss = { toastMassege = "" })
                if (commonConfirm != null) CommonConfirmDialog(commonConfirm!!)
                BackHandler(enabled = true) {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                    else finish()
                }
                Scaffold(topBar = { TopAppBar(title = { Text(topAppBarTitle) },
                    navigationIcon = { IconButton(onClick = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else finish()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = Screens.Main.name, Modifier.padding(innerPadding)) {
                        composable(Screens.Main.name) {
                            topAppBarTitle = stringResource(Screens.Main.titleRes)
                            MainPreferencesScreen(navController) }
                        composable(Screens.InterfaceScreen.name) {
                            topAppBarTitle = stringResource(Screens.InterfaceScreen.titleRes)
                            UIPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.DownloadScreen.name) {
                            topAppBarTitle = stringResource(Screens.DownloadScreen.titleRes)
                            NetworkScreen(this@PreferenceActivity) }
                        composable(Screens.ImportExportScreen.name) {
                            topAppBarTitle = stringResource(Screens.ImportExportScreen.titleRes)
                            ImportExportPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.PlaybackScreen.name) {
                            topAppBarTitle = stringResource(Screens.PlaybackScreen.titleRes)
                            PlaybackPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.NotificationScreen.name) {
                            topAppBarTitle = stringResource(Screens.NotificationScreen.titleRes)
                            NotificationPreferencesScreen() }
                        composable(Screens.AboutScreen.name) {
                            topAppBarTitle = stringResource(Screens.AboutScreen.titleRes)
                            this@PreferenceActivity.AboutScreen(navController)
                        }
                        composable(Screens.LicensesScreen.name) {
                            topAppBarTitle = stringResource(Screens.LicensesScreen.titleRes)
                            this@PreferenceActivity.LicensesScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        procFlowEvents()
    }

    override fun onStop() {
        super.onStop()
        cancelFlowEvents()
    }

    private var eventSink: Job?     = null
    private fun cancelFlowEvents() {
        eventSink?.cancel()
        eventSink = null
    }
    private fun procFlowEvents() {
        if (eventSink != null) return
        eventSink = lifecycleScope.launch {
            EventFlow.events.collectLatest { event ->
                Logd("PreferenceActivity", "Received event: ${event.TAG}")
                when (event) {
                    is FlowEvent.MessageEvent -> {
                        commonConfirm = CommonConfirmAttrib(
                            title = event.message,
                            message = event.actionText ?: "",
                            confirmRes = R.string.confirm_label,
                            cancelRes = R.string.no,
                            onConfirm = { event.action?.invoke(this@PreferenceActivity) })
                    }
                    else -> {}
                }
            }
        }
    }

    @Composable
    fun MainPreferencesScreen(navController: NavController) {
        @Composable
        fun IconTitleSummaryScreenRow(vecRes: Int, titleRes: Int, summaryRes: Int, screen: String) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = { navController.navigate(screen) })) {
                    Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(stringResource(summaryRes), color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        @Composable
        fun IconTitleActionRow(vecRes: Int, titleRes: Int, callback: ()-> Unit) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Column(modifier = Modifier.weight(1f).clickable(onClick = { callback() })) {
                    Text(stringResource(titleRes), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                }
            }
        }
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp).verticalScroll(rememberScrollState())) {
            if (copyrightNoticeText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = "", tint = Color.Red, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                    Text(copyrightNoticeText, color = textColor)
                }
            }
            IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, Screens.InterfaceScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, Screens.PlaybackScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.network_pref, R.string.downloads_pref_sum, Screens.DownloadScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, Screens.ImportExportScreen.name)
            IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) { navController.navigate(Screens.NotificationScreen.name) }
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
            Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            IconTitleActionRow(R.drawable.ic_questionmark, R.string.documentation_support) { openInBrowser(this@PreferenceActivity, githubAddress) }
            IconTitleActionRow(R.drawable.ic_chat, R.string.visit_user_forum) { openInBrowser(this@PreferenceActivity, "${githubAddress}/discussions") }
            IconTitleActionRow(R.drawable.ic_contribute, R.string.pref_contribute) { openInBrowser(this@PreferenceActivity, githubAddress) }
            IconTitleActionRow(R.drawable.ic_bug, R.string.bug_report_title) { startActivity(Intent(this@PreferenceActivity, BugReportActivity::class.java)) }
            IconTitleActionRow(R.drawable.ic_info, R.string.about_pref) { navController.navigate(Screens.AboutScreen.name) }
        }
    }

    @Composable
    fun AboutScreen(navController: NavController) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp)) {
            Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
                Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
                Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(getString(R.string.bug_report_title), PreferenceManager.getDefaultSharedPreferences(this@PreferenceActivity).getString("about_version", "Default summary"))
                        clipboard.setPrimaryClip(clip)
                        if (Build.VERSION.SDK_INT <= 32) Logt(TAG, getString(R.string.copied_to_clipboard))
                    })) {
                    Text(stringResource(R.string.podcini_version), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(String.format("%s (%s)", BuildConfig.VERSION_NAME, BuildConfig.COMMIT_HASH), color = textColor)
                }
            }
            IconTitleSummaryActionRow(R.drawable.ic_questionmark, R.string.online_help, R.string.online_help_sum) { openInBrowser(this@PreferenceActivity, githubAddress) }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.privacy_policy, R.string.privacy_policy) { openInBrowser(this@PreferenceActivity, "${githubAddress}/blob/main/PrivacyPolicy.md") }
            IconTitleSummaryActionRow(R.drawable.ic_info, R.string.licenses, R.string.licenses_summary) { navController.navigate(Screens.LicensesScreen.name) }
            IconTitleSummaryActionRow(R.drawable.baseline_mail_outline_24, R.string.email_developer, R.string.email_sum) {
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("xilin.vw@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, "Regarding Podcini")
                    type = "message/rfc822"
                }
                if (emailIntent.resolveActivity(packageManager) != null) startActivity(emailIntent)
                else Logt(TAG, getString(R.string.need_email_client))
            }
        }
    }

    @Composable
    fun LicensesScreen() {
        class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
        val licenses = remember { mutableStateListOf<LicenseItem>() }
        LaunchedEffect(Unit) {
            lifecycleScope.launch(Dispatchers.IO) {
                licenses.clear()
                val stream = assets.open("licenses.xml")
                val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
                for (i in 0 until libraryList.length) {
                    val lib = libraryList.item(i).attributes
                    licenses.add(LicenseItem(lib.getNamedItem("name").textContent,
                        String.format("By %s, %s license", lib.getNamedItem("author").textContent, lib.getNamedItem("license").textContent), lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
                }
            }.invokeOnCompletion { throwable -> if (throwable!= null) Logs(TAG, throwable) }
        }
        val lazyListState = rememberLazyListState()
        val textColor = MaterialTheme.colorScheme.onSurface
        val showLicense = remember { mutableStateOf(false) }
        var licenseText by remember { mutableStateOf("") }
        ComfirmDialog(titleRes = 0, message = licenseText, showLicense) {}
        var showDialog by remember { mutableStateOf(false) }
        var curLicenseIndex by remember { mutableIntStateOf(-1) }
        if (showDialog) CommonPopupCard(onDismissRequest = { showDialog = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(licenses[curLicenseIndex].title, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Row {
                    Button(onClick = { openInBrowser(this@PreferenceActivity, licenses[curLicenseIndex].licenseUrl) }) { Text("View website") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        try {
                            val reader = BufferedReader(InputStreamReader(assets.open(licenses[curLicenseIndex].licenseTextFile), "UTF-8"))
                            val sb = StringBuilder()
                            var line = ""
                            while ((reader.readLine()?.also { line = it }) != null) sb.append(line).append("\n")
                            licenseText = sb.toString()
                            showLicense.value = true
                        } catch (e: IOException) { Logs(TAG, e) }
                        //                            showLicenseText(licenses[curLicenseIndex].licenseTextFile)
                    }) { Text("View license") }
                }
            }
        }
        LazyColumn(state = lazyListState, modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(licenses) { index, item ->
                Column(Modifier.clickable(onClick = {
                    curLicenseIndex = index
                    showDialog = true
                })) {
                    Text(item.title, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                    Text(item.subtitle, color = textColor, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    fun NotificationPreferencesScreen() {
        val intent = Intent()
        intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    enum class Screens(val titleRes: Int) {
        Main(R.string.settings_label),
        InterfaceScreen(R.string.user_interface_label),
        PlaybackScreen(R.string.playback_pref),
        DownloadScreen(R.string.network_pref),
        ImportExportScreen(R.string.import_export_pref),
        NotificationScreen(R.string.notification_pref_fragment),
        AboutScreen(R.string.about_pref),
        LicensesScreen(R.string.licenses);
    }
}
