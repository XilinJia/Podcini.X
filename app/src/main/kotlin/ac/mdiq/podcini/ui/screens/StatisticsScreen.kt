package ac.mdiq.podcini.ui.screens

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.LocalNavController
import ac.mdiq.podcini.ui.compose.ComfirmDialog
import ac.mdiq.podcini.ui.compose.DatesFilterDialog
import ac.mdiq.podcini.ui.compose.EpisodeLazyColumn
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure


class StatisticsVM(val context: Context, val lcScope: CoroutineScope) {

    internal var statisticsState by mutableIntStateOf(0)
    internal val selectedTabIndex = mutableIntStateOf(0)
    internal var showFilter by mutableStateOf(false)

    var date: LocalDate by mutableStateOf(LocalDate.now())
    var statsOfDay by mutableStateOf(StatisticsResult())
    var statsResult by mutableStateOf(StatisticsResult())

    var chartData by mutableStateOf<LineChartData?>(null)
    var timeFilterFrom by mutableLongStateOf(appAttribs.statisticsFrom)
    var timeFilterTo by mutableLongStateOf(appAttribs.statisticsUntil.takeIf { it != 0L } ?: Long.MAX_VALUE)
    var numDays by mutableIntStateOf(1)
    var periodText by mutableStateOf("")

    internal var showTodayStats by mutableStateOf(false)

    val monthStats = mutableStateListOf<MonthlyStatistics>()
    var monthlyMaxDataValue by mutableFloatStateOf(1f)

    internal var downloadstatsData by mutableStateOf<StatisticsResult?>(null)
    internal var downloadChartData by mutableStateOf<LineChartData?>(null)

    internal val showResetDialog = mutableStateOf(false)

    internal fun setTimeFilter(timeFilterFrom_: Long, timeFilterTo_: Long) {
        timeFilterFrom = timeFilterFrom_
        timeFilterTo = timeFilterTo_
        upsertBlk(appAttribs) {
            it.statisticsFrom = timeFilterFrom_
            it.statisticsUntil = timeFilterTo_
        }
    }

    fun numOfDays(): Int {
        val dateFrom = Date(if (timeFilterFrom != 0L) timeFilterFrom else statsResult.oldestDate).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val dateTo = (if (timeFilterTo != Long.MAX_VALUE) Date(timeFilterTo) else Date()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(dateFrom, dateTo).toInt() + 1
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
            statsResult.feedStats.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.timePlayed.compareTo(stat1.item.timePlayed) }
            val chartValues = MutableList(statsResult.feedStats.size){0f}
            for (i in statsResult.feedStats.indices) {
                val stat = statsResult.feedStats[i]
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
        } catch (error: Throwable) { Logs(TAG, error, "loadStatistics failed") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val navController = LocalNavController.current
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
            vm.monthStats.clear()
//            vm.monthlyStats.clear()
            vm.downloadstatsData = null
            vm.downloadChartData = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MyTopAppBar() {
        var expanded by remember { mutableStateOf(false) }
        val textColor = MaterialTheme.colorScheme.onSurface
        val buttonColor = Color(0xDDFFD700)
        val buttonAltColor = lerp(MaterialTheme.colorScheme.tertiary, Color.Green, 0.5f)
        Box {
            TopAppBar(title = { Text("") }, navigationIcon = { IconButton(onClick = { openDrawer() }) { Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_chart_box), contentDescription = "Open Drawer") } }, actions = {
                if (vm.selectedTabIndex.intValue <= 2) {
                    IconButton(onClick = { vm.showFilter = true }) {
                        val filterColor = if (vm.timeFilterFrom > 0L || vm.timeFilterTo < Long.MAX_VALUE) buttonAltColor else textColor
                        Icon(imageVector = ImageVector.vectorResource(R.drawable.ic_filter), tint = filterColor, contentDescription = "filter")
                    }
                }
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = expanded, border = BorderStroke(1.dp, buttonColor), onDismissRequest = { expanded = false }) {
                    if (vm.selectedTabIndex.intValue == 0 || vm.selectedTabIndex.intValue == 1) DropdownMenuItem(text = { Text(stringResource(R.string.statistics_reset_data)) }, onClick = {
                        vm.showResetDialog.value = true
                        expanded = false
                    })
                }
            })
            HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), thickness = DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
        }
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
    fun FeedsList(statisticsData: StatisticsResult, lineChartData: LineChartData, infoCB: @Composable (FeedStatistics)->Unit) {
        val lazyListState = rememberLazyListState()
        var showFeedStats by remember { mutableStateOf(false) }
        var feedId by remember { mutableLongStateOf(0L) }
        var feedTitle by remember { mutableStateOf("") }
        if (showFeedStats) FeedStatisticsDialog(feedTitle, feedId, vm.timeFilterFrom, vm.timeFilterTo, showOpenFeed = true) { showFeedStats = false }
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(statisticsData.feedStats, key = { _, item -> item.feed.id }) { index, feedStats ->
                Row(Modifier.background(MaterialTheme.colorScheme.surface).fillMaxWidth()) {
                    val img = remember(feedStats) { ImageRequest.Builder(context).data(feedStats.feed.imageUrl).memoryCachePolicy(CachePolicy.ENABLED).placeholder(R.mipmap.ic_launcher).error(R.mipmap.ic_launcher).build() }
                    AsyncImage(model = img, contentDescription = "imgvCover", placeholder = painterResource(R.mipmap.ic_launcher), error = painterResource(R.mipmap.ic_launcher), contentScale = ContentScale.FillBounds,
                        modifier = Modifier.width(40.dp).height(90.dp).padding(end = 5.dp).clickable(onClick = {
                            feedOnDisplay = feedStats.feed
                            feedScreenMode = FeedScreenMode.Info
                            navController.navigate(Screens.FeedDetails.name)
                        })
                    )
                    Column(modifier = Modifier.clickable(onClick = {
                        feedId = feedStats.feed.id
                        feedTitle = feedStats.feed.title ?: "No title"
                        showFeedStats = true
                    })) {
                        val chipColor = lineChartData.getComposeColorOfItem(index)
                        Text("⬤" + (feedStats.feed.title?:"No title"), maxLines = 1, color = chipColor, style = MaterialTheme.typography.bodyMedium.merge())
                        infoCB(feedStats)
                    }
                }
            }
        }
    }

    if (vm.showTodayStats) {
        AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { vm.showTodayStats = false },  confirmButton = {},
            text = { EpisodeLazyColumn(LocalContext.current, vm.statsOfDay.episodes, showCoverImage = false, showActionButtons = false) },
            dismissButton = { TextButton(onClick = { vm.showTodayStats = false }) { Text(stringResource(R.string.cancel_label)) } } )
    }

    @Composable
    fun OverviewNumbers(stats: StatisticsItem, nd: Int = 1, center: Boolean = true) {
        fun formatEpisodes(num: Int): String {
            if (nd == 1) return num.toString()
            @SuppressLint("DefaultLocale")
            return String.format("%.2f", 1f*num/nd)
        }
        val textColor = MaterialTheme.colorScheme.onSurface
        Row {
            if (center) Spacer(Modifier.weight(0.3f))
            Text( stringResource(R.string.spent) + ": " + getDurationStringShort(stats.timeSpent*1000/nd, true), color = textColor)
            Spacer(Modifier.weight(0.3f))
        }
        Row {
            if (center) Spacer(Modifier.weight(0.3f))
            Text(stringResource(R.string.total) + ": " + formatEpisodes(stats.episodesTotal), color = textColor)
            Spacer(Modifier.weight(0.1f))
            Text(getDurationStringShort(stats.durationTotal*1000/nd, true), color = textColor)
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesPlayed > 0) {
                Text(stringResource(R.string.played) + ": " + formatEpisodes(stats.episodesPlayed), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationPlayed*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.3f))
        }
        if (stats.episodesStarted > 0 || stats.episodesSkipped > 0) Row {
            if (center) Spacer(Modifier.weight(0.3f))
            if (stats.episodesStarted > 0) {
                Text(stringResource(R.string.started) + ": " + formatEpisodes(stats.episodesStarted), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.timePlayed*1000/nd, true), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationStarted*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesSkipped > 0) {
                Text( stringResource(R.string.skipped) + ": " + formatEpisodes(stats.episodesSkipped), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationSkipped*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.3f))
        }
        if (stats.episodesPassed > 0 || stats.episodesIgnored > 0) Row {
            if (center) Spacer(Modifier.weight(0.3f))
            if (stats.episodesPassed > 0) {
                Text(stringResource(R.string.passed) + ": " + formatEpisodes(stats.episodesPassed), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationPassed*1000/nd, true), color = textColor)
            }
            Spacer(Modifier.weight(0.2f))
            if (stats.episodesIgnored > 0) {
                Text( stringResource(R.string.ignored) + ": " + formatEpisodes(stats.episodesIgnored), color = textColor)
                Spacer(Modifier.weight(0.1f))
                Text(getDurationStringShort(stats.durationIgnored*1000/nd, true), color = textColor)
            }
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
                    val dateText by remember { derivedStateOf {
                        if (vm.date == LocalDate.now()) context.getString(R.string.statistics_today) else {
                            val locale = Locale.getDefault()
                            val dateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMM d yyyy"), locale)
                            dateFormat.format(Date.from(vm.date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                        }
                    } }
                    Text(dateText, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.weight(0.2f))
                IconButton(onClick = {
                    if (vm.date.isBefore(LocalDate.now())) {
                        vm.date = vm.date.plusDays(1)
                        vm.loadDailyStats()
                    }
                }) {
                    val plusText by remember { derivedStateOf { if (vm.date >= LocalDate.now()) "" else "+" } }
                    Text(plusText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(0.2f))
                IconButton(onClick = {
                    if (vm.date.plusDays(6).isBefore(LocalDate.now())) {
                        vm.date = vm.date.plusDays(7)
                        vm.loadDailyStats()
                    }
                }) {
                    val plusText by remember { derivedStateOf { if (vm.date.plusDays(6) >= LocalDate.now()) "" else "+7" } }
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
            if (vm.chartData != null) FeedsList(vm.statsResult, vm.chartData!!) { stat -> OverviewNumbers(stats = stat.item, center = false) }
        }
    }

    @Composable
    fun MonthlyStats() {
        fun loadMongthStats() {
            val medias = realm.query(Episode::class).query(getStatsQueryText(vm.timeFilterFrom, vm.timeFilterTo)).find()
            val groupdMedias = medias.groupBy {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = if (it.lastPlayedTime > 0L) it.lastPlayedTime else it.playStateSetTime
                "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}"
            }
            val orderedGroupedItems = groupdMedias.toList().sortedBy {
                val (key, _) = it
                val year = key.substringBefore("-").toInt()
                val month = key.substringAfter("-").toInt()
                year * 12 + month
            }.toMap()
            val months: MutableList<MonthlyStatistics> = mutableListOf()
            for (key in orderedGroupedItems.keys) {
                val episodes = orderedGroupedItems[key] ?: continue
                val mItem = MonthlyStatistics()
                mItem.year = key.substringBefore("-").toInt()
                mItem.month = key.substringAfter("-").toInt()
                mItem.stats = getStatistics(episodes)
                months.add(mItem)
            }
            vm.monthStats.clear()
            vm.monthStats.addAll(months)
            for (item in vm.monthStats) vm.monthlyMaxDataValue = max(vm.monthlyMaxDataValue.toDouble(), item.stats.statTotal.timePlayed.toDouble()).toFloat()
        }
        @Composable
        fun ClickableBarChart(bars: List<MonthlyStatistics>, onBarClick: (Int) -> Unit) {
            val barRects = remember { mutableStateListOf<Rect>() }
            val barWidth = 50f
            val spaceBetweenBars = 20f
            val totalContentWidth = remember { bars.size * (barWidth + spaceBetweenBars).toInt() }
            val screenWidth = with(LocalDensity.current) { getAppContext().resources.displayMetrics.widthPixels.toDp() }
            val barColor = MaterialTheme.colorScheme.tertiary
            Canvas(modifier = Modifier.horizontalScroll(rememberScrollState()).width(if (totalContentWidth.dp > screenWidth) totalContentWidth.dp else screenWidth).height(150.dp).padding(start = 10.dp, end = 10.dp)
                .border(BorderStroke(1.dp, barColor)).pointerInput(Unit) {
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
                val textOffsetY = maxHeight.toInt() + 20.dp.toPx()
                barRects.clear()
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.CYAN
                    textSize = 16.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                bars.forEachIndexed { index, stats ->
                    val left = spaceBetweenBars + index * (barWidth + spaceBetweenBars)
                    val right = left + barWidth
                    val top = maxHeight * (1 - stats.stats.statTotal.timePlayed / vm.monthlyMaxDataValue)
                    val barRect = Rect(left, top, right, maxHeight)
                    barRects.add(barRect)
                    drawRect(color = barColor, topLeft = barRect.topLeft, size = barRect.size)
                    drawIntoCanvas { canvas ->
                        val textX = left + barWidth / 2
                        canvas.nativeCanvas.drawText(stats.month.toString(), textX, textOffsetY, textPaint)
                    }
                }
            }
        }
        val episodes = remember { mutableStateListOf<Episode>() }
        fun onMonthClicked(index: Int) {
            val year = vm.monthStats[index].year
            val month = vm.monthStats[index].month
            val yearMonth = YearMonth.of(year, month)
            val start = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val end = yearMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
            val data = getStatistics(start, end)
            episodes.clear()
            episodes.addAll(data.episodes)
        }

        if (episodes.isNotEmpty()) AlertDialog(properties = DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.fillMaxWidth().padding(10.dp).border(BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary)), onDismissRequest = { episodes.clear() },  confirmButton = {},
            text = { EpisodeLazyColumn(LocalContext.current, episodes, showCoverImage = false, showActionButtons = false) },
            dismissButton = { TextButton(onClick = { episodes.clear() }) { Text(stringResource(R.string.cancel_label)) } } )

        if (vm.statisticsState >= 0 && vm.monthStats.isEmpty()) loadMongthStats()
        Column {
            ClickableBarChart(vm.monthStats) { index -> onMonthClicked(index) }
            val lazyListState = rememberLazyListState()
            val textColor = MaterialTheme.colorScheme.onSurface
            LazyColumn(state = lazyListState, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 30.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(vm.monthStats) { index, item ->
                    Row(Modifier.background(MaterialTheme.colorScheme.surface).clickable(onClick = { onMonthClicked(index) })) {
                        Column {
                            Text(String.format(Locale.getDefault(), "%d-%d", item.year, item.month), color = textColor, style = MaterialTheme.typography.headlineSmall.merge())
                            OverviewNumbers(item.stats.statTotal, center = false)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DownloadStats() {
        fun loadDownloadStatistics() {
            vm.downloadstatsData = getStatistics(0, Long.MAX_VALUE, forDL = true)
            vm.downloadstatsData!!.feedStats.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.totalDownloadSize.compareTo(stat1.item.totalDownloadSize) }
            val dataValues = MutableList(vm.downloadstatsData!!.feedStats.size) { 0f }
            for (i in vm.downloadstatsData!!.feedStats.indices) {
                val stat = vm.downloadstatsData!!.feedStats[i]
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
                val info = ("${Formatter.formatShortFileSize(context, stat.item.totalDownloadSize)} • " + String.format(Locale.getDefault(), "%d%s", stat.item.episodesDownloadCount, context.getString(R.string.episodes_suffix)))
                Text(info, color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }

    ComfirmDialog(titleRes = R.string.statistics_reset_data, message = stringResource(R.string.statistics_reset_data_msg), showDialog = vm.showResetDialog) {
        vm.setTimeFilter(0L, Long.MAX_VALUE)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (realm.query(Episode::class).query("playedDuration != 0 || timeSpent != 0").count().find() > 0) {
                    realm.write {
                        val mediaAll = query(Episode::class).query("playedDuration != 0 || timeSpent != 0").find()
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
                        color = if (vm.selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else { Color.Transparent }),
                        selected = vm.selectedTabIndex.intValue == index,
                        onClick = { vm.selectedTabIndex.intValue = index },
                        text = { Text(text = stringResource(titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                            color = if (vm.selectedTabIndex.intValue == index) MaterialTheme.colorScheme.primary else { MaterialTheme.colorScheme.onSurface }) }
                    )
                }
            }
            when (vm.selectedTabIndex.intValue) {
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
    fun getComposeColorOfItem(index: Int): Color {
        val pc = if (sum == 0f) 0f else values[index] / sum
        if (pc <= 0.01) return Color.Gray
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

class MonthlyStatistics {
    var year: Int = 0
    var month: Int = 0
    var stats: StatisticsResult = StatisticsResult()
}

class StatisticsResult {
    var feedStats: MutableList<FeedStatistics> = mutableListOf()
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

private fun getStatsQueryText(timeFrom: Long, timeTo: Long): String {
    var qs1 = "(lastPlayedTime > $timeFrom AND lastPlayedTime < $timeTo)"
    val qs3 = "playStateSetTime > $timeFrom AND playStateSetTime < $timeTo"
    qs1 += " OR ($qs3 AND playState == ${EpisodeState.PLAYED.code})"
    qs1 += " OR ($qs3 AND playState == ${EpisodeState.SKIPPED.code})"
    qs1 += " OR ($qs3 AND playState == ${EpisodeState.PASSED.code})"
    qs1 += " OR ($qs3 AND playState == ${EpisodeState.IGNORED.code})"
    return qs1
}

private fun getStatistics(episodes: List<Episode>, feedId: Long = 0L, forDL: Boolean = false): StatisticsResult {
    val result = StatisticsResult()
    result.episodes = episodes

    val groupdMedias = episodes.groupBy { it.feedId ?: 0L }
    result.oldestDate = Long.MAX_VALUE
    for ((fid, episodes) in groupdMedias) {
        val feed = getFeed(fid, false) ?: continue
        Logd(TAG, "getStatistics feed: ${feed.title}")
        val fStat = FeedStatistics()
        fStat.feed = feed
        fStat.item.episodesTotal = episodes.size
        for (e in episodes) {
            if (feedId != 0L || !forDL) {
                if (e.lastPlayedTime > 0L && e.lastPlayedTime < result.oldestDate) result.oldestDate = e.lastPlayedTime
                if (e.playStateSetTime > 0L && e.playStateSetTime < result.oldestDate) result.oldestDate = e.playStateSetTime
                if (e.duration > 0) fStat.item.durationTotal += e.duration
                else Loge(TAG, "episode duration abnormal: ${e.duration} state: ${e.playState} ${e.title}")
                Logd(TAG, "getStatistics e.playState: ${e.playState} e.timeSpent: ${e.timeSpent} ${e.playedDuration} ${e.title}")
                if (e.playState == EpisodeState.PLAYED.code) {
                    fStat.item.episodesPlayed++
                    fStat.item.durationPlayed += e.duration
                }
                if (e.playState == EpisodeState.SKIPPED.code) {
                    fStat.item.episodesSkipped++
                    fStat.item.durationSkipped += e.duration
                }
                if (e.playState == EpisodeState.PASSED.code) {
                    fStat.item.episodesPassed++
                    fStat.item.durationPassed += e.duration
                }
                if (e.playState == EpisodeState.IGNORED.code) {
                    fStat.item.episodesIgnored++
                    fStat.item.durationIgnored += e.duration
                }
                if (e.playedDuration > 0) {
                    fStat.item.episodesStarted++
                    fStat.item.timePlayed += e.playedDuration
                    fStat.item.durationStarted += e.duration
                    val tSpent = e.timeSpent
//                    if (tSpent > 3 * max(e.playedDuration, 60000)) {
//                        Logt(TAG, "timeSpent: ${e.timeSpent} > playedDuration: ${e.playedDuration} ${e.title}")
////                        tSpent = e.playedDuration.toLong()
//                    }
                    fStat.item.timeSpent += tSpent
                }
            }
            if (feedId != 0L || forDL) {
                if (e.downloaded) {
                    fStat.item.episodesDownloadCount += 1
                    if (e.size > 0) fStat.item.totalDownloadSize += e.size
                    else if (e.size < 0) Loge(TAG, "Episode media file has negative size: ${e.size} ${e.title}")
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
        result.feedStats.add(fStat)
    }
    if (result.feedStats.isNotEmpty()) {
        val item = StatisticsItem()
        result.statTotal = result.feedStats.fold(item) { acc, stats ->
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

private fun getStatistics(timeFrom: Long, timeTo: Long, feedId: Long = 0L, forDL: Boolean = false): StatisticsResult {
    Logd(TAG, "getStatistics called")
    val qs2 = when {
        forDL -> ""
        else -> getStatsQueryText(timeFrom, timeTo)
    }
    val queryString = when {
        forDL -> "downloaded == true"
        feedId != 0L -> "feedId == $feedId AND ($qs2)"
        else -> "($qs2)"
    }
    val episodes = realm.query(Episode::class).query(queryString).find()
    Logd(TAG, "getStatistics queryString: [${episodes.size}] $queryString")
    return getStatistics(episodes, feedId, forDL)
}

@Composable
fun FeedStatisticsDialog(title: String, feedId: Long, timeFrom: Long, timeTo: Long, showOpenFeed: Boolean = false, onDismissRequest: () -> Unit) {
    val navController = LocalNavController.current
    var fStat by remember { mutableStateOf<FeedStatistics?>(null) }
    val episodes = remember { mutableStateListOf<Episode>()  }
    fun loadStatistics() {
        try {
            val data = getStatistics(timeFrom, timeTo, feedId)
            if (data.feedStats.isNotEmpty()) {
                Logd(TAG, "loadStatistics data.feedStats: ${data.feedStats.size}")
                data.feedStats.sortWith { stat1: FeedStatistics, stat2: FeedStatistics -> stat2.item.timePlayed.compareTo(stat1.item.timePlayed) }
                fStat = data.feedStats[0]
                Logd(TAG,"loadStatistics durationTotal ${fStat?.item?.durationTotal}")
            }
            episodes.clear()
            episodes.addAll(data.episodes)
        } catch (error: Throwable) { Logs(TAG, error, "loadStatistics failed") }
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
                    Text(getDurationStringShort((fStat?.item?.durationStarted ?: 0)*1000, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_time_played), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort((fStat?.item?.timePlayed ?: 0)*1000, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_time_spent), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort((fStat?.item?.timeSpent ?: 0)*1000, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_total_duration), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(getDurationStringShort(duration = (fStat?.item?.durationTotal ?: 0)*1000, true), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_episodes_on_device), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.getDefault(), "%d", fStat?.item?.episodesDownloadCount ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Row {
                    Text(stringResource(R.string.statistics_space_used), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Formatter.formatShortFileSize(context, fStat?.item?.totalDownloadSize ?: 0), color = textColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
                }
                Box(modifier = Modifier.weight(1f)) { EpisodeLazyColumn(context, episodes, showCoverImage = false, showActionButtons = false) }
            }
        },
        confirmButton = { if (showOpenFeed) TextButton(onClick = {
            val feed = getFeed(feedId)
            if (feed != null) {
                feedOnDisplay = feed
                navController.navigate(Screens.FeedDetails.name)
            }
            onDismissRequest()
        }) { Text(stringResource(R.string.open_podcast))} },
        dismissButton = { TextButton(onClick = { onDismissRequest() }) { Text(stringResource(R.string.cancel_label)) } }
    )
}


