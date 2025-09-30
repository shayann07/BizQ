package com.example.finalproject.ui.availability

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.repository.AvailabilityRepository
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val repo: AvailabilityRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _businessId = MutableLiveData<String>()
    val businessId: LiveData<String> = _businessId

    /** Room-backed stream (repo mirrors Firestore → Room on demand). */
    val week: LiveData<List<Availability>> = _businessId.switchMap { id ->
        if (id.isBlank()) MutableLiveData(emptyList())
        else repo.observeWeek(id)
    }

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _success = MutableLiveData<String?>()
    val success: LiveData<String?> = _success

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Single source of truth = Firebase UID. No fetch here (bootstrap does that). */
    fun setBusinessId(id: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            _error.value = "Not authenticated"
            return
        }
        if (id != uid) {
            _error.value = "businessId must equal Firebase UID"
            return
        }
        if (_businessId.value == id) return
        _businessId.value = id
    }

    /** Explicit bootstrap for cold start / reinstall flows (called by Fragment). */
    fun bootstrap() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            _error.value = "Not authenticated"
            return
        }
        if (_businessId.value != uid) {
            _error.value = "businessId mismatch"
            return
        }
        viewModelScope.launch {
            when (val res = repo.getWeek(uid)) {
                is Resource.Error -> _error.value = res.message
                else -> Unit // Success/Loading: Room observer delivers data
            }
        }
    }

    /** Fire-and-forget save (optional). */
    fun saveWeek(week: List<Availability>) {
        viewModelScope.launch {
            _isLoading.value = true
            val uid = auth.currentUser?.uid.orEmpty()
            if (uid.isBlank()) {
                _error.value = "Not authenticated"
                _isLoading.value = false
                return@launch
            }
            val normalized = week.map { it.copy(businessId = uid) }
            when (val res = repo.updateWeek(uid, normalized)) {
                is Resource.Success -> _success.value = "Availability saved"
                is Resource.Error -> _error.value = res.message
                is Resource.Loading -> Unit
            }
            _isLoading.value = false
        }
    }

    /** Awaitable save used by the fragment; enforces UID-only discipline. */
    suspend fun saveWeekAwait(week: List<Availability>): Resource<Unit> {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return Resource.Error("Not authenticated")
        if (_businessId.value != uid) return Resource.Error("businessId mismatch")

        val normalized = week.map { it.copy(businessId = uid) }
        return repo.updateWeek(uid, normalized)
    }

    fun clearMessages() {
        _error.value = null
        _success.value = null
    }
}
