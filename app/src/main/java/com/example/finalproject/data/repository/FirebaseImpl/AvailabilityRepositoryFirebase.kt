package com.example.finalproject.data.repository.FirebaseImpl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.finalproject.data.local.dao.AvailabilityDao
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.repository.AvailabilityRepository
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.safeCall
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvailabilityRepositoryFirebase @Inject constructor(
    private val availabilityDao: AvailabilityDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : AvailabilityRepository {

    // ------- UID discipline -------
    private fun requireUid(expect: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No logged-in user (uid is null)")
        if (!expect.isNullOrBlank() && expect != uid) {
            throw IllegalArgumentException("businessId must equal Firebase UID. expected=$expect actual=$uid")
        }
        return uid
    }

    // Firestore paths
    private fun collPrimary(uid: String): CollectionReference =
        firestore.collection("users")
            .document(uid)
            .collection("business")
            .document("main")
            .collection("availability")

    private fun collLegacy(uid: String): CollectionReference =
        firestore.collection("users")
            .document(uid)
            .collection("availability")

    // ------- Observe -------
    override fun observeWeek(businessId: String): LiveData<List<Availability>> {
        val uid = try { requireUid(businessId) } catch (_: Throwable) {
            return MutableLiveData(emptyList())
        }
        return availabilityDao.observeWeek(uid)
    }

    // ------- Read (Room → Firestore → Room) -------

    /**
     * Convenience: returns a plain list for quick priming from ViewModels.
     * Room first; if empty → Firestore primary; if still empty → legacy; cache to Room.
     */
    override suspend fun getOrFetchWeek(businessId: String): List<Availability> {
        val uid = requireUid(businessId)

        // Room first
        val local = availabilityDao.getWeek(uid)
        if (local.isNotEmpty()) return local

        // Firestore fallback(s)
        val remote = fetchWeekFromFirestore(uid)
        if (remote.isNotEmpty()) availabilityDao.upsertAll(remote)
        return remote
    }

    override suspend fun getWeek(businessId: String): Resource<List<Availability>> = safeCall {
        val uid = requireUid(businessId)

        val local = availabilityDao.getWeek(uid)
        if (local.isNotEmpty()) return@safeCall Resource.Success(local)

        val remote = fetchWeekFromFirestore(uid)
        if (remote.isNotEmpty()) availabilityDao.upsertAll(remote)
        Resource.Success(remote)
    }

    private suspend fun fetchWeekFromFirestore(uid: String): List<Availability> {
        // Try primary path first
        val primary = runCatching {
            collPrimary(uid).get().await().documents.mapNotNull { doc ->
                mapDocToAvailability(uid, doc.id, doc.toObject(Availability::class.java), doc.data)
            }
        }.getOrElse { emptyList() }
        if (primary.isNotEmpty()) return primary.sortedBy { it.dayOfWeek }

        // Fallback: legacy path
        val legacy = runCatching {
            collLegacy(uid).get().await().documents.mapNotNull { doc ->
                mapDocToAvailability(uid, doc.id, doc.toObject(Availability::class.java), doc.data)
            }
        }.getOrElse { emptyList() }

        return legacy.sortedBy { it.dayOfWeek }
    }

    /**
     * Robust mapper for various schemas:
     * - Accepts numeric/non-numeric doc IDs.
     * - If 'dayOfWeek' exists in the object or raw map, uses it.
     * - Ensures businessId is always set to the logged-in uid.
     */
    private fun mapDocToAvailability(
        uid: String,
        docId: String,
        obj: Availability?,
        raw: Map<String, Any?>?
    ): Availability? {
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

    // ------- Write (Firestore → Room) -------

    override suspend fun updateDay(availability: Availability): Resource<Unit> = safeCall {
        val uid = requireUid(availability.businessId)
        val id = availability.dayOfWeek.toString()

        // Write to Firestore
        collPrimary(uid).document(id).set(availability.copy(businessId = uid)).await()

        // Mirror to Room
        availabilityDao.upsert(availability.copy(businessId = uid))
        Resource.Success(Unit)
    }

    override suspend fun updateWeek(businessId: String, week: List<Availability>): Resource<Unit> = safeCall {
        val uid = requireUid(businessId)

        // Firestore batch
        val batch = firestore.batch()
        week.forEach { a ->
            val id = a.dayOfWeek.toString()
            batch.set(collPrimary(uid).document(id), a.copy(businessId = uid))
        }
        batch.commit().await()

        // Replace local cache
        availabilityDao.deleteWeek(uid)
        if (week.isNotEmpty()) availabilityDao.upsertAll(week.map { it.copy(businessId = uid) })
        Resource.Success(Unit)
    }
}
