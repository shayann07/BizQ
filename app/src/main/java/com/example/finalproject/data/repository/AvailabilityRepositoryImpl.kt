package com.example.finalproject.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.finalproject.data.local.dao.AvailabilityDao
import com.example.finalproject.data.models.Availability
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvailabilityRepositoryImpl @Inject constructor(
    private val dao: AvailabilityDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : AvailabilityRepository {

    /** Always derive business identity from the logged-in Firebase user. */
    private fun requireUid(expect: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No logged-in user (uid is null)")
        if (!expect.isNullOrBlank() && expect != uid) {
            throw IllegalArgumentException("businessId must equal Firebase UID. expected=$expect actual=$uid")
        }
        return uid
    }

    /** Primary path (current): users/{uid}/business/main/availability/{0..6} */
    private fun collPrimary(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("business")
            .document("main")
            .collection("availability")

    /** Legacy path (fallback): users/{uid}/availability/{0..6} */
    private fun collLegacy(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("availability")

    // ---------------------- Observe ----------------------

    override fun observeWeek(businessId: String): LiveData<List<Availability>> {
        val uid = try { requireUid(businessId) } catch (_: Throwable) {
            return MutableLiveData(emptyList())
        }
        return dao.observeWeek(uid)
    }

    // ---------------------- Read (Room → Firestore → Room) ----------------------

    /**
     * Preferred use from VM: getOrFetchWeek(businessId)
     * This returns a plain List by unwrapping Resource.
     */
    override suspend fun getOrFetchWeek(businessId: String): List<Availability> {
        return when (val res = getWeek(businessId)) {
            is Resource.Success -> res.data ?: emptyList()
            else -> emptyList()
        }
    }

    override suspend fun getWeek(businessId: String): Resource<List<Availability>> {
        return try {
            val uid = requireUid(businessId)

            // 1) Room first
            val local = dao.getWeek(uid)
            if (local.isNotEmpty()) return Resource.Success(local)

            // 2) Firestore primary, else legacy, then cache to Room
            val remote = fetchWeekFromFirestore(uid)
            if (remote.isNotEmpty()) {
                dao.upsertAll(remote)
            }
            Resource.Success(remote)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load availability")
        }
    }

    /**
     * Reads from primary path; if empty, falls back to legacy.
     * Handles both numeric and non-numeric doc IDs and/or a stored 'dayOfWeek' field.
     */
    private suspend fun fetchWeekFromFirestore(uid: String): List<Availability> {
        // Try primary path
        val primary = runCatching {
            collPrimary(uid).get().await().documents.mapNotNull { doc ->
                mapDocToAvailability(uid, doc.id, doc.toObject(Availability::class.java), doc.data)
            }
        }.getOrElse { emptyList() }

        if (primary.isNotEmpty()) return primary.sortedBy { it.dayOfWeek }

        // Fallback to legacy path
        val legacy = runCatching {
            collLegacy(uid).get().await().documents.mapNotNull { doc ->
                mapDocToAvailability(uid, doc.id, doc.toObject(Availability::class.java), doc.data)
            }
        }.getOrElse { emptyList() }

        return legacy.sortedBy { it.dayOfWeek }
    }

    /**
     * Robust mapper:
     * - If the doc has a valid Availability, use it and ensure businessId & dayOfWeek are set.
     * - If dayOfWeek missing in object, try to parse from docId or from raw map.
     * - If object is null, try constructing from raw map + docId.
     */
    private fun mapDocToAvailability(
        uid: String,
        docId: String,
        obj: Availability?,
        raw: Map<String, Any?>?
    ): Availability? {
        // Try to extract a day from the object or the id/map
        val idDay = docId.toIntOrNull()
        val mapDay = (raw?.get("dayOfWeek") as? Number)?.toInt()
        val resolvedDay = when {
            obj?.dayOfWeek in 0..6 -> obj!!.dayOfWeek
            idDay in 0..6 -> idDay
            mapDay in 0..6 -> mapDay
            else -> null
        } ?: return null

        if (obj != null) {
            return obj.copy(
                businessId = uid,
                dayOfWeek = resolvedDay
            )
        }

        // Build from map if needed
        val isOpen = (raw?.get("isOpen") as? Boolean) ?: false
        val startMin = ((raw?.get("startMinutes") as? Number)?.toInt()) ?: 9 * 60
        val endMin = ((raw?.get("endMinutes") as? Number)?.toInt()) ?: 18 * 60

        return Availability(
            businessId = uid,
            dayOfWeek = resolvedDay,
            isOpen = isOpen,
            startMinutes = startMin,
            endMinutes = endMin
        )
    }

    // ---------------------- Write (Firestore → Room) ----------------------

    override suspend fun updateWeek(businessId: String, week: List<Availability>): Resource<Unit> {
        return try {
            val uid = requireUid(businessId)

            val batch = firestore.batch()
            week.forEach { a ->
                val id = a.dayOfWeek.toString()
                batch.set(collPrimary(uid).document(id), a.copy(businessId = uid))
            }
            batch.commit().await()

            // Replace local cache
            dao.deleteWeek(uid)
            if (week.isNotEmpty()) dao.upsertAll(week.map { it.copy(businessId = uid) })
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to save availability")
        }
    }

    override suspend fun updateDay(availability: Availability): Resource<Unit> {
        return try {
            val uid = requireUid(availability.businessId)
            val id = availability.dayOfWeek.toString()
            collPrimary(uid).document(id).set(availability.copy(businessId = uid)).await()
            dao.upsert(availability.copy(businessId = uid))
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update day")
        }
    }
}
