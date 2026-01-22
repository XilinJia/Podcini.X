package ac.mdiq.podcini.preferences.screens

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.DefaultPages
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.ui.activity.PreferenceActivity
import ac.mdiq.podcini.ui.compose.CustomTextStyles
import ac.mdiq.podcini.ui.compose.TitleSummaryActionColumn
import ac.mdiq.podcini.ui.compose.TitleSummarySwitchPrefRow
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UserInterfaceScreen(activity: PreferenceActivity) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.appearance), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            var checkIndex by remember { mutableIntStateOf(
                when(AppPreferences.theme) {
                    AppPreferences.ThemePreference.SYSTEM -> 0
                    AppPreferences.ThemePreference.LIGHT -> 1
                    AppPreferences.ThemePreference.DARK -> 2
                    else -> 0
                }) }
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 0, onClick = {
                checkIndex = 0
                AppPreferences.theme = AppPreferences.ThemePreference.SYSTEM
                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_automatic), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 1, onClick = {
                checkIndex = 1
                AppPreferences.theme = AppPreferences.ThemePreference.LIGHT
                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_light), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            RadioButton(selected = checkIndex == 2, onClick = {
                checkIndex = 2
                AppPreferences.theme = AppPreferences.ThemePreference.DARK
                activity.recreate()
            })
            Text(stringResource(R.string.pref_theme_title_dark), color = textColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        TitleSummarySwitchPrefRow(R.string.pref_black_theme_title, R.string.pref_black_theme_message, AppPrefs.prefThemeBlack) {
            putPref(AppPrefs.prefThemeBlack, it)
            activity.recreate()
        }
        //            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp)) {
        //                Column(modifier = Modifier.weight(1f)) {
        //                    Text(stringResource(R.string.pref_tinted_theme_title), color = textColor, style = CustomTextStyles.titleCustom, fontWeight = FontWeight.Bold)
        //                    Text(stringResource(R.string.pref_tinted_theme_message), color = textColor)
        //                }
        //                var isChecked by remember { mutableStateOf(getPref(AppPrefs.prefTintedColors, false)) }
        //                Switch(checked = isChecked, onCheckedChange = {
        //                    isChecked = it
        //                    putPref(AppPrefs.prefTintedColors, it)
        //                    recreate()
        //                })
        //            }
        TitleSummarySwitchPrefRow(R.string.pref_episode_cover_title, R.string.pref_episode_cover_summary, AppPrefs.prefEpisodeCover)

        Text(stringResource(R.string.external_elements), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        TitleSummarySwitchPrefRow(R.string.pref_show_notification_skip_title, R.string.pref_show_notification_skip_sum, AppPrefs.prefShowSkip)
        Text(stringResource(R.string.behavior), color = textColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp))
        var showDefaultPageOptions by remember { mutableStateOf(false) }
        TitleSummaryActionColumn(R.string.pref_default_page, R.string.pref_default_page_sum) { showDefaultPageOptions = true }
        if (showDefaultPageOptions) {
            var tempSelectedOption by remember { mutableStateOf(getPref(AppPrefs.prefDefaultPage, DefaultPages.Library.name)) }
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
                        putPref(AppPrefs.prefDefaultPage, tempSelectedOption)
                        showDefaultPageOptions = false
                    }) { Text(text = "OK") }
                },
                dismissButton = { TextButton(onClick = { showDefaultPageOptions = false }) { Text(stringResource(R.string.cancel_label)) } }
            )
        }
        TitleSummarySwitchPrefRow(R.string.pref_back_button_opens_drawer, R.string.pref_back_button_opens_drawer_summary, AppPrefs.prefBackButtonOpensDrawer)
        TitleSummarySwitchPrefRow(R.string.pref_show_error_toasts, R.string.pref_show_error_toasts_sum, AppPrefs.prefShowErrorToasts)
        TitleSummarySwitchPrefRow(R.string.pref_print_logs, R.string.pref_print_logs_sum, AppPrefs.prefPrintDebugLogs)
        TitleSummarySwitchPrefRow(R.string.pref_dont_ask_restricted, R.string.pref_dont_ask_restricted_sum, AppPrefs.dont_ask_again_unrestricted_background)
    }
}
