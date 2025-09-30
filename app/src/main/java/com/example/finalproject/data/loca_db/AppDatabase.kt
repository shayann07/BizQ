package com.example.finalproject.data.local_db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.finalproject.data.loca_db.BusinessDao
import com.example.finalproject.data.loca_db.ServiceDao
import com.example.finalproject.data.local.dao.AvailabilityDao
import com.example.finalproject.data.loca_db.UserDao
import com.example.finalproject.data.local.dao.OnboardingStateDao
import com.example.finalproject.data.local.onboarding.OnboardingEntity
import com.example.finalproject.data.models.*

// NOTE: version bumped to 3 to reflect the users table change to uid String PK.
@Database(
    entities = [
        Business::class,
        Service::class,
        Availability::class,
        User::class,
        OnboardingEntity::class,
        com.example.finalproject.data.models.onboarding.OnboardingState::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun serviceDao(): ServiceDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun userDao(): UserDao
    abstract fun onboardingStateDao(): OnboardingStateDao
    abstract fun onboardingDao(): com.example.finalproject.data.local.onboarding.OnboardingDao


}
