// app/src/main/java/com/example/finalproject/data/repository/FirebaseImpl/ServicesRepositoryFirebase.kt
package com.example.finalproject.data.repository.FirebaseImpl

import androidx.lifecycle.LiveData
import com.example.finalproject.data.loca_db.ServiceDao
import com.example.finalproject.data.models.Service
import com.example.finalproject.data.repository.ServiceRepository
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.safeCall
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicesRepositoryFirebase @Inject constructor(
    private val serviceDao: ServiceDao,
    private val firestore: FirebaseFirestore
) : ServiceRepository {

    private fun servicesColl(businessId: String) =
        firestore.collection("businesses").document(businessId).collection("services")

    override fun observeServices(businessId: String): LiveData<List<Service>> {
        // Room-backed source for UI
        return serviceDao.observeServices(businessId)
    }

    override suspend fun getOrFetchServices(businessId: String): List<Service> {
        // Room first
        val local = serviceDao.getServicesListByBusinessId(businessId)
        if (local.isNotEmpty()) return local

        // Firestore → cache → return
        val snap = servicesColl(businessId).get().await()
        val remote = snap.documents.mapNotNull { d ->
            d.toObject(Service::class.java)?.copy(
                id = d.id,                 // force Firestore doc id
                businessId = businessId    // force path id
            )
        }
        if (remote.isNotEmpty()) serviceDao.upsertAll(remote)
        return remote
    }

    override suspend fun createService(service: Service): Resource<Unit> = safeCall {
        require(service.businessId.isNotBlank()) { "businessId is blank" }
        // Let Firestore assign a stable id when missing
        val id = if (service.id.isBlank()) {
            servicesColl(service.businessId).document().id
        } else service.id

        val final = service.copy(id = id)
        servicesColl(service.businessId).document(id).set(final).await()
        serviceDao.upsert(final) // mirror locally
        Resource.Success(Unit)
    }

    override suspend fun updateService(service: Service): Resource<Unit> = safeCall {
        require(service.businessId.isNotBlank()) { "businessId is blank" }
        require(service.id.isNotBlank()) { "service id is blank" }

        servicesColl(service.businessId).document(service.id).set(service).await()
        serviceDao.upsert(service) // mirror locally
        Resource.Success(Unit)
    }

    override suspend fun deleteService(businessId: String, serviceId: String): Resource<Unit> = safeCall {
        require(businessId.isNotBlank()) { "businessId is blank" }
        require(serviceId.isNotBlank()) { "serviceId is blank" }

        servicesColl(businessId).document(serviceId).delete().await()
        serviceDao.delete(businessId, serviceId) // mirror locally
        Resource.Success(Unit)
    }
}
