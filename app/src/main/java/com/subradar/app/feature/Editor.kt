package com.subradar.app.feature

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subradar.app.R
import com.subradar.app.data.uuid
import com.subradar.app.data.validateSubscription
import com.subradar.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

private data class Preset(val title: Int, val count: Int, val unit: IntervalUnit)
private val presets = listOf(
    Preset(R.string.weekly, 1, IntervalUnit.WEEKS), Preset(R.string.monthly, 1, IntervalUnit.MONTHS),
    Preset(R.string.quarterly, 3, IntervalUnit.MONTHS), Preset(R.string.semiannual, 6, IntervalUnit.MONTHS),
    Preset(R.string.annual, 12, IntervalUnit.MONTHS), Preset(R.string.custom, 0, IntervalUnit.DAYS)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionEditor(
    existing: Subscription?, categories: List<Category>, defaultCurrency: String, vm: RadarViewModel,
    done: () -> Unit
) {
    val today = LocalDate.now()
    val key = existing?.id
    var name by rememberSaveable(key) { mutableStateOf(existing?.name.orEmpty()) }
    var categoryId by rememberSaveable(key) { mutableStateOf(existing?.categoryId ?: "other") }
    var currency by rememberSaveable(key) { mutableStateOf(existing?.price?.currency ?: defaultCurrency) }
    var amount by rememberSaveable(key) {
        mutableStateOf(existing?.price?.let {
            BigDecimal.valueOf(it.minor, Currency.getInstance(it.currency).defaultFractionDigits).toPlainString()
        }.orEmpty())
    }
    var start by rememberSaveable(key) { mutableStateOf(existing?.start?.let { LocalDate.ofEpochDay(it).toString() } ?: today.toString()) }
    var next by rememberSaveable(key) {
        mutableStateOf(existing?.nextPayment?.let { LocalDate.ofEpochDay(it).toString() } ?: today.plusMonths(1).toString())
    }
    var status by rememberSaveable(key) { mutableStateOf(existing?.status ?: Status.ACTIVE) }
    var type by rememberSaveable(key) { mutableStateOf(existing?.type ?: SubscriptionType.STANDARD) }
    var trialStart by rememberSaveable(key) { mutableStateOf(existing?.trialStart?.let { LocalDate.ofEpochDay(it).toString() } ?: today.toString()) }
    var trialEnd by rememberSaveable(key) {
        mutableStateOf(existing?.trialEnd?.let { LocalDate.ofEpochDay(it).toString() } ?: today.plusDays(7).toString())
    }
    var interval by rememberSaveable(key) { mutableStateOf((existing?.repeat?.count ?: 1).toString()) }
    var unit by rememberSaveable(key) { mutableStateOf(existing?.repeat?.unit ?: IntervalUnit.MONTHS) }
    var presetIndex by rememberSaveable(key) {
        mutableIntStateOf(presets.indexOfFirst { it.count.toString() == interval && it.unit == unit }
            .takeIf { it >= 0 } ?: presets.lastIndex)
    }
    var payment by rememberSaveable(key) { mutableStateOf(existing?.paymentLabel.orEmpty()) }
    var notes by rememberSaveable(key) { mutableStateOf(existing?.notes.orEmpty()) }
    var paymentMask by rememberSaveable(key) { mutableIntStateOf((existing?.paymentReminders ?: setOf(1)).mask()) }
    var trialMask by rememberSaveable(key) { mutableIntStateOf((existing?.trialReminders ?: setOf(1)).mask()) }
    var error by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showCategory by rememberSaveable { mutableStateOf(false) }
    var customCategory by rememberSaveable { mutableStateOf("") }
    val categoryOptions = categories.ifEmpty { defaultCategories }
    val category = categoryOptions.firstOrNull { it.id == categoryId } ?: categoryOptions.last()

    if (showCatalog) {
        AlertDialog(onDismissRequest = { showCatalog = false },
            title = { Text(stringResource(R.string.service_catalog)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(catalog) { service ->
                        TextButton(onClick = {
                            name = service.name
                            categoryId = service.categoryId
                            showCatalog = false
                        }, modifier = Modifier.fillMaxWidth()) { Text(service.name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showCatalog = false }) { Text(stringResource(R.string.close)) } })
    }
    if (showCategory) {
        AlertDialog(onDismissRequest = { showCategory = false },
            title = { Text(stringResource(R.string.new_category)) },
            text = { OutlinedTextField(customCategory, { customCategory = it.take(60) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = customCategory.isNotBlank(), onClick = {
                    vm.category(customCategory) {
                        categoryId = it
                        showCategory = false
                        customCategory = ""
                    }
                }) { Text(stringResource(R.string.save)) }
            }, dismissButton = { TextButton(onClick = { showCategory = false }) { Text(stringResource(R.string.cancel)) } })
    }

    ScreenList {
        item {
            Panel {
                if (existing == null) OutlinedButton(onClick = { showCatalog = true }) { Text(stringResource(R.string.service_catalog)) }
                Field(name, { name = it.take(100) }, R.string.name, Modifier.testTag("subscription-name"))
                Choice(stringResource(R.string.category), category, categoryOptions, { it.label() }) { categoryId = it.id }
                TextButton(onClick = { showCategory = true }) { Text(stringResource(R.string.new_category)) }
                Choice(stringResource(R.string.type), type, SubscriptionType.entries,
                    { stringResource(if (it == SubscriptionType.TRIAL) R.string.trial else R.string.standard) }) {
                    type = it
                    if (type == SubscriptionType.TRIAL && status == Status.ACTIVE) status = Status.TRIAL
                    if (type == SubscriptionType.STANDARD && status == Status.TRIAL) status = Status.ACTIVE
                }
                Choice(stringResource(R.string.status), status,
                    Status.entries.filter { it != Status.TRIAL || type == SubscriptionType.TRIAL }, { it.label() }) { status = it }
                if (status == Status.CANCELLED) Text(stringResource(R.string.cancel_provider))
            }
        }
        item {
            Panel {
                Section(stringResource(R.string.billing))
                Field(amount, { amount = it.take(20) }, if (type == SubscriptionType.TRIAL) R.string.after_trial_cost else R.string.amount,
                    Modifier.testTag("subscription-amount"), keyboard = KeyboardType.Decimal)
                CurrencyChoice(currency) { currency = it }
                DateField(start, { start = it }, R.string.start_date)
                if (type == SubscriptionType.TRIAL) {
                    DateField(trialStart, { trialStart = it }, R.string.trial_start)
                    DateField(trialEnd, { trialEnd = it }, R.string.trial_end)
                    Text(stringResource(R.string.trial_explanation), style = MaterialTheme.typography.bodySmall)
                }
                DateField(next, { next = it }, R.string.next_payment)
                Choice(stringResource(R.string.recurrence), presetIndex, presets.indices.toList(),
                    { stringResource(presets[it].title) }) {
                    presetIndex = it
                    if (it != presets.lastIndex) {
                        interval = presets[it].count.toString()
                        unit = presets[it].unit
                    }
                }
                if (presetIndex == presets.lastIndex) {
                    Field(interval, { interval = it.filter(Char::isDigit).take(4) }, R.string.interval, keyboard = KeyboardType.Number)
                    Choice(stringResource(R.string.unit), unit, IntervalUnit.entries, { it.label() }) { unit = it }
                }
            }
        }
        item {
            Panel {
                Section(stringResource(R.string.payment_reminders))
                ReminderChips(paymentMask) { paymentMask = it }
                if (type == SubscriptionType.TRIAL) {
                    Section(stringResource(R.string.trial_reminders))
                    ReminderChips(trialMask) { trialMask = it }
                }
                Field(payment, { payment = it.take(120) }, R.string.payment_label)
                Field(notes, { notes = it.take(2000) }, R.string.notes, singleLine = false)
            }
        }
        item {
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            Button(enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("save-subscription"), onClick = {
                val price = runCatching { Money.parse(amount, currency) }.getOrNull()
                if (price == null) {
                    error = R.string.invalid_amount
                    return@Button
                }
                val candidate = runCatching {
                    val date = LocalDate.parse(next).toEpochDay()
                    val count = interval.toInt()
                    val first = LocalDate.parse(start)
                    val rule = billingRule(count, unit, first, LocalDate.ofEpochDay(date), existing?.repeat)
                    val now = System.currentTimeMillis()
                    Subscription(existing?.id ?: uuid(), name.trim(), category.id, price, first.toEpochDay(),
                        date, rule, type, status, payment.trim(), notes.trim(),
                        if (type == SubscriptionType.TRIAL) LocalDate.parse(trialStart).toEpochDay() else null,
                        if (type == SubscriptionType.TRIAL) LocalDate.parse(trialEnd).toEpochDay() else null,
                        existing?.lastUsed, existing?.createdAt ?: now, now, paymentMask.offsets(), trialMask.offsets())
                        .also(::validateSubscription)
                }.getOrNull()
                if (candidate == null) error = R.string.invalid_fields
                else {
                    error = 0
                    saving = true
                    vm.save(candidate) { saved -> saving = false; if (saved) done() }
                }
            }) { Text(stringResource(R.string.save)) }
        }
    }
}

private fun Set<Int>.mask() = fold(0) { mask, value -> mask or (1 shl value) }
private fun Int.offsets() = reminderOffsets.filter { this and (1 shl it) != 0 }.toSet()

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReminderChips(mask: Int, change: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        reminderOffsets.sortedDescending().forEach { offset ->
            val bit = 1 shl offset
            FilterChip(mask and bit != 0, { change(mask xor bit) }, label = {
                Text(if (offset == 0) stringResource(R.string.same_day) else pluralStringResource(R.plurals.days_before, offset, offset))
            })
        }
    }
}

@Composable private fun Field(value: String, change: (String) -> Unit, label: Int, modifier: Modifier = Modifier,
                              keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(value, change, modifier.fillMaxWidth(), label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), singleLine = singleLine)
}

@Composable private fun DateField(value: String, change: (String) -> Unit, label: Int) {
    val context = LocalContext.current
    OutlinedTextField(value, { change(it.take(10)) }, Modifier.fillMaxWidth(), singleLine = true,
        label = { Text(stringResource(label)) }, supportingText = { Text("YYYY-MM-DD") },
        trailingIcon = {
            IconButton(onClick = {
                val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                    .coerceIn(minDate, maxDate)
                DatePickerDialog(context, { _, year, month, day ->
                    change(LocalDate.of(year, month + 1, day).toString())
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
            }) { Icon(Icons.Default.DateRange, stringResource(R.string.choose_date)) }
        })
}

@Composable fun CurrencyChoice(current: String, change: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val locale = uiLocale()
    val currencies = remember(locale) { Money.currencies.map { it to Currency.getInstance(it).getDisplayName(locale) } }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("${stringResource(R.string.currency)} · $current")
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, title = { Text(stringResource(R.string.currency)) },
        text = {
            Column {
                OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(currencies.filter { (code, label) -> code.contains(search, true) || label.contains(search, true) }) { (code, label) ->
                        TextButton(onClick = { change(code); open = false; search = "" }) { Text("$code · $label") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
fun SubscriptionDetails(sub: Subscription, data: Snapshot, vm: RadarViewModel, edit: () -> Unit, deleted: () -> Unit) {
    val today = LocalDate.now()
    val history = remember(data.prices, sub.id) { data.prices.filter { it.subscriptionId == sub.id }.asReversed() }
    val category = data.categories.firstOrNull { it.id == sub.categoryId }
    var dialog by rememberSaveable { mutableIntStateOf(0) }
    if (dialog != 0) ConfirmDialog(
        stringResource(if (dialog == 1) R.string.archive else R.string.delete),
        stringResource(if (dialog == 1) R.string.archive_message else R.string.delete_message),
        { dialog = 0 }, {
            val archive = dialog == 1
            dialog = 0
            if (archive) vm.archive(sub.id) else vm.delete(sub.id) { if (it) deleted() }
        }
    )
    ScreenList {
        item {
            Panel {
                Section(sub.name)
                Text(listOfNotNull(category?.label(), sub.status.label(), sub.repeat.label()).joinToString(" · "))
                Metric(stringResource(R.string.amount), sub.price.label())
                if (sub.scheduled) {
                    val first = maxOf(today, LocalDate.ofEpochDay(sub.nextPayment))
                    val next = sub.repeat.dates(first, maxDate.plusDays(1)).firstOrNull()
                    Metric(stringResource(R.string.next_payment), next?.label() ?: stringResource(R.string.no_amount))
                }
                Metric(stringResource(R.string.start_date), LocalDate.ofEpochDay(sub.start).label())
                sub.lastUsed?.let { Metric(stringResource(R.string.last_used), LocalDate.ofEpochDay(it).label()) }
                if (sub.paymentLabel.isNotBlank()) Metric(stringResource(R.string.payment_label), sub.paymentLabel)
                if (sub.notes.isNotBlank()) Metric(stringResource(R.string.notes), sub.notes)
                if (sub.status == Status.CANCELLED || sub.status == Status.ARCHIVED) Text(stringResource(R.string.cancel_provider))
                Button(onClick = edit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.edit_subscription)) }
                OutlinedButton(onClick = { vm.used(sub.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_used)) }
            }
        }
        if (sub.type == SubscriptionType.TRIAL) item {
            Panel {
                Section(stringResource(R.string.trial_guard))
                val end = requireNotNull(sub.trialEnd)
                Metric(stringResource(R.string.trial_start), LocalDate.ofEpochDay(requireNotNull(sub.trialStart)).label())
                Metric(stringResource(R.string.trial_end), LocalDate.ofEpochDay(end).label())
                if (end >= today.toEpochDay()) {
                    val days = (end - today.toEpochDay()).toInt()
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.days_remaining, days, days))
                } else Text(stringResource(R.string.trial_ended))
                Metric(stringResource(R.string.first_possible_payment), LocalDate.ofEpochDay(sub.nextPayment).label())
                Metric(stringResource(R.string.after_trial_cost), sub.price.label())
                Text(stringResource(R.string.trial_explanation))
            }
        }
        if (history.isNotEmpty()) {
            item { Section(stringResource(R.string.price_history)) }
            items(history, key = { it.id }) {
                Panel { Text(LocalDate.ofEpochDay(it.effectiveDate).label()); Text("${it.previous.label()} → ${it.next.label()}") }
            }
        }
        item {
            if (sub.status != Status.ARCHIVED) OutlinedButton(onClick = { dialog = 1 }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.archive))
            }
            TextButton(onClick = { dialog = 2 }, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
