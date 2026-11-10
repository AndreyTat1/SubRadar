package com.subradar.app

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.subradar.app.data.uuid
import com.subradar.app.domain.*
import com.subradar.app.notifications.ReminderDependencies
import com.subradar.app.notifications.ReminderWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    @Test fun postsOnceAndRespectsDisabledPreferences() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val deps = EntryPointAccessors.fromApplication(context, ReminderDependencies::class.java)
        val prefs = deps.preferences()
        val initial = prefs.current()
        val repo = deps.repository()
        repo.snapshots.first()
        val now = LocalDate.now()
        val day = now.toEpochDay()
        val sub = Subscription(uuid(), "Reminder test", "other", Money(399, "USD"), day, day,
            RepeatRule(1, IntervalUnit.MONTHS, day), createdAt = 0, updatedAt = 0, paymentReminders = setOf(0))
        val key = "${sub.id}/$day/false"
        val shouldGrant = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (shouldGrant) instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            repo.save(sub, now)
            prefs.update { it.copy(notifications = false) }
            suspend fun check() {
                val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
            }
            check()
            assertFalse(manager.activeNotifications.any { it.tag == key })
            prefs.update { it.copy(notifications = true) }
            check()
            assertTrue(manager.activeNotifications.any { it.tag == key })
            manager.cancel(key, 0)
            check()
            assertFalse(manager.activeNotifications.any { it.tag == key })
        } finally {
            manager.cancel(key, 0)
            repo.delete(sub.id)
            prefs.update { initial }
        }
    }
}
