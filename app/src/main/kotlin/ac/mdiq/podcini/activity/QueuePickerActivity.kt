package ac.mdiq.podcini.activity

import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.receiver.PodciniWidget
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.model.toWidget
import ac.mdiq.podcini.ui.compose.AppThemes
import ac.mdiq.podcini.ui.compose.PodciniTheme
import ac.mdiq.podcini.ui.compose.filterChipBorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class QueuePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
//        installSplashScreen()
        super.onCreate(savedInstanceState)
        val spinnerTexts = queuesLive.map { "${if (it.id == actQueue.id) "> " else ""}${it.name} : ${it.size()}" }

        setContent {
            PodciniTheme(AppThemes.BLACK) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = "Select queue", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                            var curIndex by remember { mutableIntStateOf(-1) }
                            for (index in queuesLive.indices) {
                                FilterChip(onClick = {
                                    curIndex = index
                                    val queue = queuesLive[index]
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val episodes = queue.episodesSorted.take(40).map { it.toWidget() }
                                        val json = Json.encodeToString(episodes)
                                        val manager = GlanceAppWidgetManager(this@QueuePickerActivity)
                                        val glanceIds = manager.getGlanceIds(PodciniWidget::class.java)
                                        glanceIds.forEach { glanceId ->
                                            updateAppWidgetState(this@QueuePickerActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                                                prefs.toMutablePreferences().apply {
                                                    this[longPreferencesKey("queue_id")] = queue.id
                                                    this[stringPreferencesKey("queue_name")] = queue.name
                                                    this[intPreferencesKey("queue_size")] = queue.size()
                                                    this[stringPreferencesKey("episodes")] = json
                                                    this[stringPreferencesKey("update_type")] = "queue"
                                                }
                                            }
                                        }
                                        PodciniWidget().updateAll(this@QueuePickerActivity)
                                        finish()
                                    }
                                }, label = { Text(spinnerTexts[index]) }, selected = curIndex == index, border = filterChipBorder(curIndex == index))
                            }
                        }
                    }
                }
            }
        }
    }
}