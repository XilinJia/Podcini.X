package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.searcher.ItunesTopListLoader
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.OnlineFeedItem
import ac.mdiq.podcini.ui.compose.SpinnerExternalSet
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


class DiscoveryVM(val context: Context, val lcScope: CoroutineScope) {
    val prefs: SharedPreferences by lazy { context.getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE) }

    init {
        lcScope.launch(Dispatchers.IO) { prefs }
    }
}

@Composable
fun TopChartFeeds() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
    val vm = remember { DiscoveryVM(context, scope) }

    var topList = remember { listOf<PodcastSearchResult>() }

    var countryCode by  remember { mutableStateOf("US") }
    var needsConfirm by remember { mutableStateOf(false) }

    var searchResults = remember { mutableStateListOf<PodcastSearchResult>() }
    var errorText by remember { mutableStateOf("") }
    var retryQerry by remember { mutableStateOf("") }
    var showProgress by remember { mutableStateOf(true) }
    var noResultText by remember { mutableStateOf("") }

    val spinnerTexts = remember { genres.keys.map { context.getString(it) } }
    var curIndex by remember {  mutableIntStateOf(0) }
    var curGenre by remember(curIndex) { mutableStateOf(genres[curIndex] ?: 0) }

    fun loadToplist(country: String?, genre: Int) {
        searchResults.clear()
        errorText = ""
        retryQerry = ""
        noResultText = ""
        showProgress = true
        if (BuildConfig.FLAVOR == "free" && needsConfirm) {
            errorText = ""
            retryQerry = context.getString(R.string.discover_confirm)
            noResultText = ""
            showProgress = false
            return
        }

        val loader = ItunesTopListLoader(context)
        scope.launch(Dispatchers.IO) {
            try {
                val podcasts = loader.loadToplist(country?:"", genre, NUM_OF_TOP_PODCASTS)
                withContext(Dispatchers.Main) {
                    showProgress = false
                    topList = podcasts
                    searchResults.clear()
                    if (topList.isNotEmpty()) {
                        searchResults.addAll(topList)
                        noResultText = ""
                    } else noResultText = context.getString(R.string.no_results_for_query)
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    countryCode = vm.prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, Locale.getDefault().country)!!
                    needsConfirm = vm.prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)
                    loadToplist(countryCode, curGenre)
                }
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            searchResults.clear()
            topList = listOf()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @Composable
    fun SelectCountryDialog(onDismiss: () -> Unit) {
        val countryNameCodeMap: MutableMap<String, String> = remember { hashMapOf() }
        val countryCodeNameMap: MutableMap<String?, String> = remember { hashMapOf() }
        val countryNamesSort = remember { mutableStateListOf<String>() }
        var selectedCountry by remember { mutableStateOf("") }
        var textInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val countryCodeArray: List<String> = listOf(*Locale.getISOCountries())
            for (code in countryCodeArray) {
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
        }
        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun CountrySelection() {
            val filteredCountries = remember { countryNamesSort.toMutableStateList() }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                TextField(value = textInput, modifier = Modifier.fillMaxWidth().padding(20.dp).menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, false), readOnly = false,
                    onValueChange = { input ->
                        textInput = input
                        if (textInput.length > 1) {
                            filteredCountries.clear()
                            filteredCountries.addAll(countryNamesSort.filter { it.contains(input, ignoreCase = true) }.take(5))
                            Logd(TAG, "input: $input filteredCountries: ${filteredCountries.size}")
                            expanded = filteredCountries.isNotEmpty()
                        }
                    },
                    label = { Text(stringResource(id = R.string.select_country)) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filteredCountries.forEach { country ->
                        DropdownMenuItem(text = { Text(text = country) }, onClick = {
                            selectedCountry = country
                            textInput = country
                            expanded = false
                        })
                    }
                }
            }
        }
        AlertDialog(modifier = Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.pref_custom_media_dir_title), style = CustomTextStyles.titleCustom) },
            text = { CountrySelection() },
            confirmButton = {
                TextButton(onClick = {
                    if (countryNameCodeMap.containsKey(selectedCountry)) countryCode = countryNameCodeMap[selectedCountry]!!
                    vm.prefs.edit { putString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE, countryCode) }
                    EventFlow.postEvent(FlowEvent.DiscoveryDefaultUpdateEvent())
                    loadToplist(countryCode, curGenre)
                    onDismiss()
                }) { Text(stringResource(R.string.confirm_label)) }
            },
            dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel_label)) } }
        )
    }

    var showSelectCounrty by remember { mutableStateOf(false) }
    if (showSelectCounrty) SelectCountryDialog { showSelectCounrty = false }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val buttonColor = Color(0xDDFFD700)
        Box {
            TopAppBar(title = {
                Row {
                    SpinnerExternalSet(items = spinnerTexts, selectedIndex = curIndex) { index: Int ->
                        curIndex = index
                        curGenre = genres[genres.keys.toList()[index]] ?: 0
                        Logd(TAG, "SpinnerExternalSet $curIndex curGenre: $curGenre")
                        loadToplist(countryCode, curGenre)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(countryCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end=20.dp).clickable(onClick = { showSelectCounrty = true }))
                } },
                navigationIcon = { IconButton(onClick = { if (navController.previousBackStackEntry != null) navController.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "") } }, )
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    
    val textColor = MaterialTheme.colorScheme.onSurface
    
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        ConstraintLayout(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val (gridView, progressBar, empty, txtvError, butRetry, powered) = createRefs()
            if (showProgress) CircularProgressIndicator(progress = { 0.6f }, strokeWidth = 10.dp, modifier = Modifier.size(50.dp).constrainAs(progressBar) { centerTo(parent) })
            val lazyListState = rememberLazyListState()
            if (searchResults.isNotEmpty()) LazyColumn(state = lazyListState, modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                .constrainAs(gridView) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchResults.size) { index -> OnlineFeedItem(searchResults[index]) }
            }
            if (searchResults.isEmpty()) Text(noResultText, color = textColor, modifier = Modifier.constrainAs(empty) { centerTo(parent) })
            if (errorText.isNotEmpty()) Text(errorText, color = textColor, modifier = Modifier.constrainAs(txtvError) { centerTo(parent) })
            if (retryQerry.isNotEmpty()) Button(
                modifier = Modifier.padding(16.dp).constrainAs(butRetry) { top.linkTo(txtvError.bottom) },
                onClick = {
                    if (needsConfirm) {
                        vm.prefs.edit { putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false) }
                        needsConfirm = false
                    }
                    loadToplist(countryCode, curGenre)
                },
            ) { Text(stringResource(id = R.string.retry_label)) }
            Text(context.getString(R.string.search_powered_by, "Apple"), color = Color.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(
                Color.LightGray)
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

