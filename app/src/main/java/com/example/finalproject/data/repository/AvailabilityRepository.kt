// data/repository/AvailabilityRepository.kt
package com.example.finalproject.data.repository

import androidx.lifecycle.LiveData
import com.example.finalproject.data.models.Availability
import com.example.finalproject.utils.Resource

interface AvailabilityRepository {
    fun observeWeek(businessId: String): LiveData<List<Availability>>
    suspend fun getWeek(businessId: String): Resource<List<Availability>>
    suspend fun updateWeek(businessId: String, week: List<Availability>): Resource<Unit>
    suspend fun updateDay(availability: Availability): Resource<Unit>
    // add this:
    suspend fun getOrFetchWeek(businessId: String): List<Availability>
}
