package com.subradar.app.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

@Serializable enum class IntervalUnit { DAYS, WEEKS, MONTHS }
@Serializable enum class Status { ACTIVE, TRIAL, PAUSED, CANCELLED, ARCHIVED }
@Serializable enum class SubscriptionType { STANDARD, TRIAL }
@Serializable enum class Theme { DARK, LIGHT }

@Serializable data class RepeatRule(val count: Int, val unit: IntervalUnit, val anchor: Long)
@Serializable data class Money(val minor: Long, val currency: String) {
    fun formatted(locale: Locale): String {
        val unit = Currency.getInstance(currency)
        return NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = unit
            minimumFractionDigits = unit.defaultFractionDigits
            maximumFractionDigits = unit.defaultFractionDigits
        }.format(BigDecimal.valueOf(minor, unit.defaultFractionDigits))
    }

    companion object {
        val currencies = Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 }
            .map { it.currencyCode }.sorted()
        fun parse(text: String, currency: String): Money {
            val digits = Currency.getInstance(currency).defaultFractionDigits
            val raw = text.trim().replace(',', '.')
            require(digits >= 0 && raw.matches(Regex("[0-9]{1,14}(\\.[0-9]{1,4})?")))
            return Money(
                BigDecimal(raw).movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact().also { require(it in 0..MAX_PRICE) },
                currency
            )
        }
    }
}

const val MAX_PRICE = 100_000_000_000L
val reminderOffsets = setOf(0, 1, 3, 7)
val minDate: LocalDate = LocalDate.of(1900, 1, 1)
val maxDate: LocalDate = LocalDate.of(9999, 12, 31)

@Serializable data class Subscription(
    val id: String,
    val name: String,
    val categoryId: String,
    val price: Money,
    val start: Long,
    val nextPayment: Long,
    val repeat: RepeatRule,
    val type: SubscriptionType = SubscriptionType.STANDARD,
    val status: Status = Status.ACTIVE,
    val paymentLabel: String = "",
    val notes: String = "",
    val trialStart: Long? = null,
    val trialEnd: Long? = null,
    val lastUsed: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val paymentReminders: Set<Int> = setOf(1),
    val trialReminders: Set<Int> = setOf(1)
) {
    val scheduled get() = status == Status.ACTIVE || status == Status.TRIAL
}

@Serializable data class Category(val id: String, val name: String, val icon: String)
@Serializable data class PriceChange(
    val id: String, val subscriptionId: String, val previous: Money, val next: Money,
    val effectiveDate: Long, val recordedAt: Long
)
@Serializable data class ConfirmedPayment(
    val id: String, val subscriptionId: String, val scheduledDate: Long,
    val amount: Money, val confirmedAt: Long
)
@Serializable data class Preferences(
    val theme: Theme = Theme.DARK,
    val currency: String = "USD",
    val notifications: Boolean = true,
    val mondayFirst: Boolean = true
)
@Serializable data class Snapshot(
    val subscriptions: List<Subscription> = emptyList(),
    val categories: List<Category> = emptyList(),
    val prices: List<PriceChange> = emptyList(),
    val payments: List<ConfirmedPayment> = emptyList()
)
data class Occurrence(val subscription: Subscription, val date: LocalDate, val price: Money)
data class Service(val name: String, val categoryId: String)

val defaultCategories = listOf(
    Category("video", "Video", "▶"), Category("music", "Music", "♫"),
    Category("games", "Games", "◇"), Category("software", "Software", "⌘"),
    Category("cloud", "Cloud", "☁"), Category("education", "Education", "✎"),
    Category("health", "Health & fitness", "♥"), Category("work", "Work", "▣"),
    Category("other", "Other", "•")
)
val catalog = listOf(
    Service("Netflix", "video"), Service("YouTube Premium", "video"),
    Service("Spotify", "music"), Service("Apple Music", "music"),
    Service("Яндекс Плюс", "music"), Service("Xbox Game Pass", "games"),
    Service("Microsoft 365", "software"), Service("iCloud+", "cloud"),
    Service("Google One", "cloud"), Service("Duolingo", "education"),
    Service("Strava", "health"), Service("Notion", "work")
)
