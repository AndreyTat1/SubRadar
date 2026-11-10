package com.subradar.app.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.subradar.app.R
import com.subradar.app.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

@Composable
fun Dashboard(data: Snapshot, open: (String) -> Unit) {
    val today = LocalDate.now()
    val future = remember(data, today) { data.unconfirmed(today, today.plusMonths(12)) }
    val annual = remember(future) { future.map { it.price }.totals() }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val active = remember(data) { data.subscriptions.filter { it.scheduled } }
    val soon = remember(future, today) { future.takeWhile { it.date < today.plusDays(30) } }
    val suggestions = remember(data, today) {
        val increased = data.prices.groupBy { it.subscriptionId }.mapValues { (_, rows) ->
            rows.maxWith(compareBy(PriceChange::effectiveDate, PriceChange::recordedAt))
        }
        val duplicates = active.groupingBy { it.categoryId }.eachCount()
        buildList {
            for (sub in active) {
                val remaining = sub.trialEnd?.minus(today.toEpochDay())
                if (sub.status == Status.TRIAL && remaining != null && remaining in 0L..7L) add(sub to R.string.tip_trial)
                val used = sub.lastUsed ?: sub.start
                if (used <= today.minusDays(30).toEpochDay()) add(sub to R.string.tip_unused)
                val change = increased[sub.id]
                if (change != null && change.previous.currency == change.next.currency &&
                    change.next.minor > change.previous.minor) add(sub to R.string.tip_increased)
                if ((duplicates[sub.categoryId] ?: 0) > 1) add(sub to R.string.tip_category)
            }
        }
    }
    ScreenList {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.overview), style = MaterialTheme.typography.labelLarge)
                    Metric(stringResource(R.string.monthly_average), amountsLabel(annual.map { it.copy(minor = (it.minor + 6) / 12) }))
                    Metric(stringResource(R.string.next_year), amountsLabel(annual))
                }
            }
        }
        item {
            Panel {
                Metric(stringResource(R.string.active_count), active.size.toString())
                Metric(stringResource(R.string.next_payment), future.firstOrNull()?.date?.label() ?: stringResource(R.string.no_amount))
                Metric(stringResource(R.string.next_30), amountsLabel(soon.map { it.price }.totals()))
            }
        }
        item { Section(stringResource(R.string.upcoming)) }
        if (soon.isEmpty()) item { Text(stringResource(R.string.no_upcoming)) }
        items(soon.take(12), key = { "${it.subscription.id}/${it.date}" }) {
            val days = ChronoUnit.DAYS.between(today, it.date).toInt()
            val remaining = pluralStringResource(R.plurals.days_remaining, days, days)
            SubscriptionCard(it.subscription, categories[it.subscription.categoryId], "${it.date.label()} · $remaining",
                { open(it.subscription.id) }, it.price)
        }
        item { Section(stringResource(R.string.recommendations)) }
        if (suggestions.isEmpty()) item { Text(stringResource(R.string.no_recommendations)) }
        items(suggestions.take(12)) { (sub, reason) ->
            Card(onClick = { open(sub.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(sub.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(reason))
                }
            }
        }
    }
}

@Composable
fun SubscriptionList(data: Snapshot, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<Status?>(null) }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    val visible = data.subscriptions.filter { it.name.contains(query.trim(), ignoreCase = true) &&
        (filter == null || it.status == filter) }
    ScreenList {
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text(stringResource(R.string.search)) })
        }
        item {
            Choice(stringResource(R.string.status), filter, listOf(null) + Status.entries,
                { it?.label() ?: stringResource(R.string.all) }) { filter = it }
        }
        if (visible.isEmpty()) item { Text(stringResource(R.string.no_subscriptions)) }
        items(visible, key = { it.id }) { sub ->
            val category = categories[sub.categoryId]
            SubscriptionCard(sub, category, listOfNotNull(category?.label(), sub.status.label()).joinToString(" · "),
                { open(sub.id) })
        }
    }
}

@Composable
private fun MonthPicker(month: YearMonth, change: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { change(month.minusMonths(1)) }, enabled = month.year > 1900 || month.monthValue > 1) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous_month))
        }
        Text(month.label(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { change(month.plusMonths(1)) }, enabled = month.year < 9999 || month.monthValue < 12) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.next_month))
        }
    }
}

@Composable
fun CalendarScreen(data: Snapshot, monday: Boolean, vm: RadarViewModel, open: (String) -> Unit) {
    val today = LocalDate.now()
    var dayKey by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayKey)
    val month = YearMonth.from(day)
    val start = month.atDay(1)
    val until = month.plusMonths(1).atDay(1)
    val first = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val pad = (start.dayOfWeek.value - first.value + 7) % 7
    val occurrences = remember(data, month) { data.unconfirmed(start, until) }
    val paid = remember(data, month) { data.payments.filter { it.scheduledDate in start.toEpochDay() until until.toEpochDay() } }
    val expectedDates = remember(occurrences) { occurrences.groupBy { it.date } }
    val paidDates = remember(paid) { paid.groupBy { LocalDate.ofEpochDay(it.scheduledDate) } }
    val locale = uiLocale()
    val subscriptions = remember(data.subscriptions) { data.subscriptions.associateBy { it.id } }
    val categories = remember(data.categories) { data.categories.associateBy { it.id } }
    ScreenList {
        item {
            Panel {
                MonthPicker(month) { dayKey = it.atDay(1).toEpochDay() }
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) {
                        val name = first.plus(it.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(name) }
                    }
                }
                repeat((pad + month.lengthOfMonth() + 6) / 7) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val number = week * 7 + column - pad + 1
                            if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f))
                            else {
                                val date = month.atDay(number)
                                val hasPayment = date in expectedDates || date in paidDates
                                val desc = if (hasPayment) stringResource(R.string.date_with_payment, date.label()) else date.label()
                                Surface(color = if (date == day) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        .semantics { contentDescription = desc; selected = date == day }
                                        .clickable { dayKey = date.toEpochDay() }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(number.toString())
                                        if (hasPayment) Box(Modifier.size(5.dp)) {
                                            val color = MaterialTheme.colorScheme.primary
                                            Canvas(Modifier.fillMaxSize()) { drawCircle(color) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Metric(stringResource(R.string.projected_month), amountsLabel(occurrences.map { it.price }.totals()))
                Metric(stringResource(R.string.confirmed_month), amountsLabel(paid.map { it.amount }.totals()))
            }
        }
        item { Section(day.label()) }
        if (expectedDates[day].isNullOrEmpty() && paidDates[day].isNullOrEmpty()) {
            item { Text(stringResource(R.string.no_day_payments)) }
        }
        items(expectedDates[day].orEmpty(), key = { it.subscription.id }) { entry ->
            SubscriptionCard(entry.subscription, categories[entry.subscription.categoryId],
                stringResource(R.string.projected), { open(entry.subscription.id) }, entry.price)
            if (day <= today) TextButton(onClick = { vm.confirm(entry.subscription.id, day) }) {
                Text(stringResource(R.string.confirm_paid))
            }
        }
        items(paidDates[day].orEmpty(), key = { it.id }) { entry ->
            val sub = subscriptions[entry.subscriptionId] ?: return@items
            SubscriptionCard(sub, categories[sub.categoryId], stringResource(R.string.confirmed),
                { open(sub.id) }, entry.amount)
            TextButton(onClick = { vm.undoConfirmation(entry.id) }) { Text(stringResource(R.string.undo_paid)) }
        }
    }
}

private val chartColors = listOf(Color(0xFF9C76EF), Color(0xFFE788BC), Color(0xFF44BFB2),
    Color(0xFFEEAA58), Color(0xFF6D9FEB), Color(0xFFB8AF50), Color(0xFFD282E5))

@Composable
fun Analytics(data: Snapshot) {
    val now = YearMonth.now()
    var monthKey by rememberSaveable { mutableStateOf(now.toString()) }
    val month = YearMonth.parse(monthKey)
    val first = LocalDate.of(month.year, 1, 1)
    val forecast = remember(data, first) { data.unconfirmed(first, first.plusYears(1)) }
    val confirmed = remember(data, first) {
        data.payments.filter { it.scheduledDate in first.toEpochDay() until first.plusYears(1).toEpochDay() }
    }
    val monthly = remember(forecast, month) { forecast.filter { YearMonth.from(it.date) == month } }
    val monthlyPaid = remember(confirmed, month) {
        confirmed.filter { YearMonth.from(LocalDate.ofEpochDay(it.scheduledDate)) == month }
    }
    val byCurrency = monthly.groupBy { it.price.currency }
    val codes = (forecast.map { it.price.currency } + confirmed.map { it.amount.currency }).distinct().sorted()
    var selected by rememberSaveable { mutableStateOf("") }
    val code = selected.takeIf { it in codes } ?: codes.firstOrNull()
    val months = (0..11).map { YearMonth.from(first).plusMonths(it.toLong()) }
    val previous = remember(data, month) { data.unconfirmed(month.minusMonths(1).atDay(1), month.atDay(1)) }
    ScreenList {
        item {
            Panel {
                MonthPicker(month) { monthKey = it.toString() }
                Text(stringResource(R.string.forecast_explanation))
                Metric(stringResource(R.string.projected_month), amountsLabel(monthly.map { it.price }.totals()))
                Metric(stringResource(R.string.confirmed_month), amountsLabel(monthlyPaid.map { it.amount }.totals()))
                Metric(stringResource(R.string.projected_year), amountsLabel(forecast.map { it.price }.totals()))
                Metric(stringResource(R.string.confirmed_year), amountsLabel(confirmed.map { it.amount }.totals()))
                Metric(stringResource(R.string.monthly_average),
                    amountsLabel(forecast.map { it.price }.totals().map { it.copy(minor = (it.minor + 6) / 12) }))
            }
        }
        if (code != null) {
            val selectedMonth = byCurrency[code].orEmpty()
            val categories = data.categories.associateBy { it.id }
            val grouped = selectedMonth.groupBy { it.subscription.categoryId }.mapValues { (_, list) ->
                list.sumOf { it.price.minor }
            }.toList().sortedByDescending { it.second }
            val monthValues = months.map { point -> forecast.filter { YearMonth.from(it.date) == point && it.price.currency == code }
                .sumOf { it.price.minor } }
            val previousTotal = previous.filter { it.price.currency == code }.sumOf { it.price.minor }
            val unused = selectedMonth.filter {
                val last = it.subscription.lastUsed ?: it.subscription.start
                last <= LocalDate.now().minusDays(30).toEpochDay()
            }.sumOf { it.price.minor }
            item { Choice(stringResource(R.string.chart_currency), code, codes, { it }) { selected = it } }
            item {
                Panel {
                    Section(stringResource(R.string.categories))
                    if (grouped.any { it.second > 0 }) Donut(grouped.map { it.second })
                    grouped.forEachIndexed { index, (id, total) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("●", color = chartColors[index % chartColors.size])
                            Text("${categories[id]?.label().orEmpty()} · ${Money(total, code).label()}")
                        }
                    }
                    if (grouped.isEmpty()) Text(stringResource(R.string.no_amount))
                }
            }
            item {
                Panel {
                    Section(stringResource(R.string.monthly_bars))
                    Trend(monthValues, false)
                    Section(stringResource(R.string.expense_trend))
                    Trend(monthValues, true)
                    Metric(stringResource(R.string.month_change), Money(monthValues[month.monthValue - 1] - previousTotal, code).label())
                    Metric(stringResource(R.string.potential_savings), Money(unused, code).label())
                    Text(stringResource(R.string.savings_explanation), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Panel {
                    Section(stringResource(R.string.top_subscriptions))
                    val top = selectedMonth.groupBy { it.subscription.id }.values.map { entries ->
                        entries.first().subscription.name to entries.sumOf { it.price.minor }
                    }.sortedByDescending { it.second }.take(5)
                    top.forEach { (name, total) -> Text("$name · ${Money(total, code).label()}") }
                    if (top.isEmpty()) Text(stringResource(R.string.no_amount))
                }
            }
            item {
                Panel {
                    Section(stringResource(R.string.monthly_breakdown))
                    months.zip(monthValues).forEach { (point, total) -> Text("${point.label()} · ${Money(total, code).label()}") }
                }
            }
        }
    }
}

@Composable private fun Donut(values: List<Long>) {
    val total = values.sum()
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val diameter = size.minDimension * .73f
        var angle = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.toFloat() / total.toFloat() * 360f
            drawArc(chartColors[index % chartColors.size], angle, sweep, false,
                topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                size = Size(diameter, diameter), style = Stroke(20.dp.toPx()))
            angle += sweep
        }
    }
}

@Composable private fun Trend(values: List<Long>, line: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val space = size.width / values.size
        val points = values.mapIndexed { i, value ->
            Offset(space * (i + .5f), size.height - 4.dp.toPx() - size.height * .85f * value.toFloat() / maximum.toFloat())
        }
        if (line) {
            points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 2.dp.toPx()) }
            points.forEach { drawCircle(color, 2.dp.toPx(), it) }
        } else {
            points.forEach { point ->
                drawLine(color, Offset(point.x, size.height), point, strokeWidth = space * .62f)
            }
        }
    }
}
