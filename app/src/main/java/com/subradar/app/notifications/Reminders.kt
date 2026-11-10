package com.subradar.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.subradar.app.MainActivity
import com.subradar.app.R
import com.subradar.app.data.DeliveryRow
import com.subradar.app.data.PreferenceStore
import com.subradar.app.data.RadarDatabase
import com.subradar.app.domain.RadarRepository
import com.subradar.app.domain.remindersOn
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun ensurePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "radar-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(4, TimeUnit.HOURS).build()
        )
    }
    fun checkSoon() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "radar-refresh", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
    fun dismissAll() = NotificationManagerCompat.from(context).cancelAll()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDependencies {
    fun repository(): RadarRepository
    fun database(): RadarDatabase
    fun preferences(): PreferenceStore
}

class ReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            checkReminders()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun checkReminders() {
        val deps = EntryPointAccessors.fromApplication(context, ReminderDependencies::class.java)
        if (!deps.preferences().current().notifications) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val tag = deps.preferences().language()
        val strings = if (tag.isEmpty()) context else context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, strings.getString(R.string.reminders),
            NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return
        val today = LocalDate.now()
        val dao = deps.database().dao()
        dao.prune(today.minusDays(45).toEpochDay())
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(strings.resources.configuration.locales[0])
        for (candidate in deps.repository().snapshot().remindersOn(today)) {
            val delivery = DeliveryRow(candidate.key, today.toEpochDay())
            if (dao.claim(delivery) == -1L) continue
            try {
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.SUBSCRIPTION_ID, candidate.subscription.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val open = PendingIntent.getActivity(context, candidate.key.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val title = if (candidate.trial) R.string.trial_notification else R.string.payment_notification
                val date = candidate.date.format(formatter)
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(strings.getString(title, candidate.subscription.name))
                    .setContentText(strings.getString(R.string.notification_date, date))
                    .setAutoCancel(true).setContentIntent(open).build()
                notifications.notify(candidate.key, 0, notification)
            } catch (e: Exception) {
                dao.release(delivery)
                throw e
            }
        }
    }

    private companion object { const val CHANNEL = "subradar-reminders" }
}
