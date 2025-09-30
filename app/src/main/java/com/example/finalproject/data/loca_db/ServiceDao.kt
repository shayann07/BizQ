package com.example.finalproject.data.loca_db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.finalproject.data.models.Service

@Dao
interface ServiceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(service: Service)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(services: List<Service>)

    @Update
    suspend fun update(service: Service)

    @Delete
    suspend fun delete(service: Service)

    @Query("SELECT * FROM services WHERE businessId = :businessId ORDER BY name ASC")
    fun observeServices(businessId: String): LiveData<List<Service>>

    @Query("SELECT * FROM services WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getServicesListByBusinessId(businessId: String): List<Service>

    // ✅ FIX: correct parameter name to match query placeholder
    @Query("DELETE FROM services WHERE businessId = :businessId AND id = :serviceId")
    suspend fun delete(businessId: String, serviceId: String)
}
