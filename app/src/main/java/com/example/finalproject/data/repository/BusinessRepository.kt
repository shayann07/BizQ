// app/src/main/java/com/example/finalproject/data/repository/BusinessRepository.kt
package com.example.finalproject.data.repository

import androidx.lifecycle.LiveData
import com.example.finalproject.data.models.Business
import com.example.finalproject.utils.Resource

interface BusinessRepository {

    /** Create or overwrite the business and cache locally. */
    suspend fun createBusiness(business: Business): Resource<Unit>

    /** Update the business and cache locally. */
    suspend fun updateBusiness(business: Business): Resource<Unit>

    /** Room-first; caller may fall back to Firestore in another impl. */
    suspend fun getOrFetchByOwner(ownerUid: String): Business?

    /** Observe the single business owned by this user (Room-backed). */
    fun observeByOwner(ownerUid: String): LiveData<Business?>

    /** Merge/update only industry and cache locally. */
    suspend fun upsertIndustry(ownerUid: String, businessId: String, industry: String): Resource<Unit>



}
