package com.subradar.app

import com.subradar.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ScheduleTest {
    @Test fun monthlyClampsShortMonthsWithoutChangingAnchor() {
        for (day in 29..31) {
            val anchor = LocalDate.of(2025, 1, day)
            val rule = RepeatRule(1, IntervalUnit.MONTHS, anchor.toEpochDay())
            assertEquals(listOf(anchor, date("2025-02-28"), LocalDate.of(2025, 3, day)),
                rule.dates(anchor, date("2025-04-01")).toList())
        }
    }

    @Test fun annualLeapDayReturnsInLeapYear() {
        val rule = RepeatRule(12, IntervalUnit.MONTHS, date("2024-02-29").toEpochDay())
        assertEquals(listOf(date("2025-02-28"), date("2026-02-28"), date("2027-02-28"), date("2028-02-29")),
            rule.dates(date("2024-03-01"), date("2029-01-01")).toList())
    }

    @Test fun editingAClampedDatePreservesTheOriginalBillingDay() {
        val start = date("2025-01-31")
        val next = date("2025-02-28")
        val rule = billingRule(1, IntervalUnit.MONTHS, start, next, null)
        assertEquals(start.toEpochDay(), rule.anchor)
        assertEquals(rule, billingRule(1, IntervalUnit.MONTHS, start, date("2025-03-31"), rule))
        assertEquals(date("2025-03-31"), rule.dates(next.plusDays(1), date("2025-04-01")).single())
    }

    @Test fun missedDatesAndCustomIntervals() {
        val rule = RepeatRule(2, IntervalUnit.WEEKS, date("2024-01-01").toEpochDay())
        assertEquals(listOf(date("2025-01-13"), date("2025-01-27")),
            rule.dates(date("2025-01-01"), date("2025-02-01")).toList())
    }

    @Test fun priceHistoryAndConfirmationKeepCurrenciesAndTotalsSeparate() {
        val sub = sample()
        val yen = sub.copy(id = UUID.randomUUID().toString(), price = Money(350, "JPY"))
        val change = PriceChange(UUID.randomUUID().toString(), sub.id, Money(250, "USD"), sub.price,
            date("2025-02-01").toEpochDay(), 2)
        val paid = ConfirmedPayment(UUID.randomUUID().toString(), sub.id, sub.nextPayment, change.previous, 2)
        val snapshot = Snapshot(listOf(sub, yen), prices = listOf(change), payments = listOf(paid))
        val jan = snapshot.occurrences(date("2025-01-01"), date("2025-02-01"))
        assertEquals(listOf(Money(350, "JPY"), Money(250, "USD")), jan.map { it.price }.totals())
        assertEquals(listOf(Money(350, "JPY")),
            snapshot.unconfirmed(date("2025-01-01"), date("2025-02-01")).map { it.price }.totals())
        assertEquals(listOf(Money(350, "JPY"), Money(499, "USD")),
            snapshot.unconfirmed(date("2025-02-01"), date("2025-03-01")).map { it.price }.totals())
    }

    @Test fun trialAndPaymentRemindersAreIndependentAndSkipConfirmed() {
        val sub = sample().copy(type = SubscriptionType.TRIAL, status = Status.TRIAL,
            trialStart = date("2025-01-01").toEpochDay(), trialEnd = date("2025-01-07").toEpochDay(),
            paymentReminders = setOf(0), trialReminders = setOf(1))
        val data = Snapshot(listOf(sub))
        assertTrue(data.remindersOn(date("2025-01-06")).single().trial)
        assertTrue(data.remindersOn(date("2025-01-07")).isEmpty())
        assertFalse(data.remindersOn(date("2025-01-31")).single().trial)
        val paid = ConfirmedPayment(UUID.randomUUID().toString(), sub.id, sub.nextPayment, sub.price, 4)
        assertTrue(data.copy(payments = listOf(paid)).remindersOn(date("2025-01-31")).isEmpty())
        assertTrue(Snapshot(listOf(sub.copy(status = Status.ARCHIVED))).remindersOn(date("2025-01-06")).isEmpty())
    }

    @Test fun amountUsesCurrencySpecificPrecision() {
        assertEquals(Money(1234, "KWD"), Money.parse("1,234", "KWD"))
        assertEquals(Money(17, "JPY"), Money.parse("17", "JPY"))
        assertThrows(ArithmeticException::class.java) { Money.parse("17.5", "JPY") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("-1", "USD") }
    }
}

internal fun date(text: String): LocalDate = LocalDate.parse(text)
internal fun sample(): Subscription {
    val anchor = date("2025-01-31").toEpochDay()
    return Subscription(UUID.randomUUID().toString(), "Sample", "other", Money(499, "USD"),
        date("2025-01-01").toEpochDay(), anchor, RepeatRule(1, IntervalUnit.MONTHS, anchor),
        createdAt = 1, updatedAt = 1)
}
