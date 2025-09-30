// ServiceRepository.kt
package com.example.finalproject.data.repository

import androidx.lifecycle.LiveData
import com.example.finalproject.data.models.Service
import com.example.finalproject.utils.Resource

interface ServiceRepository {
    fun observeServices(businessId: String): LiveData<List<Service>>
    suspend fun getOrFetchServices(businessId: String): List<Service>
    suspend fun createService(service: Service): Resource<Unit>
    suspend fun updateService(service: Service): Resource<Unit>
    suspend fun deleteService(businessId: String, serviceId: String): Resource<Unit>
}
