package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.PodciniApp.Companion.forceRestart
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.newBuilder
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.reinit
import ac.mdiq.podcini.net.feed.FeedUpdateManager.checkAndscheduleUpdateTaskOnce
import ac.mdiq.podcini.net.feed.FeedUpdateManager.getInitialDelay
import ac.mdiq.podcini.net.feed.FeedUpdateManager.nextRefreshTime
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.SynchronizationCredentials
import ac.mdiq.podcini.net.sync.SynchronizationProviderViewData
import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setSelectedSyncProvider
import ac.mdiq.podcini.net.sync.SynchronizationSettings.setWifiSyncEnabled
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow
import ac.mdiq.podcini.net.sync.nextcloud.NextcloudLoginFlow.AuthenticationCallback
import ac.mdiq.podcini.net.sync.wifi.WifiSyncService.Companion.startInstantSync
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.proxyConfig
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.MediaFilesTransporter
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.storage.utils.deleteDirectoryRecursively
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.CommonPopupCard
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.NumberEditor
import ac.mdiq.podcini.ui.compose.Spinner
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.app.Activity.RESULT_OK
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials.basic
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "NetworkPreferences"

@Suppress("EnumEntryName")
enum class MobileUpdateOptions(val res: Int) {
    feed_refresh(R.string.pref_mobileUpdate_refresh),
    episode_download(R.string.pref_mobileUpdate_episode_download),
    auto_download(R.string.pref_mobileUpdate_auto_download),
    streaming(R.string.pref_mobileUpdate_streaming),
    images(R.string.pref_mobileUpdate_images),
    sync(R.string.synchronization_pref);
}

@Composable
fun SynchronizationScreen(activity: PreferenceActivity) {

    val selectedSyncProviderKey: String = SynchronizationSettings.selectedSyncProviderKey?:""
    var selectedProvider by remember { mutableStateOf(SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)) }
    var loggedIn by remember { mutableStateOf(isProviderConnected) }

    @Composable
    fun NextcloudAuthenticationDialog(onDismissRequest: ()->Unit) {
        var nextcloudLoginFlow = remember<NextcloudLoginFlow?> { null }
        var showUrlEdit by remember { mutableStateOf(true) }
        var serverUrlText by remember { mutableStateOf(getPref(AppPrefs.pref_nextcloud_server_address, "")) }
        var errorText by remember { mutableStateOf("") }
        var showChooseHost by remember { mutableStateOf(serverUrlText.isNotBlank()) }

        val nextCloudAuthCallback = object : AuthenticationCallback {
            override fun onNextcloudAuthenticated(server: String, username: String, password: String) {
                Logd("NextcloudAuthenticationDialog", "onNextcloudAuthenticated: $server")
                setSelectedSyncProvider(SynchronizationProviderViewData.NEXTCLOUD_GPODDER)
                SynchronizationCredentials.clear(activity)
                SynchronizationCredentials.password = password
                SynchronizationCredentials.hosturl = server
                SynchronizationCredentials.username = username
                SyncService.fullSync(activity)
                loggedIn = isProviderConnected
                onDismissRequest()
            }
            override fun onNextcloudAuthError(errorMessage: String?) {
                errorText = errorMessage ?: ""
                showChooseHost = true
                showUrlEdit = true
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) nextcloudLoginFlow?.poll() }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.gpodnetauth_login_butLabel), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Text(stringResource(R.string.synchronization_host_explanation))
                    if (showUrlEdit) TextField(value = serverUrlText, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.synchronization_host_label)) },
                        onValueChange = {
                            serverUrlText = it
                            showChooseHost = serverUrlText.isNotBlank()
                        }
                    )
                    Text(stringResource(R.string.synchronization_nextcloud_authenticate_browser))
                    if (errorText.isNotBlank()) Text(errorText)
                }
            },
            confirmButton = {
                if (showChooseHost) TextButton(onClick = {
                    putPref(AppPrefs.pref_nextcloud_server_address, serverUrlText)
                    nextcloudLoginFlow = NextcloudLoginFlow(getHttpClient(), serverUrlText, activity, nextCloudAuthCallback)
                    errorText = ""
                    showChooseHost = false
                    nextcloudLoginFlow.start()
                    //                        onDismissRequest()
                }) { Text(stringResource(R.string.proceed_to_login_butLabel)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showNextCloudAuthDialog by remember { mutableStateOf(false) }
    if (showNextCloudAuthDialog) NextcloudAuthenticationDialog { showNextCloudAuthDialog = false }

    @Composable
    fun ChooseProviderAndLoginDialog(onDismissRequest: ()->Unit) {
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.dialog_choose_sync_service_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    SynchronizationProviderViewData.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                            .clickable {
                                when (option) {
                                    //                                    SynchronizationProviderViewData.GPODDER_NET -> GpodderAuthenticationFragment().show(activity.supportFragmentManager, GpodderAuthenticationFragment.TAG)
                                    SynchronizationProviderViewData.NEXTCLOUD_GPODDER -> showNextCloudAuthDialog = true
                                }
                                loggedIn = isProviderConnected
                                onDismissRequest()
                            }) {
                            Icon(painter = painterResource(id = option.iconResource), contentDescription = "", modifier = Modifier.size(40.dp).padding(end = 15.dp))
                            Text(stringResource(option.summaryResource), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    @Composable
    fun WifiAuthenticationDialog(onDismissRequest: ()->Unit) {
        val TAG = "WifiAuthenticationDialog"
        val textColor = MaterialTheme.colorScheme.onSurface
        val context by rememberUpdatedState(LocalContext.current)
        var progressMessage by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            scope.launch {
                EventFlow.events.collectLatest { event ->
                    Logd(TAG, "Received event: ${event.TAG}")
                    when (event) {
                        is FlowEvent.SyncServiceEvent -> {
                            when (event.messageResId) {
                                R.string.sync_status_error -> {
                                    errorMessage = event.message
                                    Loge(TAG, errorMessage)
                                    onDismissRequest()
                                }
                                R.string.sync_status_success -> {
                                    Logt(TAG, context.getString(R.string.sync_status_success))
                                    onDismissRequest()
                                }
                                R.string.sync_status_in_progress -> progressMessage = event.message
                                else -> Loge(TAG, "Sync result unknown ${event.messageResId}")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
        var portNum by remember { mutableIntStateOf(SynchronizationCredentials.hostport) }
        var isGuest by remember { mutableStateOf<Boolean?>(null) }
        var hostAddress by remember { mutableStateOf(SynchronizationCredentials.hosturl?:"") }
        var showHostAddress by remember { mutableStateOf(true)  }
        var portString by remember { mutableStateOf(SynchronizationCredentials.hostport.toString()) }
        var showProgress by remember { mutableStateOf(false) }
        var showConfirm by remember { mutableStateOf(true)  }
        var showCancel by remember { mutableStateOf(true)  }
        AlertDialog(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.connect_to_peer), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Text(stringResource(R.string.wifisync_explanation_message), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = {
                            val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
                            val ipAddress = wifiManager.connectionInfo.ipAddress
                            val ipString = String.format(Locale.US, "%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
                            hostAddress = ipString
                            showHostAddress = false
                            portNum = portString.toInt()
                            isGuest = false
                            SynchronizationCredentials.hostport = portNum
                        }) { Text(stringResource(R.string.host_butLabel)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            SynchronizationCredentials.hosturl = hostAddress
                            showHostAddress = true
                            portNum = portString.toInt()
                            isGuest = true
                            SynchronizationCredentials.hostport = portNum
                        }) { Text(stringResource(R.string.guest_butLabel)) }
                    }
                    Row {
                        if (showHostAddress) TextField(value = hostAddress, modifier = Modifier.weight(0.6f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { input -> hostAddress = input },
                            label = { Text(stringResource(id = R.string.synchronization_host_address_label)) })
                        TextField(value = portString, modifier = Modifier.weight(0.4f).padding(start = 3.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            onValueChange = { input ->
                                portString = input
                                portNum = input.toInt()
                            },
                            label = { Text(stringResource(id = R.string.synchronization_host_port_label)) })
                    }
                    if (showProgress)  {
                        CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp))
                        Text(stringResource(R.string.wifisync_progress_message) + " " + progressMessage, color = textColor)
                    }
                    Text(errorMessage, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                if (showConfirm) TextButton(onClick = {
                    Logd(TAG, "confirm button pressed")
                    if (isGuest == null) {
                        Logt(TAG, getAppContext().getString(R.string.host_or_guest))
                        return@TextButton
                    }
                    showProgress = true
                    showConfirm = false
                    showCancel = false
                    setWifiSyncEnabled(true)
                    startInstantSync(getAppContext(), portNum, hostAddress, isGuest!!)
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { if (showCancel) TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showWifiAuthenticationDialog by remember { mutableStateOf(false) }
    if (showWifiAuthenticationDialog) WifiAuthenticationDialog { showWifiAuthenticationDialog = false }

    var chooseProviderAndLoginDialog by remember { mutableStateOf(false) }
    if (chooseProviderAndLoginDialog) ChooseProviderAndLoginDialog { chooseProviderAndLoginDialog = false }

//    fun isProviderSelected(provider: SynchronizationProviderViewData): Boolean {
//        val selectedSyncProviderKey = selectedSyncProviderKey
//        return provider.identifier == selectedSyncProviderKey
//    }

    val textColor = MaterialTheme.colorScheme.onSurface
    TitleSummaryActionColumn(R.string.wifi_sync, R.string.wifi_sync_summary_unchoosen) {
        showWifiAuthenticationDialog = true
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 10.dp)) {
        var titleRes by remember { mutableIntStateOf(0) }
        var summaryRes by remember { mutableIntStateOf(R.string.synchronization_summary_unchoosen) }
        var iconRes by remember { mutableIntStateOf(R.drawable.ic_notification_sync) }
        var onClick: (() -> Unit)? = null
        if (loggedIn) {
            selectedProvider = SynchronizationProviderViewData.fromIdentifier(selectedSyncProviderKey)
            if (selectedProvider != null) {
                summaryRes = selectedProvider!!.summaryResource
                iconRes = selectedProvider!!.iconResource
                Icon(painter = painterResource(id = iconRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
            }
        } else {
            titleRes = R.string.synchronization_choose_title
            summaryRes = R.string.synchronization_summary_unchoosen
            iconRes = R.drawable.ic_cloud
            onClick = { chooseProviderAndLoginDialog = true }
            Icon(imageVector = ImageVector.vectorResource(iconRes), contentDescription = "", tint = textColor, modifier = Modifier.size(40.dp).padding(end = 15.dp))
        }
        TitleSummaryActionColumn(titleRes, summaryRes) { onClick?.invoke() }
    }
    if (loggedIn) {
        TitleSummaryActionColumn(R.string.synchronization_sync_changes_title, R.string.synchronization_sync_summary) { SyncService.syncImmediately(activity.applicationContext) }
        TitleSummaryActionColumn(R.string.synchronization_full_sync_title, R.string.synchronization_force_sync_summary) { SyncService.fullSync(activity) }
        TitleSummaryActionColumn(R.string.synchronization_logout, 0) {
            SynchronizationCredentials.clear(activity)
            Logt("SynchronizationPreferencesScreen", activity.getString(R.string.pref_synchronization_logout_toast))
            setSelectedSyncProvider(null)
            loggedIn = isProviderConnected
        }
    }
}

@Composable
fun NetworkScreen(activity: PreferenceActivity) {
    @Composable
    fun ProxyDialog(onDismissRequest: ()->Unit) {
        val textColor = MaterialTheme.colorScheme.onSurface
        val types = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            types.add(Proxy.Type.DIRECT.name)
            types.add(Proxy.Type.HTTP.name)
            types.add(Proxy.Type.SOCKS.name)
        }
        var testSuccessful by remember { mutableStateOf(false) }
        var type by remember { mutableStateOf(proxyConfig.type.name) }
        var typePos by remember { mutableIntStateOf(0) }
        var host by remember { mutableStateOf(proxyConfig.host?:"") }
//        var hostError by remember { mutableStateOf("") }
        var port by remember { mutableStateOf(if (proxyConfig.port > 0) proxyConfig.port.toString() else "") }
//        var portError by remember { mutableStateOf("") }
        var portValue by remember { mutableIntStateOf(proxyConfig.port) }
        var username by remember { mutableStateOf(proxyConfig.username) }
        var password by remember { mutableStateOf(proxyConfig.password) }
        var message by remember { mutableStateOf("") }
        var messageColor by remember { mutableStateOf(textColor) }
        var showOKButton by remember { mutableStateOf(false) }
        var okButtonTextRes by remember { mutableIntStateOf(R.string.proxy_test_label) }

        fun setProxyConfig() {
            val typeEnum = Proxy.Type.valueOf(type)
            if (username.isNullOrEmpty()) username = null
            if (password.isNullOrEmpty()) password = null
            if (port.isNotEmpty()) portValue = port.toInt()
            val config = ProxyConfig(typeEnum, host, portValue, username, password)
            proxyConfig = config
            PodciniHttpClient.setProxyConfig(config)
        }
        fun setTestRequired(required: Boolean) {
            if (required) {
                testSuccessful = false
                okButtonTextRes = R.string.proxy_test_label
            } else {
                testSuccessful = true
                okButtonTextRes = android.R.string.ok
            }
        }
        fun checkHost(): Boolean {
            if (host.isEmpty()) {
//                hostError = activity.getString(R.string.proxy_host_empty_error)
                Loge(TAG, activity.getString(R.string.proxy_host_empty_error))
                return false
            }
            if ("localhost" != host && !Patterns.DOMAIN_NAME.matcher(host).matches()) {
//                hostError = activity.getString(R.string.proxy_host_invalid_error)
                Loge(TAG, activity.getString(R.string.proxy_host_invalid_error))
                return false
            }
            return true
        }
        fun checkPort(): Boolean {
            if (portValue !in 0..65535) {
//                portError = activity.getString(R.string.proxy_port_invalid_error)
                Loge(TAG, "activity.getString(R.string.proxy_port_invalid_error)")
                return false
            }
            return true
        }
        fun checkValidity(): Boolean {
            var valid = true
            if (typePos > 0) valid = checkHost()
            valid = valid and checkPort()
            return valid
        }
        fun test() {
            if (!checkValidity()) {
                setTestRequired(true)
                return
            }
            val checking = activity.getString(R.string.proxy_checking)
            messageColor = textColor
            message = "{faw_circle_o_notch spin} $checking"
            val coroutineScope = CoroutineScope(Dispatchers.Main)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (port.isNotEmpty()) portValue = port.toInt()
                    val address: SocketAddress = InetSocketAddress.createUnresolved(host, portValue)
                    val proxyType = Proxy.Type.valueOf(type.uppercase())
                    val builder: OkHttpClient.Builder = newBuilder().connectTimeout(10, TimeUnit.SECONDS).proxy(Proxy(proxyType, address))
                    if (!username.isNullOrBlank())
                        builder.proxyAuthenticator { _: Route?, response: Response -> response.request.newBuilder().header("Proxy-Authorization", basic(username?:"", password?:"")).build() }
                    val client: OkHttpClient = builder.build()
                    val request: Request = Builder().url("https://www.example.com").head().build()
                    try { client.newCall(request).execute().use { response -> if (!response.isSuccessful) throw IOException(response.message) }
                    } catch (e: IOException) { throw e }
                    withContext(Dispatchers.Main) {
                        message = String.format("%s %s", "{faw_check}", activity.getString(R.string.proxy_test_successful))
                        messageColor = Color.Green
                        setTestRequired(false)
                    }
                } catch (e: Throwable) {
                    Logs("DownloadsPreferencesScreen", e)
                    messageColor = Color.Red
                    message = String.format("%s %s: %s", "{faw_close}", activity.getString(R.string.proxy_test_failed), e.message)
                    setTestRequired(true)
                }
            }
        }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
            title = { Text(stringResource(R.string.pref_proxy_title), style = CustomTextStyles.titleCustom) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.proxy_type_label))
                        Spinner(items = types, selectedItem = proxyConfig.type.name) { position ->
                            val name = Proxy.Type.entries.getOrNull(position)?.name
                            if (!name.isNullOrBlank()) {
                                typePos = position
                                type = name
                                showOKButton = position != 0
                                setTestRequired(position > 0)
                            }
                        }
                    }
                    if (typePos > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.host_label))
                            TextField(value = host, label = { Text("www.example.com") }, isError = !checkHost(), modifier = Modifier.fillMaxWidth(),
                                onValueChange = { host = it }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.port_label))
                            TextField(value = port, label = { Text("8080") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !checkPort(), modifier = Modifier.fillMaxWidth(),
                                onValueChange = {
                                    port = it
                                    portValue = it.toIntOrNull() ?: -1
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.username_label))
                            TextField(value = username ?: "", label = { Text(stringResource(R.string.optional_hint)) }, modifier = Modifier.fillMaxWidth(),
                                onValueChange = { username = it }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.password_label))
                            TextField(value = password ?: "", label = { Text(stringResource(R.string.optional_hint)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                                onValueChange = { password = it }
                            )
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = messageColor)
                }
            },
            confirmButton = {
                if (showOKButton) TextButton(onClick = {
                    if (!testSuccessful) {
                        test()
                        return@TextButton
                    }
                    setProxyConfig()
                    reinit()
                    onDismissRequest()
                }) { Text(stringResource(okButtonTextRes)) }
            },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showProxyDialog by remember { mutableStateOf(false) }
    if (showProxyDialog) ProxyDialog {showProxyDialog = false }

    var useCustomMediaDir by remember { mutableStateOf(getPref(AppPrefs.prefUseCustomMediaFolder, false)) }

    val showImporSuccessDialog = remember { mutableStateOf(false) }
    ComfirmDialog(titleRes = R.string.successful_import_label, message = stringResource(R.string.import_ok), showDialog = showImporSuccessDialog, cancellable = false) { forceRestart() }

    val textColor = MaterialTheme.colorScheme.onSurface

    var showProgress by remember { mutableStateOf(false) }
    if (showProgress) {
        CommonPopupCard(onDismissRequest = { showProgress = false }) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = {0.6f}, strokeWidth = 10.dp, color = textColor, modifier = Modifier.size(50.dp).align(Alignment.TopCenter))
                Text("Loading...", color = textColor, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    var customMediaFolderUriString by remember { mutableStateOf(getPref(AppPrefs.prefCustomMediaUri, "")) }
    val selectCustomMediaDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val uri: Uri? = it.data?.data
            if (uri != null) {
                showProgress = true
                CoroutineScope(Dispatchers.IO).launch {
                    val chosenDir = if (customMediaFolderUriString.isNotBlank()) DocumentFile.fromTreeUri(activity, customMediaFolderUriString.toUri()) else null
                    activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val baseDir = DocumentFile.fromTreeUri(getAppContext(), uri) ?: return@launch
                    val mediaDir = baseDir.createDirectory("Podcini.media") ?: return@launch
                    MediaFilesTransporter("Podcini.media").exportToUri(mediaDir.uri, getAppContext(), move = true, useSubDir = false)
                    customMediaFolderUriString = mediaDir.uri.toString()
                    useCustomMediaDir = true
                    putPref(AppPrefs.prefUseCustomMediaFolder, true)
                    putPref(AppPrefs.prefCustomMediaUri, customMediaFolderUriString)
                    if (chosenDir != null) deleteDirectoryRecursively(chosenDir)
                    showProgress = false
                    showImporSuccessDialog.value = true
                }
            } else Loge("selectCustomMediaDirLauncher", "uri is null")
        }
    }

    var refreshInterval by remember { mutableStateOf(getPref(AppPrefs.prefAutoUpdateIntervalMinutes, "360")) }
    LaunchedEffect(Unit) {
        getInitialDelay(activity)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feed_refresh_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                NumberEditor(refreshInterval.toInt(), stringResource(R.string.time_minutes), nz = false, modifier = Modifier.weight(0.6f)) {
                    refreshInterval = it.toString()
                    Logd("DownloadsSetting", "refreshInterval: $refreshInterval")
                    putPref(AppPrefs.prefAutoUpdateIntervalMinutes, refreshInterval)
                    checkAndscheduleUpdateTaskOnce(activity.applicationContext, replace = true, force = true)
                }
            }
            Text(stringResource(R.string.feed_refresh_sum), color = textColor, style = MaterialTheme.typography.bodySmall)
            if (refreshInterval != "0") Text(stringResource(R.string.feed_next_refresh_time) + " " + nextRefreshTime, color = textColor, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
        var isEnabled by remember { mutableStateOf(getPref(AppPrefs.prefEnableAutoDl, false)) }
        TitleSummarySwitchPrefRow(R.string.pref_automatic_download_title, R.string.pref_automatic_download_sum, AppPrefs.prefEnableAutoDl) {
            isEnabled = it
            putPref(AppPrefs.prefEnableAutoDl, it)
        }
        if (isEnabled) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pref_episode_cache_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    var interval by remember { mutableStateOf(getPref(AppPrefs.prefEpisodeCacheSize, "25")) }
                    NumberEditor(interval.toInt(), label = "integer", nz = false, modifier = Modifier.weight(0.5f)) {
                        interval = it.toString()
                        putPref(AppPrefs.prefEpisodeCacheSize, interval)
                    }
                }
                Text(stringResource(R.string.pref_episode_cache_summary), color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            var showCleanupOptions by remember { mutableStateOf(false) }
            TitleSummaryActionColumn(R.string.pref_episode_cleanup_title, R.string.pref_episode_cleanup_summary) { showCleanupOptions = true }
            if (showCleanupOptions) {
                var tempCleanupOption by remember { mutableStateOf(getPref(AppPrefs.prefEpisodeCleanup, "-1")) }
                var interval by remember { mutableStateOf(getPref(AppPrefs.prefEpisodeCleanup, "-1")) }
                if ((interval.toIntOrNull() ?: -1) > 0) tempCleanupOption = EpisodeCleanupOptions.LimitBy.num.toString()
                AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showCleanupOptions = false },
                    title = { Text(stringResource(R.string.pref_episode_cleanup_title), style = CustomTextStyles.titleCustom) },
                    text = {
                        Column {
                            EpisodeCleanupOptions.entries.forEach { option ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp)
                                    .clickable { tempCleanupOption = option.num.toString() }) {
                                    Checkbox(checked = tempCleanupOption == option.num.toString(), onCheckedChange = { tempCleanupOption = option.num.toString() })
                                    Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) {
                                NumberEditor(interval.toInt(), label = "integer", modifier = Modifier.weight(0.6f)) { interval = it.toString() }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            var num = if (tempCleanupOption == EpisodeCleanupOptions.LimitBy.num.toString()) interval else tempCleanupOption
                            if (num.toIntOrNull() == null) num = EpisodeCleanupOptions.Never.num.toString()
                            putPref(AppPrefs.prefEpisodeCleanup, num)
                            showCleanupOptions = false
                        }) { Text(text = "OK") }
                    },
                    dismissButton = { TextButton(onClick = { showCleanupOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
                )
            }
            TitleSummarySwitchPrefRow(R.string.pref_automatic_download_on_battery_title, R.string.pref_automatic_download_on_battery_sum, AppPrefs.prefEnableAutoDownloadOnBattery)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)

        TitleSummarySwitchPrefRow(R.string.pref_disable_wifilock_title, R.string.pref_disable_wifilock_sum, AppPrefs.prefDisableWifiLock)

        var showSetCustomFolderDialog by remember { mutableStateOf(false) }
        if (showSetCustomFolderDialog) {
            val sumTextRes = if (useCustomMediaDir) R.string.pref_custom_media_dir_sum1 else R.string.pref_custom_media_dir_sum
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showSetCustomFolderDialog = false },
                title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
                text = { Text(stringResource(sumTextRes), color = textColor, style = MaterialTheme.typography.bodySmall) },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        selectCustomMediaDirLauncher.launch(intent)
                        showSetCustomFolderDialog = false
                    }) { Text(stringResource(R.string.confirm_label)) }
                },
                dismissButton = { TextButton(onClick = { showSetCustomFolderDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        var showResetCustomFolderDialog by remember { mutableStateOf(false) }
        if (showResetCustomFolderDialog) {
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showResetCustomFolderDialog = false },
                title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
                text = { Text(stringResource(R.string.pref_custom_media_dir_sum2), color = textColor, style = MaterialTheme.typography.bodySmall) },
                confirmButton = {
                    TextButton(onClick = {
                        showProgress = true
                        CoroutineScope(Dispatchers.IO).launch {
                            val chosenDir = DocumentFile.fromTreeUri(activity, customMediaFolderUriString.toUri()) ?: throw IOException("Destination directory is not valid")
                            customMediaFolderUriString = ""
                            useCustomMediaDir = false
                            putPref(AppPrefs.prefUseCustomMediaFolder, false)
                            putPref(AppPrefs.prefCustomMediaUri, "")
                            MediaFilesTransporter("").importFromUri(chosenDir.uri, activity, move = true, verify = false)
                            deleteDirectoryRecursively(chosenDir)
//                            createNoMediaFile()
                            showProgress = false
                            showImporSuccessDialog.value = true
                            showResetCustomFolderDialog = false
                        }
                    }) { Text(stringResource(R.string.reset)) }
                },
                dismissButton = { TextButton(onClick = { showResetCustomFolderDialog = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = { showSetCustomFolderDialog = true })) {
                Text(stringResource(R.string.pref_custom_media_dir_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
                Text(customMediaFolderUriString.ifBlank { stringResource(R.string.pref_custom_media_dir_sum) }, color = textColor, style = MaterialTheme.typography.bodySmall)
            }
            if (useCustomMediaDir) TextButton(onClick = { showResetCustomFolderDialog = true }) { Text(stringResource(R.string.reset)) }
        }
        var showMeteredNetworkOptions by remember { mutableStateOf(false) }
        TitleSummaryActionColumn(R.string.pref_metered_network_title, R.string.pref_mobileUpdate_sum) { showMeteredNetworkOptions = true }
        if (showMeteredNetworkOptions) {
            val initMobileOptions by remember { mutableStateOf(getPref(AppPrefs.prefMobileUpdateTypes, setOf("images"))) }
            var tempSelectedOptions by remember { mutableStateOf(getPref(AppPrefs.prefMobileUpdateTypes, setOf("images"))) }
            fun updateSepections(option: MobileUpdateOptions) {
                tempSelectedOptions = if (tempSelectedOptions.contains(option.name)) tempSelectedOptions - option.name else tempSelectedOptions + option.name
                when (option) {
                    MobileUpdateOptions.auto_download if tempSelectedOptions.contains(option.name) -> {
                        tempSelectedOptions += MobileUpdateOptions.episode_download.name
                        tempSelectedOptions += MobileUpdateOptions.feed_refresh.name
                    }
                    MobileUpdateOptions.episode_download if !tempSelectedOptions.contains(option.name) -> tempSelectedOptions -= MobileUpdateOptions.auto_download.name
                    MobileUpdateOptions.feed_refresh if !tempSelectedOptions.contains(option.name) -> tempSelectedOptions -= MobileUpdateOptions.auto_download.name
                    else -> {}
                }
            }
            AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { showMeteredNetworkOptions = false },
                title = { Text(stringResource(R.string.pref_metered_network_title), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        MobileUpdateOptions.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(2.dp).clickable { updateSepections(option) }) {
                                Checkbox(checked = tempSelectedOptions.contains(option.name), onCheckedChange = { updateSepections(option) })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        putPref(AppPrefs.prefMobileUpdateTypes, tempSelectedOptions)
                        val optionsDiff = (tempSelectedOptions - initMobileOptions) + (initMobileOptions - tempSelectedOptions)
                        if (optionsDiff.contains(MobileUpdateOptions.feed_refresh.name) || optionsDiff.contains(MobileUpdateOptions.auto_download.name))
                            checkAndscheduleUpdateTaskOnce(activity.applicationContext, replace = true, force = true)
                        showMeteredNetworkOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showMeteredNetworkOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        TitleSummaryActionColumn(R.string.pref_proxy_title, R.string.pref_proxy_sum) { showProxyDialog = true }
        HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer, thickness = 1.dp)
        SynchronizationScreen(activity)
    }
}

enum class EpisodeCleanupOptions(val res: Int, val num: Int) {
    ExceptFavorites(R.string.episode_cleanup_except_favorite, -3),
    Never(R.string.episode_cleanup_never, -2),
    NotInQueue(R.string.episode_cleanup_not_in_queue, -1),
    LimitBy(R.string.episode_cleanup_limit_by, 0)
}
