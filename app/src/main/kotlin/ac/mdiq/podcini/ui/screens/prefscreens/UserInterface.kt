package ac.mdiq.podcini.ui.screens.prefscreens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.settings.AppPreferences
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.screens.DefaultPages
import ac.mdiq.podcini.ui.compose.AppThemes
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchRow
import ac.mdiq.podcini.ui.compose.appTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UserInterfaceScreen() {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        Text(stringResource(R.string.appearance), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            var checkIndex by remember { mutableIntStateOf(
                when(appTheme) {
                    AppThemes.SYSTEM -> 0
                    AppThemes.LIGHT -> 1
                    AppThemes.DARK -> 2
                    else -> 0
                }) }
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 0, onClick = {
                checkIndex = 0
                appTheme = AppThemes.SYSTEM
//                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_automatic), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 1, onClick = {
                checkIndex = 1
                appTheme = AppThemes.LIGHT
//                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_light), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 2, onClick = {
                checkIndex = 2
                appTheme = AppThemes.DARK
//                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_dark), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        TitleSummarySwitchRow(R.string.pref_black_theme_title, R.string.pref_black_theme_message, appPrefs.themeBlack) {
            upsertBlk(appPrefs) { p-> p.themeBlack = it }
//            activity.recreate()
        }
        TitleSummarySwitchRow(R.string.pref_episode_cover_title, R.string.pref_episode_cover_summary, appPrefs.useEpisodeCover) {
            upsertBlk(appPrefs) { p-> p.useEpisodeCover = it }
        }

        Text(stringResource(R.string.external_elements), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        TitleSummarySwitchRow(R.string.pref_show_notification_skip_title, R.string.pref_show_notification_skip_sum, appPrefs.showSkip) {
            upsertBlk(appPrefs) { p-> p.showSkip = it }
        }
        Text(stringResource(R.string.behavior), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        var showDefaultPageOptions by remember { mutableStateOf(false) }
        TitleSummaryActionColumn(R.string.pref_default_page, R.string.pref_default_page_sum) { showDefaultPageOptions = true }
        if (showDefaultPageOptions) {
            var tempSelectedOption by remember { mutableStateOf(appPrefs.defaultPage) }
            AlertDialog(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraLarge), onDismissRequest = { showDefaultPageOptions = false },
                title = { Text(stringResource(R.string.pref_default_page), style = CustomTextStyles.titleCustom) },
                text = {
                    Column {
                        DefaultPages.entries.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { tempSelectedOption = option.name }) {
                                Checkbox(checked = tempSelectedOption == option.name, onCheckedChange = { tempSelectedOption = option.name })
                                Text(stringResource(option.res), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        upsertBlk(appPrefs) { it.defaultPage = tempSelectedOption }
                        showDefaultPageOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showDefaultPageOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        TitleSummarySwitchRow(R.string.pref_back_button_opens_drawer, R.string.pref_back_button_opens_drawer_summary, appPrefs.backButtonOpensDrawer) {
            upsertBlk(appPrefs) { p-> p.backButtonOpensDrawer = it }
        }
        TitleSummarySwitchRow(R.string.pref_show_error_toasts, R.string.pref_show_error_toasts_sum, appPrefs.showErrorToasts) {
            upsertBlk(appPrefs) { p-> p.showErrorToasts = it }
        }
        TitleSummarySwitchRow(R.string.pref_print_logs, R.string.pref_print_logs_sum, appPrefs.printDebugLogs) {
            upsertBlk(appPrefs) { p-> p.printDebugLogs = it }
        }
        TitleSummarySwitchRow(R.string.pref_dont_ask_restricted, R.string.pref_dont_ask_restricted_sum, appPrefs.dont_ask_again_unrestricted_background) {
            upsertBlk(appPrefs) { p-> p.dont_ask_again_unrestricted_background = it }
        }
    }
}
