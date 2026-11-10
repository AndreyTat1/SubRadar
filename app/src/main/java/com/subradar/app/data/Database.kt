package com.subradar.app.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.subradar.app.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryRow(@PrimaryKey val id: String, val name: String, val icon: String) {
    fun model() = Category(id, name, icon)
}
fun Category.row() = CategoryRow(id, name, icon)

@Entity(tableName = "subscriptions", foreignKeys = [ForeignKey(
    entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"]
)], indices = [Index("categoryId"), Index("nextPaymentDate")])
data class SubscriptionRow(
    @PrimaryKey val id: String, val name: String, val categoryId: String,
    val priceMinorUnits: Long, val currencyCode: String, val startDate: Long,
    val nextPaymentDate: Long, val repeatCount: Int, val repeatUnit: String, val repeatAnchor: Long,
    val type: String, val status: String, val paymentLabel: String, val notes: String,
    val trialStartDate: Long?, val trialEndDate: Long?, val lastUsedDate: Long?,
    val createdAt: Long, val updatedAt: Long
) {
    fun model(settings: ReminderRow?) = Subscription(
        id, name, categoryId, Money(priceMinorUnits, currencyCode), startDate, nextPaymentDate,
        RepeatRule(repeatCount, IntervalUnit.valueOf(repeatUnit), repeatAnchor),
        SubscriptionType.valueOf(type), Status.valueOf(status), paymentLabel, notes, trialStartDate,
        trialEndDate, lastUsedDate, createdAt, updatedAt,
        settings?.paymentMask?.offsets() ?: emptySet(), settings?.trialMask?.offsets() ?: emptySet()
    )
}
fun Subscription.row() = SubscriptionRow(
    id, name, categoryId, price.minor, price.currency, start, nextPayment, repeat.count,
    repeat.unit.name, repeat.anchor, type.name, status.name, paymentLabel, notes,
    trialStart, trialEnd, lastUsed, createdAt, updatedAt
)

@Entity(tableName = "notification_settings", foreignKeys = [ForeignKey(
    entity = SubscriptionRow::class, parentColumns = ["id"], childColumns = ["subscriptionId"],
    onDelete = ForeignKey.CASCADE
)])
data class ReminderRow(@PrimaryKey val subscriptionId: String, val paymentMask: Int, val trialMask: Int)
private fun Int.offsets() = reminderOffsets.filter { this and (1 shl it) != 0 }.toSet()
fun Subscription.reminders() = ReminderRow(id, paymentReminders.mask(), trialReminders.mask())
private fun Set<Int>.mask() = fold(0) { mask, days -> mask or (1 shl days) }

@Entity(tableName = "price_history", foreignKeys = [ForeignKey(
    entity = SubscriptionRow::class, parentColumns = ["id"], childColumns = ["subscriptionId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["subscriptionId", "effectiveDate"])])
data class PriceRow(
    @PrimaryKey val id: String, val subscriptionId: String, val oldMinor: Long,
    val oldCurrency: String, val newMinor: Long, val newCurrency: String,
    val effectiveDate: Long, val recordedAt: Long
) {
    fun model() = PriceChange(id, subscriptionId, Money(oldMinor, oldCurrency),
        Money(newMinor, newCurrency), effectiveDate, recordedAt)
}
fun PriceChange.row() = PriceRow(id, subscriptionId, previous.minor, previous.currency,
    next.minor, next.currency, effectiveDate, recordedAt)

@Entity(tableName = "payment_occurrences", foreignKeys = [ForeignKey(
    entity = SubscriptionRow::class, parentColumns = ["id"], childColumns = ["subscriptionId"],
    onDelete = ForeignKey.CASCADE
)], indices = [Index(value = ["subscriptionId", "scheduledDate"], unique = true)])
data class PaymentRow(
    @PrimaryKey val id: String, val subscriptionId: String, val scheduledDate: Long,
    val amountMinor: Long, val currency: String, val confirmedAt: Long
) {
    fun model() = ConfirmedPayment(id, subscriptionId, scheduledDate, Money(amountMinor, currency), confirmedAt)
}
fun ConfirmedPayment.row() = PaymentRow(id, subscriptionId, scheduledDate, amount.minor, amount.currency, confirmedAt)

@Entity(tableName = "notification_deliveries", primaryKeys = ["key", "day"])
data class DeliveryRow(val key: String, val day: Long)

@Dao
interface RadarDao {
    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE") suspend fun subscriptions(): List<SubscriptionRow>
    @Query("SELECT * FROM subscriptions WHERE id = :id") suspend fun subscription(id: String): SubscriptionRow?
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM price_history ORDER BY effectiveDate, recordedAt") suspend fun prices(): List<PriceRow>
    @Query("SELECT * FROM payment_occurrences ORDER BY scheduledDate") suspend fun payments(): List<PaymentRow>
    @Query("SELECT * FROM notification_settings") suspend fun settings(): List<ReminderRow>
    @Upsert suspend fun put(row: SubscriptionRow)
    @Upsert suspend fun put(row: ReminderRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCategories(rows: List<CategoryRow>)
    @Insert suspend fun addCategory(row: CategoryRow)
    @Insert suspend fun addPrice(row: PriceRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addPayment(row: PaymentRow): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun claim(row: DeliveryRow): Long
    @Query("DELETE FROM notification_deliveries WHERE day < :before") suspend fun prune(before: Long)
    @Delete suspend fun release(row: DeliveryRow)
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM subscriptions") suspend fun deleteSubscriptions()
    @Query("DELETE FROM categories") suspend fun deleteCategories()
    @Query("DELETE FROM notification_deliveries") suspend fun deleteDeliveries()
    @Query("DELETE FROM payment_occurrences WHERE id = :id") suspend fun removePayment(id: String)
}

@Database(
    entities = [CategoryRow::class, SubscriptionRow::class, ReminderRow::class,
        PriceRow::class, PaymentRow::class, DeliveryRow::class],
    version = 2
)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun dao(): RadarDao
}

val migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notification_deliveries (`key` TEXT NOT NULL, day INTEGER NOT NULL, PRIMARY KEY(`key`, day))")
    }
}
