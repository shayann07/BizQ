// app/src/main/java/com/example/finalproject/data/local/onboarding/OnboardingDao.kt
package com.example.finalproject.data.local.onboarding

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OnboardingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OnboardingEntity)

    @Query("SELECT * FROM onboarding_drafts WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): OnboardingEntity?

    @Query("DELETE FROM onboarding_drafts WHERE uid = :uid")
    suspend fun delete(uid: String)
}
