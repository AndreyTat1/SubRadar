package com.subradar.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subradar.app.data.*
import com.subradar.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StorageTest {
    @Test fun createEditRestartArchiveAndDelete() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${uuid()}.db"
        fun open() = Room.databaseBuilder(context, RadarDatabase::class.java, name).build()
        val today = LocalDate.of(2025, 1, 1)
        val day = today.toEpochDay()
        val sub = Subscription(uuid(), "Original", "other", Money(99, "USD"), day, day,
            RepeatRule(1, IntervalUnit.MONTHS, day), createdAt = 0, updatedAt = 0)
        try {
            open().withOpenDatabase { db ->
                val repo = LocalRadarRepository(db)
                repo.snapshots.first()
                repo.save(sub, today)
                repo.confirm(sub.id, today, today)
                repo.save(sub.copy(name = "Edited", price = Money(199, "USD")), today.plusDays(1))
                assertEquals(99L, repo.snapshot().payments.single().amount.minor)
            }
            open().withOpenDatabase { db ->
                val repo = LocalRadarRepository(db)
                assertEquals("Edited", repo.snapshot().subscriptions.single().name)
                assertEquals(99L, repo.snapshot().prices.single().previous.minor)
                repo.archive(sub.id)
                assertTrue(repo.snapshot().occurrences(today, today.plusMonths(3)).isEmpty())
                assertEquals(Status.ARCHIVED, repo.snapshot().subscriptions.single().status)
                repo.delete(sub.id)
                assertTrue(repo.snapshot().subscriptions.isEmpty())
                assertTrue(repo.snapshot().payments.isEmpty())
                assertTrue(repo.snapshot().prices.isEmpty())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationPreservesSubscriptionAndAddsNotificationTable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${uuid()}.db"
        val today = LocalDate.of(2025, 1, 1)
        val day = today.toEpochDay()
        val sub = Subscription(uuid(), "Before migration", "other", Money(99, "USD"), day, day,
            RepeatRule(1, IntervalUnit.MONTHS, day), createdAt = 0, updatedAt = 0)
        try {
            Room.databaseBuilder(context, RadarDatabase::class.java, name).build().withOpenDatabase { db ->
                val repo = LocalRadarRepository(db)
                repo.snapshots.first()
                repo.save(sub, today)
            }
            android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            ).use { sqlite ->
                sqlite.execSQL("DROP TABLE notification_deliveries")
                sqlite.execSQL("DROP TABLE room_master_table")
                sqlite.version = 1
            }
            Room.databaseBuilder(context, RadarDatabase::class.java, name).addMigrations(migration1To2)
                .build().withOpenDatabase { db ->
                    assertEquals(sub.name, LocalRadarRepository(db).snapshot().subscriptions.single().name)
                    assertTrue(db.dao().claim(DeliveryRow("after-migration", day)) != -1L)
                }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun notificationClaimDeduplicatesAndUsesSeparateDays() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Room.inMemoryDatabaseBuilder(context, RadarDatabase::class.java).build().withOpenDatabase { db ->
            val dao = db.dao()
            val delivery = DeliveryRow("subscription/20000/false", 19999)
            assertTrue(dao.claim(delivery) != -1L)
            assertEquals(-1L, dao.claim(delivery))
            assertTrue(dao.claim(delivery.copy(day = 20000)) != -1L)
        }
    }
}

private inline fun RadarDatabase.withOpenDatabase(block: (RadarDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
