package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.Feeds.getFeed
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.storage.utils.DurationConverter.durationInHours
import ac.mdiq.podcini.storage.utils.DurationConverter.getDurationStringShort
import ac.mdiq.podcini.ui.activity.MainActivity
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.DatesFilterDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.ui.compose.EpisodeVM
import ac.mdiq.podcini.ui.utils.feedOnDisplay
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

private val prefs: SharedPreferences by lazy { getAppContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

class StatisticsVM(val context: Context, val lcScope: CoroutineScope) {

    internal var statisticsState by mutableIntStateOf(0)
    internal val selectedTabIndex = mutableIntStateOf(0)
    internal var showFilter by mutableStateOf(false)

    var date: LocalDate by mutableStateOf(LocalDate.now())
    var statsOfDay by mutableStateOf(StatisticsResult())
    var statsResult by mutableStateOf(StatisticsResult())

    var chartData by mutableStateOf<LineChartData?>(null)
    var timeFilterFrom by mutableLongStateOf(prefs.getLong(Prefs.FilterFrom.name, 0L))
    var timeFilterTo by mutableLongStateOf(prefs.getLong(Prefs.FilterTo.name, Long.MAX_VALUE))
    var numDays by mutableIntStateOf(1)
    var periodText by mutableStateOf("")

    internal var showTodayStats by mutableStateOf(false)

    var monthlyStats = mutableStateListOf<MonthlyStatisticsItem>()
    var monthlyMaxDataValue by mutableFloatStateOf(1f)
    var monthVMS = mutableStateListOf<EpisodeVM>()

    internal var downloadstatsData by mutableStateOf<StatisticsResult?>(null)
    internal var downloadChartData by mutableStateOf<LineChartData?>(null)

    internal val showResetDialog = mutableStateOf(false)

    internal fun setTimeFilter(timeFilterFrom_: Long, timeFilterTo_: Long) {
        timeFilterFrom = timeFilterFrom_
        timeFilterTo = timeFilterTo_
        prefs.edit()?.putLong(Prefs.FilterFrom.name, timeFilterFrom_)?.putLong(Prefs.FilterTo.name, timeFilterTo_)?.apply()
    }

    fun numOfDays(): Int {
        val dateFrom = Date(if (timeFilterFrom != 0L) timeFilterFrom else statsResult.oldestDate).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val dateTo = (if (timeFilterTo != Long.MAX_VALUE) Date(timeFilterTo) else Date()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(dateFrom, dateTo).toInt()
    }

    fun loadDailyStats() {
        Logd(TAG, "loadDailyStats")
        statsOfDay = getStatistics(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    internal fun loadStatistics() {
        loadDailyStats()
        try {
            Logd(TAG, "loadStatistics")
            statsResult = getStatistics(timeFilterFrom, timeFilterTo)
            statsResult.statsItems.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.timePlayed.compareTo(stat1.item.timePlayed) }
            val chartValues = MutableList(statsResult.statsItems.size){0f}
            for (i in statsResult.statsItems.indices) {
                val stat = statsResult.statsItems[i]
                chartValues[i] = stat.item.timePlayed.toFloat()
            }
            chartData = LineChartData(chartValues)
            numDays = numOfDays()
            periodText = run {
                    val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
                    val dateFrom = dateFormat.format(Date(if (timeFilterFrom != 0L) timeFilterFrom else statsResult.oldestDate))
                    val dateTo = dateFormat.format(if (timeFilterTo != Long.MAX_VALUE) Date(timeFilterTo) else Date())
                    "$dateFrom to $dateTo"
                }
        } catch (error: Throwable) { Logs(TAG, error) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vm = remember { StatisticsVM(context, scope) }

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
            vm.chartData = null
            vm.statsOfDay = StatisticsResult()
            vm.statsResult = StatisticsResult()
            vm.monthlyStats.clear()
            vm.downloadstatsData = null
            vm.downloadChartData = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        TopAppBar(title = { Text("") },
            navigationIcon = { IconButton(onClick = { MainActivity.openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chart_box), contentDescription = "Open Drawer") } },
            actions = {
                if (vm.selectedTabIndex.value <= 2) {
                    IconButton(onClick = { vm.showFilter = true }) {
                        val filterColor = if (vm.timeFilterFrom > 0L || vm.timeFilterTo < Long.MAX_VALUE) Color.Green else MaterialTheme.colorScheme.onSurface
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = filterColor, contentDescription = "filter")
                    }
                }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (vm.selectedTabIndex.value == 0 || vm.selectedTabIndex.value == 1) DropdownMenuItem(text = { Text(stringResource(R.string.statistics_reset_data)) }, onClick = {
                        vm.showResetDialog.value = true
                        expanded = false
                    })
                }
            }
        )
    }

    @Composable
    fun HorizontalLineChart(lineChartData: LineChartData) {
        val data = lineChartData.values
        val total = data.sum()
        Canvas(modifier = Modifier.fillMaxWidth().height(20.dp).padding(start = 20.dp, end = 20.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            var startX = 0f
            for (index in data.indices) {
                val segmentWidth = (data[index] / total) * canvasWidth
                drawRect(color = lineChartData.getComposeColorOfItem(index), topLeft = Offset(startX, 0f), size = Size(segmentWidth, canvasHeight))
                startX += segmentWidth
            }
        }
    }

    @Composable
    fun FeedsList(statisticsData: StatisticsResult, lineChartData: LineChartData, infoCB: (FeedStatistics)->String) {
        val lazyListState = rememberLazyListState()
        var showFeedStats by remember { mutableStateOf(false) }
        var feedId by remember { mutableLongStateOf(0L) }
        var feedTitle by remember { mutableStateOf("") }
        if (showFeedStats) FeedStatisticsDialog(feedTitle, feedId, vm.timeFilterFrom, vm.timeFilterTo, showOpenFeed = true) { showFeedStats = false }
        val textColor = MaterialTheme.colorScheme.onSurface
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(statisticsData.statsItems, key = { _, item -> item.feed.id }) { index, item ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface).fillMaxWidth().clickable(onClick = {
                    feedId = item.feed.id
                    feedTitle = item.feed.title ?: "No title"
                    showFeedStats = true
                })) {
                    val imgLoc = remember(item) { item.feed.imageUrl }
                    AsyncImage(model = ImageRequest.Builder(context).data(imgLoc).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build(),
                        contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher),
                        modifier = Modifier.width(40.dp).height(40.dp).padding(end = 5.dp)
                    )
                    Column {
                        Text(item.feed.title?:"No title", color = textColor, style = MaterialTheme.typography.bodyLarge.merge())
                        Row {
                            val chipColor = lineChartData.getComposeColorOfItem(index)
                            Text("⬤", style = MaterialTheme.typography.bodyMedium.merge(), color = chipColor)
                            Text(infoCB(item), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun TodayStatsDialog(onDismissRequest: () -> Unit) {
        val vms = remember { mutableStateListOf<EpisodeVM>() }
        LaunchedEffect(vm.statsOfDay) {
            vms.clear()
            for (e in vm.statsOfDay.episodes) vms.add(EpisodeVM(e, TAG))
        }
        AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },  confirmButton = {},
            text = { EpisodeLazyColumn(LocalContext.current, vms = vms, showCoverImage = false, showActionButtons = false) },
            dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } } )
    }
    if (vm.showTodayStats) TodayStatsDialog { vm.showTodayStats = false }

    @Composable
    fun OverviewNumbers(stats: StatisticsItem, nd: Int = 1) {
        fun formatEpisodes(num: Int): String {
            if (nd == 1) return num.toString()
            @SuppressLint("DefaultLocale")
            return String.format("%.2f", 1f*num/nd)
        }
        val textColor = MaterialTheme.colorScheme.onSurface
        Row {
            Spacer(Modifier.weight(0.3f))
            Text(stringResource(R.string.total) + ": " + formatEpisodes(stats.episodesTotal), color = textColor)
            Spacer(Modifier.weight(0.1f))
            Text(getDurationStringShort(stats.durationTotal*1000/nd, true), color = textColor)
            Spacer(Modifier.weight(0.2f))
            Text( stringResource(R.string.spent) + ": " + getDurationStringShort(stats.timeSpent*1000/nd, true), color = textColor)
            Spacer(Modifier.weight(0.3f))
        }
        if (stats.episodesStarted > 0 || stats.episodesPlayed > 0) Row {
            Spacer(Modifier.weight(0.3f))
            if (stats.episodesStarted > 0) {
                Text(stringResource(R.string.started) + ": " + formatEpisodes(stats.episodesStarted), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.timePlayed*1000/nd, true), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationStarted*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesPlayed > 0) {
                Text(stringResource(R.string.played) + ": " + formatEpisodes(stats.episodesPlayed), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationPlayed*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.3f))
        }
        Row {
            Spacer(Modifier.weight(0.3f))
            if (stats.episodesSkipped > 0) {
                Text( stringResource(R.string.skipped) + ": " + formatEpisodes(stats.episodesSkipped), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationSkipped*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesPassed > 0) Text(stringResource(R.string.passed) + ": " + formatEpisodes(stats.episodesPassed), color = textColor)
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesIgnored > 0) Text( stringResource(R.string.ignored) + ": " + formatEpisodes(stats.episodesIgnored), color = textColor)
            Spacer(Modifier.weight(0.3f))
        }
    }

    @Composable
    fun Overview() {
        LaunchedEffect(vm.statisticsState) { if (vm.chartData == null) vm.loadStatistics() }
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    vm.date = vm.date.minusDays(7)
                    vm.loadDailyStats()
                }) { Text("-7", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(0.2f))
                IconButton(onClick = {
                    vm.date = vm.date.minusDays(1)
                    vm.loadDailyStats()
                }) { Text("-", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(0.2f))
                TextButton(onClick = { vm.showTodayStats = true }) {
                    val dateText by derivedStateOf {
                        if (vm.date == LocalDate.now()) context.getString(R.string.statistics_today) else {
                            val locale = Locale.getDefault()
                            val dateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMM d yyyy"), locale)
                            dateFormat.format(Date.from(vm.date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                        }
                    }
                    Text(dateText, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.weight(0.2f))
                IconButton(onClick = {
                    if (vm.date.isBefore(LocalDate.now())) {
                        vm.date = vm.date.plusDays(1)
                        vm.loadDailyStats()
                    }
                }) {
                    val plusText by derivedStateOf { if (vm.date >= LocalDate.now()) "" else "+" }
                    Text(plusText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(0.2f))
                IconButton(onClick = {
                    if (vm.date.plusDays(6).isBefore(LocalDate.now())) {
                        vm.date = vm.date.plusDays(7)
                        vm.loadDailyStats()
                    }
                }) {
                    val plusText by derivedStateOf { if (vm.date.plusDays(6) >= LocalDate.now()) "" else "+7" }
                    Text(plusText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
            }
            OverviewNumbers(vm.statsOfDay.statTotal)
            TextButton(onClick = { vm.showFilter = true }, modifier = Modifier.padding(top = 20.dp)) { Text(vm.periodText, style = MaterialTheme.typography.headlineSmall) }
            OverviewNumbers(vm.statsResult.statTotal)
            Text(stringResource(R.string.daily_average), style = MaterialTheme.typography.headlineSmall, color = textColor, modifier = Modifier.padding(top = 20.dp))
            OverviewNumbers(vm.statsResult.statTotal, vm.numDays)
        }
    }

    @Composable
    fun Subscriptions() {
        LaunchedEffect(vm.statisticsState) { if (vm.statisticsState >= 0 && vm.chartData == null) vm.loadStatistics() }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(10.dp))
            if (vm.chartData != null) HorizontalLineChart(vm.chartData!!)
            Spacer(Modifier.height(10.dp))
            if (vm.chartData != null) FeedsList(vm.statsResult, vm.chartData!!) { stat ->
                context.getString(R.string.duration) + ": " + durationInHours(stat.item.timePlayed) + " \t " + context.getString(R.string.spent) + ": " + durationInHours(stat.item.timeSpent)
            }
        }
    }

    @Composable
    fun MonthlyStats() {
        fun loadMonthlyStatistics() {
            try {
                val months: MutableList<MonthlyStatisticsItem> = mutableListOf()
                val medias = realm.query(Episode::class).query("lastPlayedTime > 0").find()
                val groupdMedias = medias.groupBy {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = it.lastPlayedTime
                    "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}"
                }
                val orderedGroupedItems = groupdMedias.toList().sortedBy {
                    val (key, _) = it
                    val year = key.substringBefore("-").toInt()
                    val month = key.substringAfter("-").toInt()
                    year * 12 + month
                }.toMap()
                for (key in orderedGroupedItems.keys) {
                    val episodes = orderedGroupedItems[key] ?: continue
                    val mItem = MonthlyStatisticsItem()
                    mItem.year = key.substringBefore("-").toInt()
                    mItem.month = key.substringAfter("-").toInt()
                    var dur = 0L
                    var spent = 0L
                    for (e in episodes) {
                        dur += when {
                            e.playState == PlayState.PLAYED.code || e.playState == PlayState.SKIPPED.code
                                    || e.playState == PlayState.PASSED.code || e.playState == PlayState.IGNORED.code -> e.duration
                            e.playedDuration > 0 -> e.playedDuration
                            else -> e.position
                        }
                        spent += e.timeSpent
                    }
                    mItem.timePlayed = dur
                    mItem.timeSpent = spent
                    months.add(mItem)
                }
                vm.monthlyStats = months.toMutableStateList()
                for (item in vm.monthlyStats) vm.monthlyMaxDataValue = max(vm.monthlyMaxDataValue.toDouble(), item.timePlayed.toDouble()).toFloat()
                Logd(TAG, "maxDataValue: ${vm.monthlyMaxDataValue}")
            } catch (error: Throwable) { Logs(TAG, error) }
        }
        @Composable
        fun ClickableBarChart(bars: List<MonthlyStatisticsItem>, onBarClick: (Int) -> Unit) {
            val barRects = remember { mutableStateListOf<Rect>() }
            val barWidth = 50f
            val spaceBetweenBars = 20f
            Canvas(modifier = Modifier.width((bars.size * (barWidth + spaceBetweenBars)).dp).height(200.dp).pointerInput(Unit) {
                detectTapGestures { offset ->
                    barRects.forEachIndexed { index, rect ->
                        if (rect.contains(offset)) {
                            onBarClick(index)
                            return@detectTapGestures
                        }
                    }
                }
            }) {
                val maxHeight = size.height
                barRects.clear()
                bars.forEachIndexed { index, value ->
                    val left = spaceBetweenBars + index * (barWidth + spaceBetweenBars)
                    val top = maxHeight * (1 - value.timePlayed / vm.monthlyMaxDataValue)
                    val right = left + barWidth
                    val bottom = maxHeight
                    val barRect = Rect(left, top, right, bottom)
                    barRects.add(barRect)
                    drawRect(color = Color.Blue, topLeft = barRect.topLeft, size = barRect.size)
                }
            }
        }
        fun onMonthClicked(index: Int) {
            val year = vm.monthlyStats[index].year
            val month = vm.monthlyStats[index].month
            val yearMonth = YearMonth.of(year, month)
            val start = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val end = yearMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
            val data = getStatistics(start, end)
            vm.monthVMS.clear()
            for (e in data.episodes) vm.monthVMS.add(EpisodeVM(e, TAG))
        }

        @Composable
        fun MonthList() {
            val lazyListState = rememberLazyListState()
            val textColor = MaterialTheme.colorScheme.onSurface
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.monthlyStats) { index, _ ->
                    Row(Modifier.background(MaterialTheme.colorScheme.surface).clickable(onClick = { onMonthClicked(index) })) {
                        Column {
                            val monthString = String.format(Locale.getDefault(), "%d-%d", vm.monthlyStats[index].year, vm.monthlyStats[index].month)
                            Text(monthString, color = textColor, style = MaterialTheme.typography.bodyLarge.merge())
                            val hoursString = stringResource(R.string.duration) + ": " + String.format(Locale.getDefault(), "%.1f ", vm.monthlyStats[index].timePlayed / 3600000.0f) + stringResource(R.string.time_hours) +
                                    " \t " + stringResource(R.string.spent) + ": " + String.format(Locale.getDefault(), "%.1f ", vm.monthlyStats[index].timeSpent / 3600000.0f) + stringResource(R.string.time_hours)
                            Text(hoursString, color = textColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (vm.monthVMS.isNotEmpty()) AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { vm.monthVMS.clear() },  confirmButton = {},
            text = { EpisodeLazyColumn(LocalContext.current, vms = vm.monthVMS, showCoverImage = false, showActionButtons = false) },
            dismissButton = { TextButton(onClick = { vm.monthVMS.clear() }) { Text(stringResource(R.string.cancel_label)) } } )

        if (vm.statisticsState >= 0 && vm.monthlyStats.isEmpty()) loadMonthlyStatistics()
        Column {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp)) { ClickableBarChart(vm.monthlyStats) { index -> onMonthClicked(index) } }
            Spacer(Modifier.height(20.dp))
            MonthList()
        }
    }

    @Composable
    fun DownloadStats() {
        fun loadDownloadStatistics() {
            vm.downloadstatsData = getStatistics(0, Long.MAX_VALUE, forDL = true)
            vm.downloadstatsData!!.statsItems.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.totalDownloadSize.compareTo(stat1.item.totalDownloadSize) }
            val dataValues = MutableList(vm.downloadstatsData!!.statsItems.size) { 0f }
            for (i in vm.downloadstatsData!!.statsItems.indices) {
                val stat = vm.downloadstatsData!!.statsItems[i]
                dataValues[i] = stat.item.totalDownloadSize.toFloat()
            }
            vm.downloadChartData = LineChartData(dataValues)
        }
        if (vm.downloadstatsData == null) loadDownloadStatistics()
        val textColor = MaterialTheme.colorScheme.onSurface
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.total_size_downloaded_podcasts), color = textColor, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            Text(Formatter.formatShortFileSize(context, vm.downloadChartData!!.sum.toLong()), color = textColor)
            Spacer(Modifier.height(5.dp))
            if (vm.downloadChartData != null) HorizontalLineChart(vm.downloadChartData!!)
            Spacer(Modifier.height(5.dp))
            if (vm.downloadstatsData != null && vm.downloadChartData != null) FeedsList(vm.downloadstatsData!!, vm.downloadChartData!!) { stat ->
                ("${Formatter.formatShortFileSize(context, stat.item.totalDownloadSize)} • " + String.format(Locale.getDefault(), "%d%s", stat.item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))
            }
        }
    }

    ComfirmDialog(titleRes = R.string.statistics_reset_data, message = stringResource(R.string.statistics_reset_data_msg), showDialog = vm.showResetDialog) {
        vm.setTimeFilter(0L, Long.MAX_VALUE)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (realm.query(Episode::class).query("playedDuration != 0 || timeSpent != 0").count().find() > 0) {
                    realm.write {
                        var mediaAll = query(Episode::class).query("playedDuration != 0 || timeSpent != 0").find()
                        if (mediaAll.isNotEmpty()) {
                            Logd(TAG, "mediaAll: ${mediaAll.size}")
                            for (m in mediaAll) {
                                Logd(TAG, "m: ${m.title}")
                                m.playedDuration = 0
                                m.timeSpent = 0
                                m.timeSpentOnStart = 0
                                m.startTime = 0
                            }
                        }
                    }
                }
                vm.chartData = null
                vm.statisticsState++
            } catch (error: Throwable) { Logs(TAG, error) }
        }
    }
    if (vm.showFilter) DatesFilterDialog(from = vm.timeFilterFrom, to = vm.timeFilterTo, oldestDate = vm.statsResult.oldestDate, onDismissRequest = {vm.showFilter = false} ) { from, to ->
        Logd(TAG, "confirm DatesFilterDialog ${vm.timeFilterFrom} $from ${vm.timeFilterTo} $to")
        vm.setTimeFilter(from, to)
        vm.chartData = null
        vm.statisticsState++
    }
    val tabTitles = listOf(R.string.overview, R.string.subscriptions_label, R.string.months_statistics_label, R.string.downloads_label)
    Scaffold(topBar = { MyTopAppBar() }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                tabTitles.forEachIndexed { index, titleRes ->
                    Tab(modifier = Modifier.wrapContentWidth().padding(horizontal = 2.dp, vertical = 4.dp).background(shape = RoundedCornerShape(8.dp),
                        color = if (vm.selectedTabIndex.value == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else { Color.Transparent }),
                        selected = vm.selectedTabIndex.value == index,
                        onClick = { vm.selectedTabIndex.value = index },
                        text = { Text(text = stringResource(titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            color = if (vm.selectedTabIndex.value == index) MaterialTheme.colorScheme.primary else { MaterialTheme.colorScheme.onSurface }) }
                    )
                }
            }
            when (vm.selectedTabIndex.value) {
                0 -> Overview()
                1 -> Subscriptions()
                2 -> MonthlyStats()
                3 -> DownloadStats()
            }
        }
    }
}

class LineChartData(val values: MutableList<Float>) {
    val sum: Float

    init {
        var valueSum = 0f
        for (datum in values) valueSum += datum
        this.sum = valueSum
    }
    private fun getPercentageOfItem(index: Int): Float {
        if (sum == 0f) return 0f
        return values[index] / sum
    }
    private fun isLargeEnoughToDisplay(index: Int): Boolean = getPercentageOfItem(index) > 0.01
    fun getComposeColorOfItem(index: Int): Color {
        if (!isLargeEnoughToDisplay(index)) return Color.Gray
        return Color(COLOR_VALUES[index % COLOR_VALUES.size])
    }
    companion object {
        private val COLOR_VALUES = mutableListOf(-0xc88a1a, -0x1ae3dd, -0x6800, -0xda64dc, -0x63d850,
            -0xff663a, -0x22bb89, -0x995600, -0x47d1d2, -0xce9c6b,
            -0x66bb67, -0xdd5567, -0x5555ef, -0x99cc34, -0xff8c1a)
    }
}

class StatisticsItem {
    var timePlayed: Long = 0L  // in seconds, Respects speed, listening twice, ...
    var timeSpent: Long = 0L  // in seconds, actual time spent playing

    var durationTotal: Long = 0L   // total time, in seconds
    var durationStarted: Long = 0L  // in seconds, total duration of episodes started playing
    var durationPlayed: Long = 0L
    var durationSkipped: Long = 0L
    var durationPassed: Long = 0L
    var durationIgnored: Long = 0L

    var episodesTotal: Int = 0    // Number of episodes.
    var episodesStarted: Int = 0  // Episodes that are actually played.
    var episodesPlayed: Int = 0
    var episodesSkipped: Int = 0
    var episodesPassed: Int = 0
    var episodesIgnored: Int = 0

    var totalDownloadSize: Long = 0L  // Simply sums up the size of download podcasts.
    var episodesDownloadCount: Long = 0L   // Stores the number of episodes downloaded.
}

class FeedStatistics {
    var feed: Feed = Feed()
    val item: StatisticsItem = StatisticsItem()
}

class MonthlyStatisticsItem {
    var year: Int = 0
    var month: Int = 0
    var timePlayed: Long = 0
    var timeSpent: Long = 0
}

class StatisticsResult {
    var statsItems: MutableList<FeedStatistics> = mutableListOf()
    var statTotal: StatisticsItem = StatisticsItem()
    var episodes: List<Episode> = listOf()
    var oldestDate: Long = 0L
}

private const val TAG = "StatisticsScreen"

private const val PREF_NAME: String = "StatisticsActivityPrefs"

enum class Prefs {
    FilterFrom,
    FilterTo
}

private fun getStatistics(timeFrom: Long, timeTo: Long, feedId: Long = 0L, forDL: Boolean = false): StatisticsResult {
    Logd(TAG, "getStatistics called")
    val qs2 = when {
        forDL -> ""
        else -> {
            var qs1 = "(lastPlayedTime > $timeFrom AND lastPlayedTime < $timeTo)"
            var qs3 = "playStateSetTime > $timeFrom AND playStateSetTime < $timeTo"
            qs1 += " OR ($qs3 AND playState == ${PlayState.PLAYED.code})"
            qs1 += " OR ($qs3 AND playState == ${PlayState.SKIPPED.code})"
            qs1 += " OR ($qs3 AND playState == ${PlayState.PASSED.code})"
            qs1 += " OR ($qs3 AND playState == ${PlayState.IGNORED.code})"
            qs1
        }
    }
    val queryString = when {
        forDL -> "downloaded == true"
        feedId != 0L -> "feedId == $feedId AND ($qs2)"
        else -> "($qs2)"
    }
    val medias = realm.query(Episode::class).query(queryString).find()
    Logd(TAG, "getStatistics queryString: [${medias.size}] $queryString")

    val result = StatisticsResult()
    result.episodes = medias

    val groupdMedias = medias.groupBy { it.feedId ?: 0L }
    result.oldestDate = Long.MAX_VALUE
    for ((fid, episodes) in groupdMedias) {
        val feed = getFeed(fid, false) ?: continue
        val fStat = FeedStatistics()
        fStat.feed = feed
        fStat.item.episodesTotal = episodes.size
        for (e in episodes) {
            if (feedId != 0L || !forDL) {
                if (e.lastPlayedTime > 0L && e.lastPlayedTime < result.oldestDate) result.oldestDate = e.lastPlayedTime
                if (e.playStateSetTime > 0L && e.playStateSetTime < result.oldestDate) result.oldestDate = e.playStateSetTime
                if (e.duration > 0) fStat.item.durationTotal += e.duration
                else Loge(TAG, "episode has negative duration: ${e.duration} state: ${e.playState} ${e.title}")
                Logd(TAG, "getStatistics e.playState: ${e.playState} e.timeSpent: ${e.timeSpent} ${e.title}")
                if (e.playState == PlayState.PLAYED.code) {
                    fStat.item.episodesPlayed++
                    fStat.item.durationPlayed += e.duration
                }
                if (e.playState == PlayState.SKIPPED.code) {
                    fStat.item.episodesSkipped++
                    fStat.item.durationSkipped += e.duration
                }
                if (e.playState == PlayState.PASSED.code) {
                    fStat.item.episodesPassed++
                    fStat.item.durationPassed += e.duration
                }
                if (e.playState == PlayState.IGNORED.code) {
                    fStat.item.episodesIgnored++
                    fStat.item.durationIgnored += e.duration
                }
                if (e.playedDuration > 0) {
                    fStat.item.episodesStarted++
                    fStat.item.timePlayed += e.playedDuration
                    fStat.item.durationStarted += e.duration
                    fStat.item.timeSpent += e.timeSpent
                }
            }
            if (feedId != 0L || forDL) {
                if (e.downloaded) {
                    fStat.item.episodesDownloadCount += 1
                    fStat.item.totalDownloadSize += e.size
                }
            }
        }
        fStat.item.timePlayed /= 1000
        fStat.item.timeSpent /= 1000
        fStat.item.durationTotal /= 1000
        fStat.item.durationStarted /= 1000
        fStat.item.durationPlayed /= 1000
        fStat.item.durationSkipped /= 1000
        fStat.item.durationPassed /= 1000
        fStat.item.durationIgnored /= 1000
        result.statsItems.add(fStat)
    }
    if (result.statsItems.isNotEmpty()) {
        val item = StatisticsItem()
        result.statTotal = result.statsItems.fold(item) { acc, stats ->
            val properties = StatisticsItem::class.memberProperties
            properties.forEach { prop ->
                val setter = prop as? KMutableProperty1<StatisticsItem, *> ?: throw IllegalStateException("Property ${prop.name} is not mutable")
                val propertyType = prop.returnType.jvmErasure
                when (propertyType) {
                    Int::class -> {
                        @Suppress("UNCHECKED_CAST")
                        val typedSetter = setter as KMutableProperty1<StatisticsItem, Int>
                        val accValue = typedSetter.get(acc)
                        val statsValue = typedSetter.get(stats.item)
                        typedSetter.set(item, accValue + statsValue)
                    }
                    Long::class -> {
                        @Suppress("UNCHECKED_CAST")
                        val typedSetter = setter as KMutableProperty1<StatisticsItem, Long>
                        val accValue = typedSetter.get(acc)
                        val statsValue = typedSetter.get(stats.item)
                        typedSetter.set(item, accValue + statsValue)
                    }
                    else -> throw IllegalStateException("Unsupported property type: ${prop.name}")
                }
            }
            item
        }
    }
    return result
}

@Composable
fun FeedStatisticsDialog(title: String, feedId: Long, timeFrom: Long, timeTo: Long, showOpenFeed: Boolean = false, onDismissRequest: () -> Unit) {
    var fStat by remember { mutableStateOf<FeedStatistics?>(null) }
    val vms = remember { mutableStateListOf<EpisodeVM>() }
    fun loadStatistics() {
        try {
            val data = getStatistics(timeFrom, timeTo, feedId)
            data.statsItems.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.timePlayed.compareTo(stat1.item.timePlayed) }
            if (data.statsItems.isNotEmpty()) fStat = data.statsItems[0]
            vms.clear()
            for (e in data.episodes) vms.add(EpisodeVM(e, TAG))
        } catch (error: Throwable) { Logs(TAG, error) }
    }
    LaunchedEffect(Unit) { loadStatistics() }
    AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { onDismissRequest() },
        text = {
            val textColor = MaterialTheme.colorScheme.onSurface
            val context = LocalContext.current
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Row {
                    Text(stringResource(R.string.statistics_episodes_started_total), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.getDefault(), "%d / %d", fStat?.item?.episodesStarted ?: 0, fStat?.item?.episodesTotal ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_length_played), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort(fStat?.item?.durationStarted ?: 0, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_time_played), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort(fStat?.item?.timePlayed ?: 0, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_time_spent), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort(fStat?.item?.timeSpent ?: 0, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_total_duration), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort(fStat?.item?.durationTotal ?: 0, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_episodes_on_device), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.getDefault(), "%d", fStat?.item?.episodesDownloadCount ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_space_used), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Formatter.formatShortFileSize(context, fStat?.item?.totalDownloadSize ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Box(modifier = Modifier.weight(1f)) { EpisodeLazyColumn(context, vms = vms, showCoverImage = false, showActionButtons = false) }
            }
        },
        confirmButton = { if (showOpenFeed) TextButton(onClick = {
            val feed = getFeed(feedId)
            if (feed != null) {
                feedOnDisplay = feed
                mainNavController.navigate(Screens.FeedDetails.name)
            }
            onDismissRequest()
        }) { Text(stringResource(R.string.open_podcast))} },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}


