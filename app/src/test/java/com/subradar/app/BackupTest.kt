package com.subradar.app

import com.subradar.app.data.Backup
import com.subradar.app.data.BackupCodec
import com.subradar.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val sub = sample().copy(name = "=SUM(1,2) \"example\"")
    private val backup = Backup(1, Snapshot(listOf(sub), defaultCategories), Preferences(currency = "RUB"), "ru")

    @Test fun jsonRoundTrip() = assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))

    @Test fun rejectsUnsupportedVersionAndBrokenReferences() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(backup.copy(version = 2))) }
        val bad = backup.copy(data = backup.data.copy(subscriptions = listOf(sub.copy(categoryId = "missing"))))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("{") }
    }

    @Test fun rejectsScheduleWithImpossibleDate() {
        val bad = backup.copy(data = backup.data.copy(subscriptions = listOf(sub.copy(nextPayment = sub.nextPayment + 1))))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
    }

    @Test fun csvEscapesQuotesAndFormulaPrefixes() {
        val text = BackupCodec.csv(backup.data)
        assertTrue(text.contains("\"'=SUM(1,2) \"\"example\"\"\""))
        assertTrue(text.endsWith("\r\n"))
    }
}
