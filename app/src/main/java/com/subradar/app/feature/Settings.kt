package com.subradar.app.feature

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.subradar.app.R
import com.subradar.app.domain.Preferences
import com.subradar.app.domain.Theme

@Composable
fun SettingsScreen(vm: RadarViewModel, prefs: Preferences, language: String) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshReminders()
    }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let { vm.export(it, true) }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let { vm.export(it, false) }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::restore)
    }
    when (dialog) {
        1 -> ConfirmDialog(stringResource(R.string.import_backup), stringResource(R.string.import_message),
            { dialog = 0 }, { dialog = 0; import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
        2 -> ConfirmDialog(stringResource(R.string.clear_data), stringResource(R.string.clear_message),
            { dialog = 0 }, { dialog = 0; vm.clear() })
        3, 4 -> AlertDialog(onDismissRequest = { dialog = 0 },
            title = { Text(stringResource(if (dialog == 3) R.string.about else R.string.privacy)) },
            text = { Text(stringResource(if (dialog == 3) R.string.about_text else R.string.privacy_text)) },
            confirmButton = { TextButton(onClick = { dialog = 0 }) { Text(stringResource(R.string.close)) } })
    }
    ScreenList {
        item {
            Panel {
                Section(stringResource(R.string.appearance))
                Choice(stringResource(R.string.theme), prefs.theme, Theme.entries,
                    { stringResource(if (it == Theme.DARK) R.string.dark else R.string.light) }) {
                    vm.changePreferences { old -> old.copy(theme = it) }
                }
                Choice(stringResource(R.string.language), language, listOf("", "en", "ru"),
                    { when (it) { "en" -> "English"; "ru" -> "Русский"; else -> stringResource(R.string.system) } }) {
                    vm.setLanguage(it)
                }
                CurrencyChoice(prefs.currency) { vm.changePreferences { old -> old.copy(currency = it) } }
                Choice(stringResource(R.string.week_start), prefs.mondayFirst, listOf(true, false),
                    { stringResource(if (it) R.string.monday else R.string.sunday) }) {
                    vm.changePreferences { old -> old.copy(mondayFirst = it) }
                }
            }
        }
        item {
            Panel {
                Section(stringResource(R.string.reminders))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.enable_reminders), Modifier.weight(1f))
                    Switch(prefs.notifications, {
                        vm.changePreferences { old -> old.copy(notifications = it) }
                        if (it && Build.VERSION.SDK_INT >= 33 &&
                            !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    })
                }
                Text(stringResource(R.string.reminder_timing))
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.allow_notifications))
                    }
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(stringResource(R.string.system_notifications)) }
            }
        }
        item {
            Panel {
                Section(stringResource(R.string.backups))
                OutlinedButton(onClick = { json.launch("subradar-backup.json") }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_json))
                }
                OutlinedButton(onClick = { csv.launch("subradar-subscriptions.csv") }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_csv))
                }
                OutlinedButton(onClick = { dialog = 1 }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_backup)) }
                TextButton(onClick = { dialog = 2 }) { Text(stringResource(R.string.clear_data), color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Panel {
                TextButton(onClick = { dialog = 3 }) { Text(stringResource(R.string.about)) }
                TextButton(onClick = { dialog = 4 }) { Text(stringResource(R.string.privacy)) }
            }
        }
    }
}
