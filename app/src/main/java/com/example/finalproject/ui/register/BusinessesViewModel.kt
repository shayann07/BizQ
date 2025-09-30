// app/src/main/java/com/example/finalproject/ui/register/BusinessesViewModel.kt
package com.example.finalproject.ui.register

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject.data.loca_db.BusinessDao
import com.example.finalproject.data.loca_db.ServiceDao
import com.example.finalproject.data.local.dao.AvailabilityDao
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.models.Business
import com.example.finalproject.data.models.Service
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BusinessesViewModel @Inject constructor(
    private val businessDao: BusinessDao,
    private val serviceDao: ServiceDao,
    private val availabilityDao: AvailabilityDao
) : ViewModel() {

    fun observeMyBusinesses(ownerUid: String): LiveData<Business?> =
        businessDao.observeByOwner(ownerUid)

    fun createOrUpdateBusiness(ownerUid: String, draft: Business) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = draft.id.ifBlank { UUID.randomUUID().toString() }
        businessDao.upsert(
            draft.copy(
                id = id,
                ownerUid = ownerUid,
                createdAt = if (draft.createdAt == 0L) now else draft.createdAt,
                updatedAt = now
            )
        )
    }

    // 🔧 Use the DAO method that actually exists
    fun observeServices(businessId: String): LiveData<List<Service>> =
        serviceDao.observeServices(businessId)

    fun addService(businessId: String, s: Service) = viewModelScope.launch {
        serviceDao.upsert(s.copy(businessId = businessId))
    }

    fun removeService(businessId: String, serviceId: String) = viewModelScope.launch {
        serviceDao.delete(businessId, serviceId)
    }

    fun observeWeek(businessId: String): LiveData<List<Availability>> =
        availabilityDao.observeWeek(businessId)

    fun saveWeek(businessId: String, dayToRange: Map<Int, Pair<Int, Int>>) = viewModelScope.launch {
        val rows = dayToRange.map { (day, range) ->
            Availability(
                businessId = businessId,
                dayOfWeek = day,
                isOpen = true,
                startMinutes = range.first,
                endMinutes = range.second
            )
        }
        availabilityDao.upsertAll(rows)
    }
}
