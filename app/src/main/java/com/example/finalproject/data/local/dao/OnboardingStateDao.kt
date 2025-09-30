// app/src/main/java/com/example/finalproject/data/local/dao/OnboardingStateDao.kt
package com.example.finalproject.data.local.dao

import com.example.finalproject.data.models.onboarding.OnboardingState
import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface OnboardingStateDao {

    @Query("SELECT * FROM onboarding_state WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): OnboardingState?

    @Query("SELECT * FROM onboarding_state WHERE uid = :uid LIMIT 1")
    fun observe(uid: String): LiveData<OnboardingState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: OnboardingState)

    @Query("DELETE FROM onboarding_state WHERE uid = :uid")
    suspend fun delete(uid: String)
}
