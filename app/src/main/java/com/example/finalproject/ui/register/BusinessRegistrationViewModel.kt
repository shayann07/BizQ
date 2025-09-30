package com.example.finalproject.ui.register

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject.R
import com.example.finalproject.data.models.Business
import com.example.finalproject.data.models.Service
import com.example.finalproject.data.models.WeekDay
import com.example.finalproject.data.models.WorkingHour
import com.example.finalproject.data.repository.BusinessRepository
import com.example.finalproject.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The single source of truth for the registration wizard UI state.
 * - Uses ONLY FirebaseAuth UID provided by caller via setUserFirebaseUid(uid).
 * - NO SharedPreferences, NO random/device IDs.
 * - Persists via BusinessRepository (Firestore merge + Room mirror).
 * - Seeds and edits working hours for the Availability step (UI-only).
 * - Branding fields (about/instagram/logo uri) are kept in-memory; logo upload is handled by BusinessProfileViewModel.
 */
@HiltViewModel
class BusinessRegistrationViewModel @Inject constructor(
    private val businessRepo: BusinessRepository
) : ViewModel() {

    // ----------------- Identity (UID-only) -----------------

    private val _uid = MutableLiveData<String?>()
    val userFirebaseUid: LiveData<String?> = _uid

    fun setUserFirebaseUid(uid: String) {
        if (_uid.value != uid) _uid.value = uid
    }

    // ----------------- Step / Progress (UI only) -----------------

    private val _currentStep = MutableLiveData(1)
    val progressPercentage: LiveData<Int> = MediatorLiveData<Int>().apply {
        fun compute(step: Int?) = postValue(((step ?: 1).coerceIn(1, 6) * 100) / 6)
        addSource(_currentStep) { compute(it) }
        compute(_currentStep.value)
    }

    fun moveToStep(step: Int) {
        _currentStep.value = step.coerceIn(1, 6)
    }

    // ----------------- Basic business details (Step 2) -----------------

    private val _businessName = MutableLiveData<String?>()
    val businessName: LiveData<String?> = _businessName

    private val _businessPhone = MutableLiveData<String?>()
    val businessPhone: LiveData<String?> = _businessPhone

    private val _businessAddress = MutableLiveData<String?>()
    val businessAddress: LiveData<String?> = _businessAddress

    fun saveBusinessDetails(name: String, phone: String, address: String?) {
        _businessName.value = name
        _businessPhone.value = phone
        _businessAddress.value = address
    }

    /**
     * Creates or updates the Business document:
     *  - Path: users/{uid}/business/main
     *  - id == ownerUid == uid (enforced by repository)
     *  - Uses getOrFetchByOwner(uid) to decide create vs update to preserve createdAt.
     */
    suspend fun saveOrUpdateBusinessBasic(
        name: String,
        phone: String,
        address: String?
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        val uid = _uid.value.orEmpty()
        if (uid.isBlank()) return@withContext Resource.Error("Missing UID")

        // Remember fields locally
        _businessName.postValue(name)
        _businessPhone.postValue(phone)
        _businessAddress.postValue(address)

        return@withContext try {
            val existing = businessRepo.getOrFetchByOwner(uid)
            val now = System.currentTimeMillis()

            val industry = selectedBusinessTypes.value?.firstOrNull().orEmpty()

            val toSave = if (existing == null) {
                Business(
                    id = uid,
                    ownerUid = uid,
                    name = name,
                    phone = phone,
                    address = address,
                    industry = industry,
                    logoUrl = null,
                    description = existing?.description, // none yet
                    isPublic = false,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                existing.copy(
                    name = name,
                    phone = phone,
                    address = address,
                    industry = if (industry.isNotBlank()) industry else existing.industry,
                    // keep existing.logoUrl; upload handled by BusinessProfileViewModel
                    updatedAt = now
                )
            }

            val res = if (existing == null) businessRepo.createBusiness(toSave)
            else businessRepo.updateBusiness(toSave)

            if (res is Resource.Success) Resource.Success(Unit)
            else Resource.Error((res as? Resource.Error)?.message ?: "Failed to save business")
        } catch (t: Throwable) {
            Resource.Error(t.message ?: "Failed to save business")
        }
    }

    // ----------------- Business type (Step 3) -----------------

    private val _selectedBusinessTypes = MutableLiveData<List<String>>(emptyList())
    val selectedBusinessTypes: LiveData<List<String>> = _selectedBusinessTypes

    fun onBusinessTypeSelected(singleTitle: String) {
        _selectedBusinessTypes.value = listOf(singleTitle)
    }

    fun getSelectedBusinessTypes(): List<String> = _selectedBusinessTypes.value.orEmpty()

    // ----------------- Services (Step 4) — mirror for summary only -----------------

    private val _servicesMirror = MutableLiveData<List<Service>>(emptyList())
    fun updateServicesList(services: List<Service>) {
        _servicesMirror.value = services
    }

    // ----------------- Availability (Step 5) — UI-only seed/edit -----------------

    private val _workingHours = MutableLiveData<List<WorkingHour>>()
    val workingHours: LiveData<List<WorkingHour>> = _workingHours

    /** Ensure the list exists with sensible defaults: Mon–Fri 09:00–17:00 (Fri 15:00), Sun/Sat closed. */
    fun ensureWorkingHoursSeeded() {
        if (_workingHours.value != null) return
        val defaults = listOf(
            wh("09:00", "17:00", false, WeekDay.SUNDAY),
            wh("09:00", "17:00", true,  WeekDay.MONDAY),
            wh("09:00", "17:00", true,  WeekDay.TUESDAY),
            wh("09:00", "17:00", true,  WeekDay.WEDNESDAY),
            wh("09:00", "17:00", true,  WeekDay.THURSDAY),
            wh("09:00", "15:00", true,  WeekDay.FRIDAY),
            wh("09:00", "17:00", false, WeekDay.SATURDAY)
        )
        _workingHours.value = defaults
    }

    fun toggleDayEnabled(position: Int, enabled: Boolean) {
        val list = _workingHours.value?.toMutableList() ?: return
        if (position !in list.indices) return
        list[position] = list[position].copy(isWorking = enabled)
        _workingHours.value = list
    }

    fun updateHour(position: Int, isStart: Boolean, newTime: String) {
        val list = _workingHours.value?.toMutableList() ?: return
        if (position !in list.indices) return
        val it = list[position]
        list[position] = if (isStart) it.copy(startTime = newTime) else it.copy(endTime = newTime)
        _workingHours.value = list
    }

    /** Returns true if valid; otherwise shows a toast and returns false. */
    @SuppressLint("StringFormatInvalid")
    fun validateWorkingHoursOrShowError(ctx: Context): Boolean {
        val list = _workingHours.value.orEmpty()
        for (item in list) {
            if (!item.isWorking) continue
            val s = hhmm(item.startTime)
            val e = hhmm(item.endTime)
            if (s == null || e == null || e <= s) {
                Toast.makeText(ctx, ctx.getString(R.string.invalid_time_range, item.dayName), Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    // ----------------- Branding (Step 6) — about/link kept in-memory; logo uploaded elsewhere -----------------

    private val _instagramLink = MutableLiveData<String?>()
    val instagramLink: LiveData<String?> = _instagramLink

    private val _aboutText = MutableLiveData<String?>()
    val aboutText: LiveData<String?> = _aboutText

    private val _logo = MutableLiveData<Uri?>()
    val logo: LiveData<Uri?> = _logo

    fun updateBrandingData(instagramLink: String?, aboutText: String?, logo: Uri?) {
        _instagramLink.value = instagramLink
        _aboutText.value = aboutText
        _logo.value = logo
    }

    fun updateLogo(uri: Uri?) {
        _logo.value = uri
    }

    // ----------------- Persist current business snapshot -----------------

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _successMessage = MutableLiveData<String?>()
    val successMessage: LiveData<String?> = _successMessage

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /**
     * Merge current VM fields into the Business document.
     * - Does NOT upload logo; call BusinessProfileViewModel.uploadLogo(uri) for that.
     * - Safely preserves createdAt/logoUrl/isPublic unless explicitly changed.
     */
    fun saveBusiness() {
        val uid = _uid.value.orEmpty()
        if (uid.isBlank()) {
            _error.value = "Missing UID"
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = businessRepo.getOrFetchByOwner(uid)
                val now = System.currentTimeMillis()

                val merged = (existing ?: Business(
                    id = uid,
                    ownerUid = uid,
                    name = _businessName.value.orEmpty(),
                    phone = _businessPhone.value.orEmpty(),
                    address = _businessAddress.value,
                    industry = selectedBusinessTypes.value?.firstOrNull().orEmpty(),
                    isPublic = false,
                    createdAt = now,
                    updatedAt = now
                )).copy(
                    name = _businessName.value?.takeIf { it.isNotBlank() } ?: (existing?.name ?: ""),
                    phone = _businessPhone.value?.takeIf { it.isNotBlank() } ?: (existing?.phone ?: ""),
                    address = _businessAddress.value ?: existing?.address,
                    industry = selectedBusinessTypes.value?.firstOrNull() ?: (existing?.industry ?: ""),
                    description = _aboutText.value ?: existing?.description,
                    // logoUrl preserved here; uploading handled elsewhere which calls updateBusiness(...)
                    updatedAt = now
                )

                val res = if (existing == null) businessRepo.createBusiness(merged)
                else businessRepo.updateBusiness(merged)

                withContext(Dispatchers.Main) {
                    if (res is Resource.Success) {
                        _successMessage.value = "Business saved"
                    } else {
                        _error.value = (res as? Resource.Error)?.message ?: "Failed to save"
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    _error.value = t.message ?: "Failed to save"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun clearMessages() {
        _successMessage.value = null
        _error.value = null
    }

    // ----------------- Helpers -----------------

    private fun wh(start: String, end: String, working: Boolean, day: WeekDay) =
        WorkingHour(
            dayName = "", // UI uses getString(day.stringRes)
            isWorking = working,
            startTime = start,
            endTime = end,
            day = day
        )

    private fun hhmm(s: String): Int? {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: return null
        val m = p.getOrNull(1)?.toIntOrNull() ?: return null
        return (h * 60) + m
    }
}
