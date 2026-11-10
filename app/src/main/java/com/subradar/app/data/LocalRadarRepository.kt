package com.subradar.app.data

import androidx.room.withTransaction
import com.subradar.app.domain.*
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalRadarRepository @Inject constructor(private val db: RadarDatabase) : RadarRepository {
    private val dao = db.dao()
    override val snapshots = flow {
        seed()
        emitAll(db.invalidationTracker.createFlow("categories", "subscriptions", "price_history",
            "payment_occurrences", "notification_settings").map { snapshot() })
    }

    private suspend fun seed() = dao.addCategories(defaultCategories.map { it.row() })

    override suspend fun snapshot(): Snapshot = db.withTransaction {
        val reminders = dao.settings().associateBy { it.subscriptionId }
        Snapshot(dao.subscriptions().map { it.model(reminders[it.id]) },
            dao.categories().map { it.model() }, dao.prices().map { it.model() }, dao.payments().map { it.model() })
    }

    override suspend fun save(subscription: Subscription, today: LocalDate) = db.withTransaction {
        validateSubscription(subscription)
        val old = dao.subscription(subscription.id)
        val now = maxOf(System.currentTimeMillis(), (old?.updatedAt ?: 0L) + 1)
        val updated = subscription.copy(createdAt = old?.createdAt ?: now, updatedAt = now)
        dao.put(updated.row())
        dao.put(updated.reminders())
        if (old != null && (old.priceMinorUnits != updated.price.minor || old.currencyCode != updated.price.currency)) {
            dao.addPrice(PriceChange(uuid(), updated.id, Money(old.priceMinorUnits, old.currencyCode),
                updated.price, today.toEpochDay(), now).row())
        }
    }

    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun archive(id: String) = change(id) { it.copy(status = Status.ARCHIVED.name) }
    override suspend fun markUsed(id: String, date: LocalDate) = change(id) { it.copy(lastUsedDate = date.toEpochDay()) }
    private suspend fun change(id: String, block: (SubscriptionRow) -> SubscriptionRow) = db.withTransaction {
        val row = dao.subscription(id) ?: return@withTransaction
        dao.put(block(row).copy(updatedAt = maxOf(System.currentTimeMillis(), row.updatedAt + 1)))
    }

    override suspend fun addCategory(name: String): Category = db.withTransaction {
        val cleaned = name.trim()
        require(cleaned.length in 1..60 && dao.categories().none { it.name.equals(cleaned, true) })
        Category(uuid(), cleaned, "◆").also { dao.addCategory(it.row()) }
    }

    override suspend fun confirm(subscriptionId: String, date: LocalDate, today: LocalDate) {
        require(date <= today)
        db.withTransaction {
            val occurrence = snapshot().occurrences(date, date.plusDays(1))
                .firstOrNull { it.subscription.id == subscriptionId } ?: error("Missing occurrence")
            dao.addPayment(ConfirmedPayment(uuid(), subscriptionId, date.toEpochDay(),
                occurrence.price, System.currentTimeMillis()).row())
        }
    }

    override suspend fun removePayment(id: String) = dao.removePayment(id)

    override suspend fun replace(snapshot: Snapshot) = db.withTransaction {
        validateSnapshot(snapshot)
        dao.deleteSubscriptions()
        dao.deleteCategories()
        seed()
        dao.addCategories(snapshot.categories.map { it.row() })
        snapshot.subscriptions.forEach { dao.put(it.row()); dao.put(it.reminders()) }
        snapshot.prices.forEach { dao.addPrice(it.row()) }
        snapshot.payments.forEach { dao.addPayment(it.row()) }
    }

    override suspend fun clear() = db.withTransaction {
        dao.deleteSubscriptions()
        dao.deleteCategories()
        dao.deleteDeliveries()
        seed()
    }
}

fun uuid(): String = UUID.randomUUID().toString()
