// app/src/main/java/com/example/finalproject/di/AppModule.kt
package com.example.finalproject.di

import android.content.Context
import androidx.room.Room
import com.example.finalproject.data.local_db.AppDatabase
import com.example.finalproject.data.local.dao.OnboardingStateDao
import com.example.finalproject.data.local.onboarding.OnboardingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, /* DB file name: */ "finalproject.db")
            .build()

    // DAOs (match your AppDatabase)
    @Provides fun provideBusinessDao(db: AppDatabase) = db.businessDao()
    @Provides fun provideServiceDao(db: AppDatabase) = db.serviceDao()
    @Provides fun provideAvailabilityDao(db: AppDatabase) = db.availabilityDao()
    @Provides fun provideUserDao(db: AppDatabase) = db.userDao()

    // onboarding state + draft
    @Provides fun provideOnboardingStateDao(db: AppDatabase): OnboardingStateDao = db.onboardingStateDao()
    @Provides fun provideOnboardingDao(db: AppDatabase): OnboardingDao = db.onboardingDao()




}
