package ac.mdiq.podcini.ui.screens.prefscreens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.activity.BugReportActivity
import ac.mdiq.podcini.config.settings.developerEmail
import ac.mdiq.podcini.config.settings.getCopyrightNoticeText
import ac.mdiq.podcini.config.settings.githubAddress
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.IconTitleSummaryActionRow
import ac.mdiq.podcini.ui.screens.LocalDrawerController
import ac.mdiq.podcini.ui.screens.PopMode
import ac.mdiq.podcini.ui.screens.defaultNavKey
import ac.mdiq.podcini.ui.screens.navTo
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.openInBrowser
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.xml.parsers.DocumentBuilderFactory

private const val TAG = "PrefsMainScreen"

val pfBackStack = mutableStateListOf<PFNavKey>(PFNav.Portal)

fun pfNavBack(): Boolean {
    if (pfBackStack.size > 1) {
        pfBackStack.removeLastOrNull()
        return true
    }
    return false
}


@Serializable
sealed class PFNavKey

object PFNav {
    @Serializable
    data object Portal : PFNavKey()

    @Serializable
    data object Interface : PFNavKey()

    @Serializable
    data object NetworkStorage : PFNavKey()

    @Serializable
    data object ImportExport : PFNavKey()

    @Serializable
    data object Playback : PFNavKey()

    @Serializable
    data object Notification : PFNavKey()

    @Serializable
    data object About : PFNavKey()

    @Serializable
    data object Licenses : PFNavKey()
}

@OptIn(ExperimentalMaterial3Api::class)
val pfEntryProvider = entryProvider {
    entry<PFNav.Portal>{ PrefPortalScreen() }
    entry<PFNav.Interface>{ UserInterfaceScreen() }
    entry<PFNav.NetworkStorage>{ NetworkStorageScreen() }
    entry<PFNav.ImportExport>{ ImportExportScreen() }
    entry<PFNav.Playback>{ PlaybackScreen() }
    entry<PFNav.Notification>{ NotificationPrefScreen() }
    entry<PFNav.About>{ AboutScreen() }
    entry<PFNav.Licenses>{ LicensesScreen() }
}

val pfAnyEntryProvider: (Any) -> NavEntry<Any> = { key ->
    pfEntryProvider(key as PFNavKey) as NavEntry<Any>
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrefsScreen() {
    var topAppBarTitle by remember { mutableStateOf("Settings") }
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner" }
    val drawerController = LocalDrawerController.current

    Scaffold(topBar = { TopAppBar(title = { Text(topAppBarTitle) },
        navigationIcon = { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "Back", modifier = Modifier.padding(7.dp).clickable { if (!pfNavBack()) drawerController?.open() } ) }) }) { innerPadding ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
            NavDisplay(backStack = pfBackStack, onBack = { pfNavBack() }, entryProvider = pfAnyEntryProvider, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun PrefPortalScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    BackHandler(enabled = true) {
        pfBackStack.removeRange(0, pfBackStack.size-1)
        navTo(defaultNavKey, PopMode.Clear)
    }

    @Composable
    fun IconTitleSummaryScreenRow(vecRes: Int, titleRes: Int, summaryRes: Int, screen: PFNavKey) {
        val textColor = MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
            Icon(imageVector = ImageVector.vectorResource(vecRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
            Column(modifier = Modifier.weight(1f).clickable { pfBackStack.add(screen) }) {
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
            Column(modifier = Modifier.weight(1f).clickable { callback() }) {
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
        IconTitleSummaryScreenRow(R.drawable.ic_appearance, R.string.user_interface_label, R.string.user_interface_sum, PFNav.Interface)
        IconTitleSummaryScreenRow(R.drawable.ic_play_24dp, R.string.playback_pref, R.string.playback_pref_sum, PFNav.Playback)
        IconTitleSummaryScreenRow(R.drawable.ic_download, R.string.network_storage_pref, R.string.downloads_pref_sum, PFNav.NetworkStorage)
        IconTitleSummaryScreenRow(R.drawable.ic_storage, R.string.import_export_pref, R.string.import_export_summary, PFNav.ImportExport)
        IconTitleActionRow(R.drawable.ic_notifications, R.string.notification_pref_fragment) { pfBackStack.add(PFNav.Notification) }
        HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        Text(stringResource(R.string.project_pref), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        IconTitleActionRow(R.drawable.ic_questionmark, R.string.whats_new) { openInBrowser("${githubAddress}/blob/main/changelog.md") }
        IconTitleActionRow(R.drawable.ic_questionmark, R.string.documentation_support) { openInBrowser(githubAddress) }
        IconTitleActionRow(R.drawable.ic_contribute, R.string.pref_contribute) { openInBrowser(githubAddress) }
        IconTitleActionRow(R.drawable.ic_bug, R.string.bug_report_title) { context.startActivity(Intent(context, BugReportActivity::class.java)) }
        IconTitleActionRow(R.drawable.ic_info, R.string.about_pref) { pfBackStack.add(PFNav.About) }
    }
}

@Composable
fun AboutScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    val textColor = MaterialTheme.colorScheme.onSurface
    BackHandler(enabled = true) { pfBackStack.removeLastOrNull() }

    Column(modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp).background(MaterialTheme.colorScheme.surface)) {
        Image(painter = painterResource(R.drawable.teaser), contentDescription = "")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp)) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_star), contentDescription = "", tint = textColor)
            Column(Modifier.padding(start = 10.dp).clickable {
                val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
//                val clip = ClipData.newPlainText(context.getString(R.string.bug_report_title), PreferenceManager.getDefaultSharedPreferences(context).getString("about_version", "Default summary"))
                val versionText = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                val clip = ClipData.newPlainText(context.getString(R.string.bug_report_title), versionText)
                clipboard.setPrimaryClip(clip)
                if (Build.VERSION.SDK_INT <= 32) Logt(TAG, context.getString(R.string.copied_to_clipboard))
            }) {
                Text(stringResource(R.string.podcini_version), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(BuildConfig.VERSION_NAME, color = textColor)
            }
        }
        IconTitleSummaryActionRow(R.drawable.ic_questionmark, R.string.online_help, R.string.online_help_sum) { openInBrowser(githubAddress) }
        IconTitleSummaryActionRow(R.drawable.ic_info, R.string.privacy_policy, R.string.privacy_policy) { openInBrowser("${githubAddress}/blob/main/PrivacyPolicy.md") }
        IconTitleSummaryActionRow(R.drawable.ic_info, R.string.licenses, R.string.licenses_summary) { pfBackStack.add(PFNav.Licenses) }
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
    BackHandler(enabled = true) { pfBackStack.removeLastOrNull() }

    class LicenseItem(val title: String, val subtitle: String, val licenseUrl: String, val licenseTextFile: String)
    val licenses = remember { mutableStateListOf<LicenseItem>() }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            licenses.clear()
            val stream = context.assets.open("licenses.xml")
            val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val libraryList = docBuilder.parse(stream).getElementsByTagName("library")
            for (i in 0 until libraryList.length) {
                val lib = libraryList.item(i).attributes
                licenses.add(LicenseItem(lib.getNamedItem("name").textContent, "By ${lib.getNamedItem("author").textContent}, ${lib.getNamedItem("license").textContent} license", lib.getNamedItem("website").textContent, lib.getNamedItem("licenseText").textContent))
            }
        }
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
            Column(Modifier.clickable {
                curLicenseIndex = index
                showDialog = true
            }) {
                Text(item.title, color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(item.subtitle, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun NotificationPrefScreen() {
    val context by rememberUpdatedState(LocalContext.current)
    BackHandler(enabled = true) { pfBackStack.removeLastOrNull() }

    val intent = Intent()
    intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}
