// app/src/main/java/com/example/finalproject/data/repository/FirebaseImpl/BusinessRepositoryFirebase.kt
package com.example.finalproject.data.repository.FirebaseImpl

import androidx.lifecycle.LiveData
import com.example.finalproject.data.loca_db.BusinessDao
import com.example.finalproject.data.models.Business
import com.example.finalproject.data.repository.BusinessRepository
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.safeCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepositoryFirebase @Inject constructor(
    private val businessDao: BusinessDao
) : BusinessRepository {

    override suspend fun createBusiness(business: Business): Resource<Unit> = safeCall {
        businessDao.upsert(business)
        Resource.Success(Unit)
    }

    override suspend fun updateBusiness(business: Business): Resource<Unit> = safeCall {
        businessDao.upsert(business)
        Resource.Success(Unit)
    }

    /** Room-only in this impl; a different impl can hit Firestore if needed. */
    override suspend fun getOrFetchByOwner(ownerUid: String): Business? {
        return businessDao.getBusinessByOwnerSuspend(ownerUid)
    }

    /** IMPORTANT: name matches the interface AND your DAO (observeByOwner). */
    override fun observeByOwner(ownerUid: String): LiveData<Business?> {
        return businessDao.observeByOwner(ownerUid)
    }

    override suspend fun upsertIndustry(
        ownerUid: String,
        businessId: String,
        industry: String
    ): Resource<Unit> = safeCall {
        val now = System.currentTimeMillis()
        val local = businessDao.getBusinessByOwnerSuspend(ownerUid)
        val target = if (local == null) {
            Business(
                id = businessId,
                ownerUid = ownerUid,
                name = "",
                phone = "",
                address = null,
                industry = industry,
                logoUrl = null,
                description = null,
                isPublic = false,
                createdAt = now,
                updatedAt = now
            )
        } else {
            local.copy(industry = industry, updatedAt = now)
        }
        businessDao.upsert(target)
        Resource.Success(Unit)
    }
}
