package com.subradar.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.subradar.app.R
import com.subradar.app.domain.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable fun uiLocale(): Locale = LocalConfiguration.current.locales[0]
@Composable fun LocalDate.label(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(uiLocale()))
@Composable fun YearMonth.label(): String = format(DateTimeFormatter.ofPattern("LLLL yyyy", uiLocale()))
@Composable fun Money.label(): String = formatted(uiLocale())
@Composable fun amountsLabel(amounts: List<Money>): String {
    val locale = uiLocale()
    return if (amounts.isEmpty()) stringResource(R.string.no_amount)
    else amounts.joinToString(" · ") { it.formatted(locale) }
}

@Composable fun Category.label(): String = when (id) {
    "video" -> stringResource(R.string.cat_video)
    "music" -> stringResource(R.string.cat_music)
    "games" -> stringResource(R.string.cat_games)
    "software" -> stringResource(R.string.cat_software)
    "cloud" -> stringResource(R.string.cat_cloud)
    "education" -> stringResource(R.string.cat_education)
    "health" -> stringResource(R.string.cat_health)
    "work" -> stringResource(R.string.cat_work)
    "other" -> stringResource(R.string.cat_other)
    else -> name
}

@Composable fun Status.label(): String = stringResource(when (this) {
    Status.ACTIVE -> R.string.active
    Status.TRIAL -> R.string.trial
    Status.PAUSED -> R.string.paused
    Status.CANCELLED -> R.string.cancelled
    Status.ARCHIVED -> R.string.archived
})
@Composable fun IntervalUnit.label(): String = stringResource(when (this) {
    IntervalUnit.DAYS -> R.string.days
    IntervalUnit.WEEKS -> R.string.weeks
    IntervalUnit.MONTHS -> R.string.months
})
@Composable fun RepeatRule.label(): String = stringResource(when (unit to count) {
    IntervalUnit.WEEKS to 1 -> R.string.weekly
    IntervalUnit.MONTHS to 1 -> R.string.monthly
    IntervalUnit.MONTHS to 3 -> R.string.quarterly
    IntervalUnit.MONTHS to 6 -> R.string.semiannual
    IntervalUnit.MONTHS to 12 -> R.string.annual
    else -> R.string.custom_interval
}, count, unit.label())

@Composable fun ScreenList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content
    )
}
@Composable fun Section(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}
@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
@Composable fun Metric(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}
@Composable fun SubscriptionCard(sub: Subscription, category: Category?, secondary: String, click: () -> Unit,
                                price: Money = sub.price) {
    Card(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Text(category?.icon ?: "•", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleMedium)
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(price.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable fun ConfirmDialog(title: String, message: String, cancel: () -> Unit, accept: () -> Unit) {
    AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = accept) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.cancel)) } })
}

@Composable fun <T> Choice(label: String, selection: T, options: List<T>, describe: @Composable (T) -> String,
                            change: (T) -> Unit) {
    var opened = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { opened.value = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${describe(selection)}")
        }
        DropdownMenu(expanded = opened.value, onDismissRequest = { opened.value = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(describe(option)) }, onClick = {
                    opened.value = false
                    change(option)
                })
            }
        }
    }
}
