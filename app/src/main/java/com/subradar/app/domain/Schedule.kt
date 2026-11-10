package com.subradar.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private fun RepeatRule.dateAt(index: Long): LocalDate {
    val date = LocalDate.ofEpochDay(anchor)
    val steps = index * count
    return when (unit) {
        IntervalUnit.DAYS -> date.plusDays(steps)
        IntervalUnit.WEEKS -> date.plusWeeks(steps)
        IntervalUnit.MONTHS -> date.plusMonths(steps)
    }
}

fun RepeatRule.firstOnOrAfter(from: LocalDate): Long {
    val date = LocalDate.ofEpochDay(anchor)
    val distance = when (unit) {
        IntervalUnit.DAYS -> ChronoUnit.DAYS.between(date, from)
        IntervalUnit.WEEKS -> ChronoUnit.WEEKS.between(date, from)
        IntervalUnit.MONTHS -> ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(from))
    }
    val index = (distance / count).coerceAtLeast(0)
    return if (dateAt(index) < from) index + 1 else index
}

fun RepeatRule.dates(from: LocalDate, until: LocalDate): Sequence<LocalDate> =
    generateSequence(firstOnOrAfter(from)) { it + 1 }.map(::dateAt).takeWhile { it < until }

fun billingRule(count: Int, unit: IntervalUnit, start: LocalDate, next: LocalDate, previous: RepeatRule?): RepeatRule {
    require(count in 1..3650)
    if (previous != null && previous.count == count && previous.unit == unit &&
        previous.dates(next, next.plusDays(1)).any()) return previous
    val fromStart = RepeatRule(count, unit, start.toEpochDay())
    if (unit == IntervalUnit.MONTHS && fromStart.dateAt(1) == next) return fromStart
    return RepeatRule(count, unit, next.toEpochDay())
}

private val priceOrder = compareBy(PriceChange::effectiveDate, PriceChange::recordedAt, PriceChange::id)

private fun priceOn(subscription: Subscription, date: LocalDate, history: List<PriceChange>): Money {
    val day = date.toEpochDay()
    // Find the first change strictly after the occurrence, including ties on the effective date.
    var low = 0
    var high = history.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (history[middle].effectiveDate <= day) low = middle + 1 else high = middle
    }
    return if (low > 0) history[low - 1].next else history.firstOrNull()?.previous ?: subscription.price
}

fun Snapshot.occurrences(from: LocalDate, until: LocalDate): List<Occurrence> {
    val history = prices.groupBy { it.subscriptionId }.mapValues { (_, changes) -> changes.sortedWith(priceOrder) }
    return subscriptions.filter { it.scheduled }.flatMap { sub ->
        val first = maxOf(from, LocalDate.ofEpochDay(maxOf(sub.start, sub.nextPayment, sub.trialEnd ?: sub.start)))
        sub.repeat.dates(first, until).map {
            Occurrence(sub, it, priceOn(sub, it, history[sub.id].orEmpty()))
        }.toList()
    }.sortedWith(compareBy({ it.date }, { it.subscription.name }))
}

fun Iterable<Money>.totals(): List<Money> = groupBy { it.currency }.toSortedMap().map { (currency, amounts) ->
    Money(amounts.sumOf { it.minor }, currency)
}

fun Snapshot.unconfirmed(from: LocalDate, until: LocalDate): List<Occurrence> {
    val confirmed = payments.map { it.subscriptionId to it.scheduledDate }.toHashSet()
    return occurrences(from, until).filter { (it.subscription.id to it.date.toEpochDay()) !in confirmed }
}

data class ReminderCandidate(val subscription: Subscription, val date: LocalDate, val trial: Boolean) {
    val key get() = "${subscription.id}/${date.toEpochDay()}/$trial"
}

fun Snapshot.remindersOn(today: LocalDate): List<ReminderCandidate> {
    val payments = unconfirmed(today, today.plusDays(8)).mapNotNull { occurrence ->
        val days = ChronoUnit.DAYS.between(today, occurrence.date).toInt()
        occurrence.takeIf { days in it.subscription.paymentReminders }?.let {
            ReminderCandidate(it.subscription, it.date, false)
        }
    }
    val trials = subscriptions.filter { it.status == Status.TRIAL }.mapNotNull { sub ->
        val end = sub.trialEnd?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
        val days = ChronoUnit.DAYS.between(today, end).toInt()
        sub.takeIf { days in it.trialReminders }?.let { ReminderCandidate(it, end, true) }
    }
    return payments + trials
}
