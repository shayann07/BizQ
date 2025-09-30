// data/local/dao/AvailabilityDao.kt
package com.example.finalproject.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.finalproject.data.models.Availability

@Dao
interface AvailabilityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: Availability)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<Availability>)

    @Query("SELECT * FROM availability WHERE businessId = :businessId ORDER BY dayOfWeek ASC")
    fun observeWeek(businessId: String): LiveData<List<Availability>>

    @Query("SELECT * FROM availability WHERE businessId = :businessId ORDER BY dayOfWeek ASC")
    suspend fun getWeek(businessId: String): List<Availability>

    @Query("DELETE FROM availability WHERE businessId = :businessId")
    suspend fun deleteWeek(businessId: String)
}
