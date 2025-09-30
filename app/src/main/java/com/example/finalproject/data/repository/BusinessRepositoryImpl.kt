// app/src/main/java/com/example/finalproject/data/repository/BusinessRepositoryImpl.kt
package com.example.finalproject.data.repository

import com.example.finalproject.data.loca_db.BusinessDao
import com.example.finalproject.data.models.Business
import com.example.finalproject.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val businessDao: BusinessDao
) : BusinessRepository {

    /** Firestore path: users/{uid}/business/main */
    private fun businessDoc(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("business")
            .document("main")

    override suspend fun createBusiness(business: Business): Resource<Unit> {
        return try {
            val now = System.currentTimeMillis()
            // Enforce stable key: business.id == ownerUid
            val uid = business.ownerUid
            val normalized = business.copy(
                id = uid,
                createdAt = if (business.createdAt == 0L) now else business.createdAt,
                updatedAt = now
            )

            // Remote
            businessDoc(uid).set(normalized, SetOptions.merge()).await()
            // Local cache
            businessDao.upsert(normalized)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save business")
        }
    }

    override suspend fun updateBusiness(business: Business): Resource<Unit> {
        return try {
            val now = System.currentTimeMillis()
            // Enforce id == ownerUid on update too
            val uid = business.ownerUid
            val normalized = business.copy(
                id = uid,
                updatedAt = now
            )

            businessDoc(uid).set(normalized, SetOptions.merge()).await()
            businessDao.upsert(normalized)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update business")
        }
    }

    override suspend fun getOrFetchByOwner(ownerUid: String): Business? {
        // Room first
        businessDao.getBusinessByOwnerSuspend(ownerUid)?.let { return it }

        // Firestore → cache → return
        return try {
            val snap = businessDoc(ownerUid).get().await()
            val remote = snap.toObject(Business::class.java)
            if (remote != null) {
                // make sure local row has correct id==uid
                val normalized = remote.copy(id = ownerUid)
                businessDao.upsert(normalized)
                normalized
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun observeByOwner(ownerUid: String) =
        businessDao.observeByOwner(ownerUid)

    override suspend fun upsertIndustry(
        ownerUid: String,
        businessId: String, // ignored; we always use ownerUid as the id
        industry: String
    ): Resource<Unit> {
        return try {
            val now = System.currentTimeMillis()

            // Update Firestore field(s) (merge)
            businessDoc(ownerUid).set(
                mapOf(
                    "id" to ownerUid,           // keep id aligned
                    "ownerUid" to ownerUid,
                    "industry" to industry,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            ).await()

            // Update/seed local cache
            val local = businessDao.getBusinessByOwnerSuspend(ownerUid)
            val toSave = if (local == null) {
                Business(
                    id = ownerUid,
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
                local.copy(industry = industry, updatedAt = now, id = ownerUid)
            }
            businessDao.upsert(toSave)

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save business type")
        }
    }
}
