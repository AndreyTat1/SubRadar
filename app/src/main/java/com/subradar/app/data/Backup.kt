package com.subradar.app.data

import com.subradar.app.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

@Serializable data class Backup(val version: Int, val data: Snapshot, val preferences: Preferences, val language: String)

object BackupCodec {
    const val limit = 8 * 1024 * 1024
    private val json = Json { encodeDefaults = true; prettyPrint = true }
    fun encode(backup: Backup): String = json.encodeToString(backup)
    fun decode(raw: String): Backup {
        require(raw.length <= limit)
        return json.decodeFromString<Backup>(raw).also {
            require(it.version == 1)
            require(it.language in setOf("", "en", "ru"))
            require(it.preferences.currency in Money.currencies)
            validateSnapshot(it.data)
        }
    }

    fun csv(snapshot: Snapshot): String = buildString {
        append("id,name,category,amount_minor,currency,start,next_payment,interval,unit,status\r\n")
        val categories = snapshot.categories.associate { it.id to it.name }
        for (sub in snapshot.subscriptions) {
            append(listOf(sub.id, sub.name, categories[sub.categoryId].orEmpty(), sub.price.minor.toString(),
                sub.price.currency, LocalDate.ofEpochDay(sub.start).toString(),
                LocalDate.ofEpochDay(sub.nextPayment).toString(), sub.repeat.count.toString(),
                sub.repeat.unit.name, sub.status.name).joinToString(",") { value ->
                val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
                "\"${safe.replace("\"", "\"\"")}\""
            })
            append("\r\n")
        }
    }
}

private fun validUuid(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
private fun validDay(day: Long) = day in minDate.toEpochDay()..maxDate.toEpochDay()
private fun validMoney(money: Money) = money.minor in 0..MAX_PRICE && money.currency in Money.currencies
private fun validTimestamp(value: Long) = value in 0..253402300799999L

fun validateSubscription(sub: Subscription) {
    require(validUuid(sub.id) && sub.name.isNotBlank() && sub.name.length <= 100)
    require(validMoney(sub.price))
    require(validDay(sub.start) && validDay(sub.nextPayment) && sub.nextPayment >= sub.start)
    require(sub.repeat.count in 1..3650 && validDay(sub.repeat.anchor) && sub.repeat.anchor <= sub.nextPayment)
    val next = LocalDate.ofEpochDay(sub.nextPayment)
    require(sub.repeat.dates(next, next.plusDays(1)).any())
    require(sub.paymentLabel.length <= 120 && sub.notes.length <= 2000)
    require(validTimestamp(sub.createdAt) && validTimestamp(sub.updatedAt) && sub.updatedAt >= sub.createdAt)
    require(sub.lastUsed?.let(::validDay) != false)
    require(reminderOffsets.containsAll(sub.paymentReminders + sub.trialReminders))
    if (sub.type == SubscriptionType.TRIAL) {
        val start = requireNotNull(sub.trialStart)
        val end = requireNotNull(sub.trialEnd)
        require(validDay(start) && validDay(end) && start >= sub.start && end >= start && sub.nextPayment >= end)
    } else {
        require(sub.status != Status.TRIAL && sub.trialStart == null && sub.trialEnd == null)
    }
}

fun validateSnapshot(data: Snapshot) {
    require(data.subscriptions.size <= 5000 && data.categories.size <= 1000)
    require(data.payments.size <= 50000 && data.prices.size <= 50000)
    val defaults = defaultCategories.associateBy { it.id }
    require(data.categories.map { it.id }.toSet().size == data.categories.size)
    data.categories.forEach {
        require((defaults[it.id] == it || validUuid(it.id)) && it.name.isNotBlank() &&
            it.name.length <= 60 && it.icon.length in 1..12)
    }
    val categoryIds = data.categories.map { it.id }.toSet() + defaults.keys
    val ids = data.subscriptions.map { it.id }.toSet()
    require(ids.size == data.subscriptions.size)
    data.subscriptions.forEach { validateSubscription(it); require(it.categoryId in categoryIds) }
    require(data.prices.map { it.id }.toSet().size == data.prices.size)
    data.prices.forEach {
        require(validUuid(it.id) && it.subscriptionId in ids && validMoney(it.previous) &&
            validMoney(it.next) && validDay(it.effectiveDate) && validTimestamp(it.recordedAt))
    }
    require(data.payments.map { it.id }.toSet().size == data.payments.size)
    require(data.payments.map { it.subscriptionId to it.scheduledDate }.toSet().size == data.payments.size)
    data.payments.forEach {
        require(validUuid(it.id) && it.subscriptionId in ids && validMoney(it.amount) &&
            validDay(it.scheduledDate) && validTimestamp(it.confirmedAt))
    }
}
