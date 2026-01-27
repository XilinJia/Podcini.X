package ac.mdiq.podcini.ui.activity

import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.receiver.PodciniWidget
import ac.mdiq.podcini.storage.database.queuesLive
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
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QueuePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spinnerTexts = queuesLive.map { "${if (it.id == actQueue.id) "> " else ""}${it.name} : ${it.size()}" }

        setContent {
            PodciniTheme {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = "Select queue", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        var curIndex by remember { mutableIntStateOf(0) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                            for (index in queuesLive.indices) {
                                FilterChip(onClick = {
                                    curIndex = index
                                    actQueue = queuesLive[index]
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        PodciniWidget().updateAll(applicationContext)
                                        withContext(Dispatchers.Main) { finish() }
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