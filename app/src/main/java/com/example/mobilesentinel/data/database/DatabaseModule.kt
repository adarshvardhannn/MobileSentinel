package com.example.mobilesentinel.data.database

import android.content.Context
import androidx.room.Room
import com.example.mobilesentinel.data.database.dao.AIExplanationDao
import com.example.mobilesentinel.data.database.dao.FindingDao
import com.example.mobilesentinel.data.database.dao.LogEntryDao
import com.example.mobilesentinel.data.database.dao.PortResultDao
import com.example.mobilesentinel.data.database.dao.ScanSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mobile_sentinel_db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideScanSessionDao(database: AppDatabase): ScanSessionDao {
        return database.scanSessionDao()
    }

    @Provides
    fun provideFindingDao(database: AppDatabase): FindingDao {
        return database.findingDao()
    }

    @Provides
    fun provideLogEntryDao(database: AppDatabase): LogEntryDao {
        return database.logEntryDao()
    }

    @Provides
    fun providePortResultDao(database: AppDatabase): PortResultDao {
        return database.portResultDao()
    }

    @Provides
    fun provideAIExplanationDao(database: AppDatabase): AIExplanationDao {
        return database.aiExplanationDao()
    }
}

