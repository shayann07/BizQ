package com.example.finalproject.ui.business_profile

import android.net.Uri
import androidx.lifecycle.*
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.models.Business
import com.example.finalproject.data.models.Service
import com.example.finalproject.data.repository.AvailabilityRepository
import com.example.finalproject.data.repository.BusinessRepository
import com.example.finalproject.data.repository.ServiceRepository
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class BusinessProfileViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    private val serviceRepository: ServiceRepository,
    private val availabilityRepository: AvailabilityRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _currentUserUid = MutableLiveData<String>()

    /** Observe Room (updates after we cache Firestore results). */
    val business: LiveData<Business?> = _currentUserUid.switchMap { uid ->
        if (uid.isNullOrBlank()) MutableLiveData(null) else businessRepository.observeByOwner(uid)
    }
    val services: LiveData<List<Service>> = business.switchMap { biz ->
        if (biz == null) MutableLiveData(emptyList())
        else serviceRepository.observeServices(biz.id) // id == uid
    }
    val workingHours: LiveData<List<Availability>> = business.switchMap { biz ->
        if (biz == null) MutableLiveData(emptyList())
        else availabilityRepository.observeWeek(biz.id) // id == uid
    }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val storage by lazy { FirebaseStorage.getInstance() }

    // Prevent duplicate warm-ups running at the same time,
    // but allow another attempt later if the first actually found nothing.
    @Volatile private var warmInFlight = false
    @Volatile private var warmDoneOnce = false

    /**
     * MUST be called with the logged-in Firebase UID.
     * 1) Begin observing Room immediately.
     * 2) Warm Room from Firestore on cold start (business + services + availability).
     * 3) If cloud has nothing, surface a helpful error.
     */
    fun loadBusinessData(userUid: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }
        if (userUid != uid) { _error.value = "UID mismatch"; return }

        if (_currentUserUid.value != uid) _currentUserUid.value = uid
        warmFromCloudIfNeeded(uid)
    }

    private fun warmFromCloudIfNeeded(uid: String) {
        if (warmInFlight || warmDoneOnce) return
        warmInFlight = true

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1) Business (required anchor)
                val biz = businessRepository.getOrFetchByOwner(uid)
                if (biz == null) {
                    _error.value = "No business found for this account. Please finish onboarding."
                    return@launch
                }

                // 2) Services
                // Accept either Resource-returning or List-returning repo implementations.
                runCatching {
                    when (val res = serviceRepository.getOrFetchServices(uid)) {
                        is Resource.Success<*> -> Unit
                        is Resource.Error<*> -> _error.postValue( "Failed to load services")
                        else                -> Unit
                    }
                }.onFailure { t ->
                    _error.postValue(t.message ?: "Failed to load services")
                }

                // 3) Availability
                runCatching {
                    // Prefer explicit "getOrFetchWeek" to guarantee Firestore→Room on cold start
                    availabilityRepository.getOrFetchWeek(uid)
                }.onFailure { t ->
                    // If the interface only has getWeek() returning Resource, this still reports errors
                    _error.postValue(t.message ?: "Failed to load availability")
                }

                // If we got here, we attempted a full warm
                warmDoneOnce = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load profile"
            } finally {
                warmInFlight = false
                _isLoading.value = false
            }
        }
    }

    fun deleteService(service: Service) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = serviceRepository.deleteService(service.businessId, service.id)) {
                is Resource.Success -> _successMessage.value = "השירות \"${service.name}\" נמחק"
                is Resource.Error   -> _error.value = result.message
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun updateBusinessDescription(newDescription: String) {
        val current = business.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val updated = current.copy(description = newDescription, updatedAt = System.currentTimeMillis())
            when (val result = businessRepository.updateBusiness(updated)) {
                is Resource.Success -> _successMessage.value = "תיאור העסק עודכן"
                is Resource.Error   -> _error.value = result.message
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun updateBusinessVisibility(isPublic: Boolean) {
        val current = business.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val updated = current.copy(isPublic = isPublic, updatedAt = System.currentTimeMillis())
            when (val result = businessRepository.updateBusiness(updated)) {
                is Resource.Success -> _successMessage.value = if (isPublic) "העסק פורסם" else "העסק הוסתר"
                is Resource.Error   -> _error.value = result.message
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun uploadLogo(localUri: Uri) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ref = storage.reference
                    .child("users").child(uid)
                    .child("business").child("main")
                    .child("logo.jpg")

                ref.putFile(localUri).await()
                val url = ref.downloadUrl.await().toString()

                val current = business.value ?: Business(
                    id = uid, ownerUid = uid,
                    name = "", phone = "",
                    address = null, industry = "",
                    logoUrl = null, description = null,
                    isPublic = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val updated = current.copy(logoUrl = url, updatedAt = System.currentTimeMillis())
                when (val res = businessRepository.updateBusiness(updated)) {
                    is Resource.Success -> _successMessage.value = "הלוגו עודכן בהצלחה"
                    is Resource.Error   -> _error.value = res.message
                    is Resource.Loading -> Unit
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to upload logo"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getCurrentBusinessId(): String? = business.value?.id

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}
