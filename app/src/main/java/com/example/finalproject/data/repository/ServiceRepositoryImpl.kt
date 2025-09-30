package com.example.finalproject.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.finalproject.data.loca_db.ServiceDao
import com.example.finalproject.data.models.Service
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRepositoryImpl @Inject constructor(
    private val dao: ServiceDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ServiceRepository {

    /** Always derive the business identity from the logged-in Firebase user. */
    private fun requireUid(expect: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("No logged-in user (uid is null)")
        if (!expect.isNullOrBlank() && expect != uid) {
            throw IllegalArgumentException("businessId must equal Firebase UID. expected=$expect actual=$uid")
        }
        return uid
    }

    /** Firestore collection path: users/{uid}/business/main/services */
    private fun servicesColl(uid: String) =
        firestore.collection("users")
            .document(uid)
            .collection("business")
            .document("main")
            .collection("services")

    // --------------------------------------------------------------------
    // Reads
    // --------------------------------------------------------------------

    override fun observeServices(businessId: String): LiveData<List<Service>> {
        val uid = try { requireUid(businessId) } catch (_: Throwable) {
            return MutableLiveData(emptyList())
        }
        return dao.observeServices(uid)
    }

    /** Room-first; if empty → Firestore → cache → return. */
    override suspend fun getOrFetchServices(businessId: String): List<Service> {
        val uid = requireUid(businessId)

        val local = dao.getServicesListByBusinessId(uid)
        if (local.isNotEmpty()) return local

        val snap = servicesColl(uid).get().await()
        val remote = snap.documents.mapNotNull { d ->
            d.toObject(Service::class.java)?.copy(
                id = d.id,
                businessId = uid // enforce correct owner
            )
        }
        if (remote.isNotEmpty()) dao.upsertAll(remote)
        return remote
    }

    // --------------------------------------------------------------------
    // Writes (Firestore first, then mirror to Room)
    // --------------------------------------------------------------------

    override suspend fun createService(service: Service): Resource<Unit> = try {
        val uid = requireUid(service.businessId)
        val col = servicesColl(uid)
        val id = if (service.id.isBlank()) col.document().id else service.id
        val final = service.copy(id = id, businessId = uid)

        col.document(id).set(final).await()
        dao.upsert(final)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Failed to add service")
    }

    override suspend fun updateService(service: Service): Resource<Unit> = try {
        val uid = requireUid(service.businessId)
        require(service.id.isNotBlank()) { "service id is blank" }

        val final = service.copy(businessId = uid)
        servicesColl(uid).document(final.id).set(final).await()
        dao.upsert(final)
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Failed to update service")
    }

    override suspend fun deleteService(businessId: String, serviceId: String): Resource<Unit> = try {
        val uid = requireUid(businessId)
        require(serviceId.isNotBlank()) { "serviceId is blank" }

        servicesColl(uid).document(serviceId).delete().await()
        dao.delete(uid, serviceId) // mirror locally
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.message ?: "Failed to delete service")
    }
}
