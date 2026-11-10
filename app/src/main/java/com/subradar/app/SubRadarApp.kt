package com.subradar.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.subradar.app.data.LocalRadarRepository
import com.subradar.app.data.RadarDatabase
import com.subradar.app.data.migration1To2
import com.subradar.app.domain.RadarRepository
import com.subradar.app.notifications.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class SubRadarApp : Application() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onCreate() {
        super.onCreate()
        scheduler.ensurePeriodic()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds abstract fun repository(impl: LocalRadarRepository): RadarRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): RadarDatabase =
        Room.databaseBuilder(context, RadarDatabase::class.java, "subradar.db")
            .addMigrations(migration1To2).build()
}
