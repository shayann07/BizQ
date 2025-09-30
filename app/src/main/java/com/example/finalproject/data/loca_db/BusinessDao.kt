package com.example.finalproject.data.loca_db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.finalproject.data.models.Business

@Dao
interface BusinessDao {

    @Query("SELECT * FROM business ORDER BY name ASC")
    fun getAllBusinesses(): LiveData<List<Business>>

    @Query("SELECT * FROM business WHERE id = :businessId")
    suspend fun getBusinessById(businessId: String): Business?

    @Query("SELECT * FROM business WHERE id = :businessId")
    fun getBusinessByIdLiveData(businessId: String): LiveData<Business?>

    @Query("SELECT * FROM business WHERE ownerUid = :ownerUid")
    fun getBusinessesByOwner(ownerUid: String): LiveData<List<Business>>

    // הוספה: פונקציה לקבלת עסק יחיד של בעל עסק
    @Query("SELECT * FROM business WHERE ownerUid = :ownerUid LIMIT 1")
    fun observeByOwner(ownerUid: String): LiveData<Business?>

    @Query("SELECT * FROM business WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun getBusinessByOwnerSuspend(ownerUid: String): Business?

    @Query("SELECT * FROM business WHERE industry = :industry AND isPublic = 1")
    fun getBusinessesByIndustry(industry: String): LiveData<List<Business>>

    @Query("SELECT * FROM business WHERE isPublic = 1 ORDER BY name ASC")
    fun getPublicBusinesses(): LiveData<List<Business>>

    @Query("SELECT DISTINCT industry FROM business WHERE isPublic = 1 ORDER BY industry ASC")
    fun getAllIndustries(): LiveData<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(business: Business)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(business: Business)

    @Update
    suspend fun update(business: Business)

    @Delete
    suspend fun delete(business: Business)

    @Query("DELETE FROM business WHERE id = :businessId")
    suspend fun deleteById(businessId: String)
}
