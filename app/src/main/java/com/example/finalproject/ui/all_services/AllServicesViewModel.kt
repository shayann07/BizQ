package com.example.finalproject.ui.all_services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.finalproject.data.models.Service
import com.example.finalproject.data.repository.ServiceRepository
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllServicesViewModel @Inject constructor(
    private val repo: ServiceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _businessId = MutableLiveData<String?>()

    /** Room-backed list; repo hydrates from Firestore if Room empty (via bootstrap). */
    val services: LiveData<List<Service>> = _businessId.switchMap { id ->
        if (id.isNullOrBlank()) MutableLiveData(emptyList())
        else repo.observeServices(id)
    }

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Must equal Firebase UID. */
    fun setBusinessId(id: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }
        if (id != uid) { _error.value = "businessId must equal Firebase UID"; return }
        if (_businessId.value == id) return
        _businessId.value = id
    }

    /** Cold-start / reinstall hydration: Firestore → Room → observeServices() updates UI. */
    fun bootstrap() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }
        if (_businessId.value != uid) { _error.value = "businessId mismatch"; return }
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repo.getOrFetchServices(uid) // triggers Room upsert if Firestore has data
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load services"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Create service (doc id assigned if blank), mirrored Firestore→Room. */
    fun addService(name: String, duration: Int, price: Int, description: String? = null) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }

        viewModelScope.launch {
            _isLoading.value = true
            val service = Service(
                id = "",
                businessId = uid,
                name = name.trim(),
                durationMinutes = duration,
                priceShekels = price,
                description = description?.trim(),
                isActive = true
            )
            when (val result = repo.createService(service)) {
                is Resource.Success -> _successMessage.value = "השירות '${service.name}' נוסף"
                is Resource.Error   -> _error.value = result.message ?: "Failed to add service"
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun updateService(service: Service) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }
        if (service.businessId != uid || service.id.isBlank()) {
            _error.value = "Invalid service ids"; return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repo.updateService(service)) {
                is Resource.Success -> _successMessage.value = "השירות '${service.name}' עודכן"
                is Resource.Error   -> _error.value = result.message ?: "Failed to update service"
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun deleteService(service: Service) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }
        if (service.businessId != uid || service.id.isBlank()) {
            _error.value = "Invalid service ids"; return
        }
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repo.deleteService(uid, service.id)) {
                is Resource.Success -> _successMessage.value = "השירות '${service.name}' נמחק"
                is Resource.Error   -> _error.value = result.message ?: "Failed to delete service"
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    fun validateService(name: String, duration: String, price: String): ValidationResult {
        if (name.isBlank()) return ValidationResult.Error("שם השירות לא יכול להיות ריק")
        val d = duration.toIntOrNull() ?: return ValidationResult.Error("משך השירות חייב להיות מספר חיובי")
        if (d <= 0) return ValidationResult.Error("משך השירות חייב להיות מספר חיובי")
        val p = price.toIntOrNull() ?: return ValidationResult.Error("מחיר השירות חייב להיות מספר חיובי או אפס")
        if (p < 0) return ValidationResult.Error("מחיר השירות חייב להיות מספר חיובי או אפס")
        return ValidationResult.Success
    }

    fun clearMessages() {
        _error.value = null
        _successMessage.value = null
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
