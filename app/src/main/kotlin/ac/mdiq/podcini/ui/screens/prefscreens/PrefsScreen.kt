package ac.mdiq.podcini.ui.screens.prefscreens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.developerEmail
import ac.mdiq.podcini.config.settings.getCopyrightNoticeText
import ac.mdiq.podcini.config.settings.githubAddress
import ac.mdiq.podcini.activity.BugReportActivity
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.screens.LocalDrawerController
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.openInBrowser
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

private val TAG = "PrefsMainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsScreen() {
    val navController = rememberNavController()
    var topAppBarTitle by remember { mutableStateOf("Home") }
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner" }
    val drawerController = LocalDrawerController.current

    Scaffold(topBar = { TopAppBar(title = { Text(topAppBarTitle) },
        navigationIcon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "Back", modifier = Modifier.padding(7.dp).clickable(onClick = {
            if (navController.previousBackStackEntry != null) navController.popBackStack()
            else drawerController?.open()
        }) ) }) }) { innerPadding ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
            NavHost(navController = navController, startDestination = PrefScreens.PortalScreen.name, Modifier.padding(innerPadding)) {
                composable(PrefScreens.PortalScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.PortalScreen.titleRes)
                    PrefPortalScreen(navController)
                }
                composable(PrefScreens.InterfaceScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.InterfaceScreen.titleRes)
                    UserInterfaceScreen()
                }
                composable(PrefScreens.DownloadScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.DownloadScreen.titleRes)
                    NetworkScreen()
                }
                composable(PrefScreens.ImportExportScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.ImportExportScreen.titleRes)
                    ImportExportScreen()
                }
                composable(PrefScreens.PlaybackScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.PlaybackScreen.titleRes)
                    PlaybackScreen()
                }
                composable(PrefScreens.NotificationScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.NotificationScreen.titleRes)
                    NotificationPrefScreen()
                }
                composable(PrefScreens.AboutScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.AboutScreen.titleRes)
                    AboutScreen(navController)
                }
                composable(PrefScreens.LicensesScreen.name) {
                    topAppBarTitle = stringResource(PrefScreens.LicensesScreen.titleRes)
                    LicensesScreen()
                }
            }
        }
    }
}

@Composable
fun PrefPortalScreen(navController: NavController) {
    val context by rememberUpdatedState(LocalContext.current)
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
    Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        val copyrightNoticeText by remember { mutableStateOf(getCopyrightNoticeText()) }
        if (copyrightNoticeText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = "", tint = Color.Red, modifier = Modifier.size(40.dp).padding(end = 15.dp))
                Text(copyrightNoticeText, color = textColor)
            }
        }
        IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, PrefScreens.InterfaceScreen.name)
        IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, PrefScreens.PlaybackScreen.name)
        IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.network_pref, R.string.downloads_pref_sum, PrefScreens.DownloadScreen.name)
        IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, PrefScreens.ImportExportScreen.name)
        IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) { navController.navigate(PrefScreens.NotificationScreen.name) }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        IconTitleActionRow(R.drawable.ic_questionmark, R.string.whats_new) { openInBrowser("${githubAddress}/blob/main/changelog.md") }
        IconTitleActionRow(R.drawable.ic_questionmark, R.string.documentation_support) { openInBrowser(githubAddress) }
        IconTitleActionRow(R.drawable.ic_contribute, R.string.pref_contribute) { openInBrowser(githubAddress) }
        IconTitleActionRow(R.drawable.ic_bug, R.string.bug_report_title) { context.startActivity(Intent(context, BugReportActivity::class.java)) }
        IconTitleActionRow(R.drawable.ic_info, R.string.about_pref) { navController.navigate(PrefScreens.AboutScreen.name) }
    }
}

@Composable
fun AboutScreen(navController: NavController) {
    val context by rememberUpdatedState(LocalContext.current)
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).background(MaterialTheme.colorScheme.surface)) {
        Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
            Column(Modifier.padding(start = 10.dp).clickable(onClick = {
                val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//                val clip = ClipData.newPlainText(context.getString(R.string.bug_report_title), PreferenceManager.getDefaultSharedPreferences(context).getString("about_version", "Default summary"))
                val versionText = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                val clip = ClipData.newPlainText(context.getString(R.string.bug_report_title), versionText)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= 32) Logt(TAG, context.getString(R.string.copied_to_clipboard))
            })) {
                Text(stringResource(R.string.podcini_version), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(String.format("%s", BuildConfig.VERSION_NAME), color = textColor)
            }
        }
        IconTitleSummaryActionRow(R.drawable.ic_questionmark, R.string.online_help, R.string.online_help_sum) { openInBrowser(githubAddress) }
        IconTitleSummaryActionRow(R.drawable.ic_info, R.string.privacy_policy, R.string.privacy_policy) { openInBrowser("${githubAddress}/blob/main/PrivacyPolicy.md") }
        IconTitleSummaryActionRow(R.drawable.ic_info, R.string.licenses, R.string.licenses_summary) { navController.navigate(PrefScreens.LicensesScreen.name) }
        IconTitleSummaryActionRow(R.drawable.baseline_mail_outline_24, R.string.email_developer, R.string.email_sum) {
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail))
                putExtra(Intent.EXTRA_SUBJECT, "Regarding Podcini")
                type = "message/rfc822"
            }
            if (emailIntent.resolveActivity(context.packageManager) != null) context.startActivity(emailIntent)
            else Logt(TAG, context.getString(R.string.need_email_client))
        }
    }
}

@Composable
fun LicensesScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    val scope = rememberCoroutineScope()
    class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
    val licenses = remember { mutableStateListOf<LicenseItem>() }
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            licenses.clear()
            val stream = context.assets.open("licenses.xml")
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
                Button(onClick = { openInBrowser(licenses[curLicenseIndex].licenseUrl) }) { Text("View website") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    try {
                        val reader = BufferedReader(InputStreamReader(context.assets.open(licenses[curLicenseIndex].licenseTextFile), "UTF-8"))
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
    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp).background(MaterialTheme.colorScheme.surface), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
fun NotificationPrefScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    val intent = Intent()
    intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

enum class PrefScreens(val titleRes: Int) {
    PortalScreen(R.string.settings_label),
    InterfaceScreen(R.string.user_interface_label),
    PlaybackScreen(R.string.playback_pref),
    DownloadScreen(R.string.network_pref),
    ImportExportScreen(R.string.import_export_pref),
    NotificationScreen(R.string.notification_pref_fragment),
    AboutScreen(R.string.about_pref),
    LicensesScreen(R.string.licenses);
}
