package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.DefaultPages
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.PreferenceUpgrader.getCopyrightNoticeText
import ac.mdiq.podcini.preferences.PreferenceUpgrader.githubAddress
import ac.mdiq.podcini.preferences.ThemeSwitcher.getNoTitleTheme
import ac.mdiq.podcini.preferences.screens.AutoDownloadPreferencesScreen
import ac.mdiq.podcini.preferences.screens.DownloadsPreferencesScreen
import ac.mdiq.podcini.preferences.screens.ImportExportPreferencesScreen
import ac.mdiq.podcini.preferences.screens.PlaybackPreferencesScreen
import ac.mdiq.podcini.preferences.screens.SynchronizationPreferencesScreen
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.CommonConfirmDialog
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.CustomTheme
import ac.mdiq.podcini.ui.compose.CustomToast
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.IntentUtils.openInBrowser
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.toastMassege
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
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
                Scaffold(topBar = { TopAppBar(title = { Text(topAppBarTitle) },
                    navigationIcon = { IconButton(onClick = {
                        if (navController.previousBackStackEntry != null) navController.popBackStack()
                        else onBackPressed()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = Screens.Main.name, Modifier.padding(innerPadding)) {
                        composable(Screens.Main.name) {
                            topAppBarTitle = stringResource(Screens.Main.titleRes)
                            MainPreferencesScreen(navController) }
                        composable(Screens.InterfaceScreen.name) {
                            topAppBarTitle = stringResource(Screens.InterfaceScreen.titleRes)
                            UserInterfacePreferencesScreen() }
                        composable(Screens.DownloadScreen.name) {
                            topAppBarTitle = stringResource(Screens.DownloadScreen.titleRes)
                            DownloadsPreferencesScreen(this@PreferenceActivity, navController) }
                        composable(Screens.ImportExportScreen.name) {
                            topAppBarTitle = stringResource(Screens.ImportExportScreen.titleRes)
                            ImportExportPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.AutoDownloadScreen.name) {
                            topAppBarTitle = stringResource(Screens.AutoDownloadScreen.titleRes)
                            AutoDownloadPreferencesScreen() }
                        composable(Screens.SynchronizationScreen.name) {
                            topAppBarTitle = stringResource(Screens.SynchronizationScreen.titleRes)
                            SynchronizationPreferencesScreen(this@PreferenceActivity) }
                        composable(Screens.PlaybackScreen.name) {
                            topAppBarTitle = stringResource(Screens.PlaybackScreen.titleRes)
                            PlaybackPreferencesScreen() }
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
//            if (supportFragmentManager.backStackEntryCount == 0) finish()
//            else {
//                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
//                var view = currentFocus
//                //If no view currently has focus, create a new one, just so we can grab a window token from it
//                if (view == null) view = View(this)
//                imm.hideSoftInputFromWindow(view.windowToken, 0)
//                supportFragmentManager.popBackStack()
//            }
            return true
        }
        return false
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
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp).verticalScroll(scrollState)) {
            if (copyrightNoticeText.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = "", tint = Color.Red, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                    Text(copyrightNoticeText, color = textColor)
                }
            }
            IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, Screens.InterfaceScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, Screens.PlaybackScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.downloads_pref, R.string.downloads_pref_sum, Screens.DownloadScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_cloud, R.string.synchronization_pref, R.string.synchronization_sum, Screens.SynchronizationScreen.name)
            IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, Screens.ImportExportScreen.name)
            IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) { navController.navigate(Screens.NotificationScreen.name) }
            TitleSummarySwitchPrefRow(R.string.pref_backup_on_google_title, R.string.pref_backup_on_google_sum, AppPrefs.prefOPMLBackup) {
                putPref(AppPrefs.prefOPMLBackup, it)
                val intent = packageManager?.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
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
                    setType("message/rfc822")
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
//        fun showLicenseText(licenseTextFile: String) {
//            try {
//                val reader = BufferedReader(InputStreamReader(assets.open(licenseTextFile), "UTF-8"))
//                val licenseText = StringBuilder()
//                var line = ""
//                while ((reader.readLine()?.also { line = it }) != null) licenseText.append(line).append("\n")
//                MaterialAlertDialogBuilder(this@PreferenceActivity).setMessage(licenseText).show()
//            } catch (e: IOException) { Logs(TAG, e) }
//        }
        val lazyListState = rememberLazyListState()
        val textColor = MaterialTheme.colorScheme.onSurface
        val showLicense = remember { mutableStateOf(false) }
        var licenseText by remember { mutableStateOf("") }
        ComfirmDialog(titleRes = 0, message = licenseText, showLicense) {}
        var showDialog by remember { mutableStateOf(false) }
        var curLicenseIndex by remember { mutableIntStateOf(-1) }
        if (showDialog) Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)) {
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
//                                MaterialAlertDialogBuilder(this@PreferenceActivity).setMessage(licenseText).show()
                            } catch (e: IOException) { Logs(TAG, e) }
//                            showLicenseText(licenses[curLicenseIndex].licenseTextFile)
                        }) { Text("View license") }
                    }
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
    fun UserInterfacePreferencesScreen() {
        val textColor = MaterialTheme.colorScheme.onSurface
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(scrollState)) {
            Text(stringResource(R.string.appearance), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                var checkIndex by remember { mutableIntStateOf(
                    when(AppPreferences.theme) {
                        AppPreferences.ThemePreference.SYSTEM -> 0
                        AppPreferences.ThemePreference.LIGHT -> 1
                        AppPreferences.ThemePreference.DARK -> 2
                        else -> 0
                    }) }
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 0, onClick = {
                    checkIndex = 0
                    AppPreferences.theme = AppPreferences.ThemePreference.SYSTEM
                    recreate()
                })
                Text(stringResource(R.string.pref_theme_title_automatic), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 1, onClick = {
                    checkIndex = 1
                    AppPreferences.theme = AppPreferences.ThemePreference.LIGHT
                    recreate()
                })
                Text(stringResource(R.string.pref_theme_title_light), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                RadioButton(selected = checkIndex == 2, onClick = {
                    checkIndex = 2
                    AppPreferences.theme = AppPreferences.ThemePreference.DARK
                    recreate()
                })
                Text(stringResource(R.string.pref_theme_title_dark), color = textColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
            TitleSummarySwitchPrefRow(R.string.pref_black_theme_title, R.string.pref_black_theme_message, AppPrefs.prefThemeBlack) {
                putPref(AppPrefs.prefThemeBlack, it)
                recreate()
            }
//            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
//                Column(modifier = Modifier.weight(1f)) {
//                    Text(stringResource(R.string.pref_black_theme_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
//                    Text(stringResource(R.string.pref_black_theme_message), color = textColor)
//                }
//                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefThemeBlack, false)) }
//                Switch(checked = isChecked, onCheckedChange = {
//                    isChecked = it
//                    putPref(AppPrefs.prefThemeBlack, it)
//                    recreate()
//                })
//            }
//            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
//                Column(modifier = Modifier.weight(1f)) {
//                    Text(stringResource(R.string.pref_tinted_theme_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
//                    Text(stringResource(R.string.pref_tinted_theme_message), color = textColor)
//                }
//                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefTintedColors, false)) }
//                Switch(checked = isChecked, onCheckedChange = {
//                    isChecked = it
//                    putPref(AppPrefs.prefTintedColors, it)
//                    recreate()
//                })
//            }
            TitleSummarySwitchPrefRow(R.string.pref_episode_cover_title, R.string.pref_episode_cover_summary, AppPrefs.prefEpisodeCover)
            Text(stringResource(R.string.subscriptions_label), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            TitleSummarySwitchPrefRow(R.string.pref_swipe_refresh_title, R.string.pref_swipe_refresh_sum, AppPrefs.prefSwipeToRefreshAll)
            TitleSummarySwitchPrefRow(R.string.pref_feedGridLayout_title, R.string.pref_feedGridLayout_sum, AppPrefs.prefFeedGridLayout)
            Text(stringResource(R.string.external_elements), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            TitleSummarySwitchPrefRow(R.string.pref_show_notification_skip_title, R.string.pref_show_notification_skip_sum, AppPrefs.prefShowSkip)
            Text(stringResource(R.string.behavior), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
            var showDefaultPageOptions by remember { mutableStateOf(false) }
            var tempSelectedOption by remember { mutableStateOf(getPref(AppPrefs.prefDefaultPage, DefaultPages.Subscriptions.name)) }
            TitleSummaryActionColumn(R.string.pref_default_page, R.string.pref_default_page_sum) { showDefaultPageOptions = true }
            if (showDefaultPageOptions) {
                AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showDefaultPageOptions = false },
                    title = { Text(stringResource(R.string.pref_default_page), style = CustomTextStyles.titleCustom) },
                    text = {
                        Column {
                            DefaultPages.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tempSelectedOption = option.name }) {
                                    Checkbox(checked = tempSelectedOption == option.name, onCheckedChange = { tempSelectedOption = option.name })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            putPref(AppPrefs.prefDefaultPage, tempSelectedOption)
                            showDefaultPageOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDefaultPageOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_back_button_opens_drawer, R.string.pref_back_button_opens_drawer_summary, AppPrefs.prefBackButtonOpensDrawer)
            TitleSummarySwitchPrefRow(R.string.pref_show_error_toasts, R.string.pref_show_error_toasts_sum, AppPrefs.prefShowErrorToasts)
            TitleSummarySwitchPrefRow(R.string.pref_print_logs, R.string.pref_print_logs_sum, AppPrefs.prefPrintDebugLogs)
        }
    }

    @Composable
    fun NotificationPreferencesScreen() {
        val intent = Intent()
        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    enum class Screens(val titleRes: Int) {
        Main(R.string.settings_label),
        InterfaceScreen(R.string.user_interface_label),
        PlaybackScreen(R.string.playback_pref),
        DownloadScreen(R.string.downloads_pref),
        SynchronizationScreen(R.string.synchronization_pref),
        ImportExportScreen(R.string.import_export_pref),
        NotificationScreen(R.string.notification_pref_fragment),
        AutoDownloadScreen(R.string.pref_automatic_download_title),
        AboutScreen(R.string.about_pref),
        LicensesScreen(R.string.licenses);
    }
}
