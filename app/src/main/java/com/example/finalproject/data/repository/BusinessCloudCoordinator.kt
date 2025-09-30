package com.example.finalproject.data.repository

import android.net.Uri
import com.example.finalproject.data.models.Business
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single place to:
 * 1) Rehydrate local Room caches from Firestore after fresh install/login (Business + Services + Availability).
 * 2) Upload a logo to Firebase Storage and write the download URL to Firestore, then mirror to Room.
 * 3) Update lightweight branding fields (instagram/about) with a merge write + Room mirror.
 *
 * IMPORTANT:
 * - Only uses FirebaseAuth UID as the business id (one business per user).
 * - No SharedPreferences or device-generated IDs.
 */
@Singleton
class BusinessCloudCoordinator @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val businessRepo: BusinessRepository,
    private val serviceRepo: ServiceRepository,
    private val availabilityRepo: AvailabilityRepository
) {

    /**
     * Warm (hydrate) all Room caches from Firestore if Room is empty.
     * Safe to call repeatedly; fast no-op when already hydrated.
     */
    suspend fun warmAllFromCloud(): Resource<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return@withContext Resource.Error("No logged-in user")
        try {
            // 1) Business
            businessRepo.getOrFetchByOwner(uid)
            // 2) Services
            serviceRepo.getOrFetchServices(uid)
            // 3) Availability
            availabilityRepo.getWeek(uid)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to bootstrap from cloud")
        }
    }

    /**
     * Upload a logo file to Firebase Storage at users/{uid}/branding/logo.jpg,
     * then write the download URL to Firestore (users/{uid}/business/main.logoUrl)
     * and mirror to Room via BusinessRepository.updateBusiness(...).
     *
     * Returns the public download URL on success.
     */
    suspend fun uploadLogoAndSave(localLogoUri: Uri): Resource<String> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return@withContext Resource.Error("No logged-in user")

        try {
            // --- Upload to Storage ---
            val ref = storage.reference.child("users/$uid/branding/logo.jpg")
            ref.putFile(localLogoUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            // --- Merge Firestore business doc ---
            val now = System.currentTimeMillis()
            val businessDoc = firestore.collection("users")
                .document(uid)
                .collection("business")
                .document("main")

            // Ensure the document exists and has id==uid/ownerUid==uid
            businessDoc.set(
                mapOf(
                    "id" to uid,
                    "ownerUid" to uid,
                    "logoUrl" to downloadUrl,
                    "updatedAt" to now
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            // --- Mirror into Room ---
            val current = businessRepo.getOrFetchByOwner(uid)
            val toSave = (current ?: Business(
                id = uid,
                ownerUid = uid,
                name = "",
                phone = "",
                address = null,
                industry = "",
                logoUrl = downloadUrl,
                description = null,
                isPublic = false,
                createdAt = now,
                updatedAt = now
            )).copy(
                logoUrl = downloadUrl,
                updatedAt = now,
                id = uid,
                ownerUid = uid
            )

            // Uses repo's Room+Firestore merge update (idempotent)
            businessRepo.updateBusiness(toSave)

            Resource.Success(downloadUrl)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to upload logo")
        }
    }

    /**
     * Update instagram/about fields and mirror to Room.
     * Call this from Branding or Profile screens when user edits text fields.
     */
    suspend fun updateBrandingFields(instagramLink: String?, aboutText: String?): Resource<Unit> =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid.orEmpty()
            if (uid.isBlank()) return@withContext Resource.Error("No logged-in user")

            try {
                val now = System.currentTimeMillis()
                val businessDoc = firestore.collection("users")
                    .document(uid)
                    .collection("business")
                    .document("main")

                val payload = mutableMapOf<String, Any>(
                    "id" to uid,
                    "ownerUid" to uid,
                    "updatedAt" to now
                )
                instagramLink?.let { payload["instagramLink"] = it }
                aboutText?.let { payload["description"] = it }

                businessDoc.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()

                // Mirror into Room
                val current = businessRepo.getOrFetchByOwner(uid)
                val toSave = (current ?: Business(
                    id = uid,
                    ownerUid = uid,
                    name = "",
                    phone = "",
                    address = null,
                    industry = "",
                    logoUrl = current?.logoUrl, // keep existing if any
                    description = null,
                    isPublic = false,
                    createdAt = now,
                    updatedAt = now
                )).copy(
                    description = aboutText ?: current?.description,
                    updatedAt = now
                ).let { b ->
                    // Inject instagramLink into description/links style if you store separately later
                    b
                }

                businessRepo.updateBusiness(toSave)
                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Failed to update branding fields")
            }
        }
}
