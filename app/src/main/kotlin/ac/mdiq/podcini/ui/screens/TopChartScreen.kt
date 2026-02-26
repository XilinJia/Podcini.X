package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.feed.PodcastSearchResult
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.filterChipBorder
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.timeIt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class DiscoveryVM: ViewModel() {
    val countryNameCodeMap: MutableMap<String, String> = hashMapOf()
    val countryCodeNameMap: MutableMap<String?, String> = hashMapOf()
    val countryNamesSort =  mutableStateListOf<String>()
    var selectedCountry by mutableStateOf("")
    var textInput by mutableStateOf("")

    var countryCode by mutableStateOf("US")
    var curGenre by mutableIntStateOf(0)

    val spinnerTexts = genres.keys.map { getAppContext().getString(it) }
    var curIndex by  mutableIntStateOf(0)

    var topList = listOf<PodcastSearchResult>()
    val searchResults = mutableStateListOf<PodcastSearchResult>()
    var errorText by mutableStateOf("")
    var retryQerry by  mutableStateOf("")
    var showProgress by  mutableStateOf(true)
    var noResultText by  mutableStateOf("")

    fun loadToplist() {
        searchResults.clear()
        errorText = ""
        retryQerry = ""
        noResultText = ""
        showProgress = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val NUM_LOADED = 100
                val COUNTRY_CODE_UNSET = "99"
                val client = getHttpClient()
                var loadCountry = countryCode
                if (countryCode == COUNTRY_CODE_UNSET) loadCountry = Locale.getDefault().country

                val url0 = "https://itunes.apple.com/%s/rss/toppodcasts/limit=$NUM_LOADED/json"
                val url = "https://itunes.apple.com/%s/rss/toppodcasts/limit=$NUM_LOADED/genre=%d/json"
                val reqStr = if (curGenre > 0) String.format(url, loadCountry, curGenre) else String.format(url0, loadCountry)
                Logd(TAG, "getTopListFeed reqStr: $reqStr")
                val httpReq: Request.Builder = Request.Builder().cacheControl(CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build()).url(reqStr)
                val feedString: String
                client.newCall(httpReq.build()).execute().use { response ->
                    if (response.isSuccessful) feedString = response.body.string()
                    else {
                        if (response.code == 400) throw IOException("iTunes does not have data for the selected country.")
                        throw IOException(getAppContext().getString(R.string.error_msg_prefix) + response)
                    }
                }
                @Throws(JSONException::class)
                fun parseFeed(jsonString: String): List<PodcastSearchResult> {
                    /**
                     * Constructs a Podcast instance from iTunes toplist entry
                     * @param json object holding the podcast information
                     * @throws JSONException
                     */
                    @Throws(JSONException::class)
                    fun fromItunesToplist(json: JSONObject): PodcastSearchResult {
                        val title = json.getJSONObject("title").getString("label")
                        var imageUrl: String? = null
                        val images = json.getJSONArray("im:image")
                        var i = 0
                        while (imageUrl == null && i < images.length()) {
                            val image = images.getJSONObject(i)
                            val height = image.getJSONObject("attributes").getString("height")
                            if (height.toInt() >= 100) imageUrl = image.getString("label")
                            i++
                        }
                        val feedUrl = "https://itunes.apple.com/lookup?id=" + json.getJSONObject("id").getJSONObject("attributes").getString("im:id")

                        var author: String? = null
                        try { author = json.getJSONObject("im:artist").getString("label")
                        } catch (e: Exception) {/* Some feeds have empty artist */ }
                        return PodcastSearchResult(title, imageUrl, feedUrl, author, null, null, -1, "Toplist")
                    }

                    val result = JSONObject(jsonString)
                    val feed: JSONObject
                    val entries: JSONArray
                    try {
                        feed = result.getJSONObject("feed")
                        entries = feed.getJSONArray("entry")
                    } catch (_: JSONException) { return mutableListOf() }
                    val results: MutableList<PodcastSearchResult> = mutableListOf()
                    for (i in 0 until entries.length()) {
                        val json = entries.getJSONObject(i)
                        results.add(fromItunesToplist(json))
                    }
                    return results
                }
                val podcasts = parseFeed(feedString).take(NUM_OF_TOP_PODCASTS)

                withContext(Dispatchers.Main) {
                    showProgress = false
                    topList = podcasts
                    searchResults.clear()
                    if (topList.isNotEmpty()) {
                        searchResults.addAll(topList)
                        noResultText = ""
                    } else noResultText = getAppContext().getString(R.string.no_results_for_query)
                    showProgress = false
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                searchResults.clear()
                errorText = e.message ?: "no error message"
                retryQerry = " retry"
            }
        }
    }

    init {
        timeIt("$TAG start of init")
        countryCode = appAttribs.topChartCountryCode

        for (code in Locale.getISOCountries()) {
            val locale = Locale("", code)
            val countryName = locale.displayCountry
            Logd(TAG, "code: $code countryName: $countryName")
            countryCodeNameMap[code] = countryName
            countryNameCodeMap[countryName] = code
        }
        countryNamesSort.addAll(countryCodeNameMap.values)
        countryNamesSort.sort()
        selectedCountry = countryCodeNameMap[countryCode] ?: ""
        textInput = selectedCountry

        loadToplist()
        timeIt("$TAG end of init")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopChartScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context by rememberUpdatedState(LocalContext.current)
    val navController = LocalNavController.current
    val drawerController = LocalDrawerController.current

    val vm: DiscoveryVM = viewModel()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {}
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @Composable
    fun SelectCountryDialog(onDismiss: () -> Unit) {
        @Composable
        fun CountrySelection() {
            val filteredCountries = remember { vm.countryNamesSort.toMutableStateList() }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                TextField(value = vm.textInput, modifier = Modifier.fillMaxWidth().padding(20.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, false), readOnly = false,
                    onValueChange = { input ->
                        vm.textInput = input
                        if (vm.textInput.length > 1) {
                            filteredCountries.clear()
                            filteredCountries.addAll(vm.countryNamesSort.filter { it.contains(input, ignoreCase = true) }.take(5))
                            Logd(TAG, "input: $input filteredCountries: ${filteredCountries.size}")
                            expanded = filteredCountries.isNotEmpty()
                        }
                    },
                    label = { Text(stringResource(id = R.string.select_country)) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filteredCountries.forEach { country ->
                        DropdownMenuItem(text = { Text(text = country) }, onClick = {
                            vm.selectedCountry = country
                            vm.textInput = country
                            expanded = false
                        })
                    }
                }
            }
        }
        AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
            text = { CountrySelection() },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.countryNameCodeMap.containsKey(vm.selectedCountry)) vm.countryCode = vm.countryNameCodeMap[vm.selectedCountry]!!
                    upsertBlk(appAttribs) { it.topChartCountryCode = vm.countryCode }
                    EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                    vm.loadToplist()
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showSelectCounrty by remember { mutableStateOf(false) }
    if (showSelectCounrty) SelectCountryDialog { showSelectCounrty = false }

    var showChooseGenre by remember { mutableStateOf(false) }
    @Composable
    fun ChooseGenre() {
        Popup(onDismissRequest = { showChooseGenre = false }, alignment = Alignment.TopStart, offset = IntOffset(100, 100), properties = PopupProperties(focusable = true)) {
            Card(modifier = Modifier.width(300.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                    for (index in vm.spinnerTexts.indices) {
                        FilterChip(onClick = {
                            vm.curIndex = index
                            vm.curGenre = genres[genres.keys.toList()[index]] ?: 0
                            Logd(TAG, "SpinnerExternalSet ${vm.curIndex} curGenre: ${vm.curGenre}")
                            vm.loadToplist()
                            showChooseGenre = false
                        }, label = { Text(vm.spinnerTexts[index]) }, selected = vm.curIndex == index, border = filterChipBorder(vm.curIndex == index))
                    }
                }
            }
        }
    }
    if (showChooseGenre) ChooseGenre()

    
    @Composable
    fun MyTopAppBar() {
        Box {
            TopAppBar(title = {
                Row {
                    Text(vm.spinnerTexts[vm.curIndex], maxLines=1, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.clickable(onClick = { showChooseGenre = true }))
                    Spacer(Modifier.weight(1f))
                    Text(vm.countryCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end=20.dp).clickable(onClick = { showSelectCounrty = true }))
                } },
                navigationIcon = { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "", modifier = Modifier.padding(7.dp).clickable(onClick = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack() else drawerController?.open() })) })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val (gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
            if (vm.showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            val lazyListState = rememberLazyListState()
            if (vm.searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .constrainAs(gridView) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.searchResults) { result -> OnlineFeedItem(result) }
            }
            if (vm.searchResults.isEmpty()) Text(vm.noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
            if (vm.errorText.isNotEmpty()) Text(vm.errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
            if (vm.retryQerry.isNotEmpty()) Button(modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) }, onClick = { vm.loadToplist() }, ) { Text(vm.retryQerry) }
            Text(context.getString(R.string.search_powered_by, "Apple"), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(Color.LightGray)
                .constrainAs(powered) {
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end)
                })
        }
    }
}

private val genres: LinkedHashMap<Int, Int> = linkedMapOf(
    R.string.All to 0,
    R.string.Arts to 1301,
    R.string.Business to 1321,
    R.string.Comedy to 1303,
    R.string.Education to 1304,
    R.string.Fiction to 1514,
    R.string.Government to 1500,
    R.string.History to 1307,
    R.string.Health_Fitness to 1306,
    R.string.Kids_Family to 1305,
    R.string.Leisure to 1324,
    R.string.Music to 1311,
    R.string.News to 1312,
    R.string.Religion_Spirituality to 1322,
    R.string.Science to 1315,
    R.string.Society_Culture to 1325,
    R.string.Sports to 1316,
    R.string.Technology to 1318,
    R.string.True_Crime to 1488,
    R.string.TV_Film to 1309,
)

private const val TAG = "DiscoveryScreen"
private const val NUM_OF_TOP_PODCASTS = 100

