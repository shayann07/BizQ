package com.example.finalproject.ui.register

import android.net.Uri
import androidx.lifecycle.*
import com.example.finalproject.colorsApi.models.ColorSwatch
import com.example.finalproject.colorsApi.repository.ColorRepository
import com.example.finalproject.data.models.Business
import com.example.finalproject.data.repository.BusinessRepository
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class BrandingViewModel @Inject constructor(
    private val businessRepo: BusinessRepository,
    private val colorRepo: ColorRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val storage by lazy { FirebaseStorage.getInstance() }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _success = MutableLiveData<Boolean?>()
    val success: LiveData<Boolean?> = _success

    // brand accent currently selected
    private val _selectedHex = MutableLiveData("#1CC7F7")
    val selectedHex: LiveData<String> = _selectedHex

    // palettes from API
    private val _analogous = MutableLiveData<Resource<List<ColorSwatch>>>(Resource.Success(emptyList()))
    val analogous: LiveData<Resource<List<ColorSwatch>>> = _analogous

    private val _triad = MutableLiveData<Resource<List<ColorSwatch>>>(Resource.Success(emptyList()))
    val triad: LiveData<Resource<List<ColorSwatch>>> = _triad

    private val _complement = MutableLiveData<Resource<List<ColorSwatch>>>(Resource.Success(emptyList()))
    val complement: LiveData<Resource<List<ColorSwatch>>> = _complement

    fun setSelectedHex(hex: String) {
        _selectedHex.value = if (hex.startsWith("#")) hex else "#$hex"
    }

    // Query A: color info (optional to show name)
    fun fetchColorInfoIfNeeded() {
        val hex = _selectedHex.value ?: "#1CC7F7"
        viewModelScope.launch {
            // no UI binding required here unless you want to show the color name
            runCatching { colorRepo.getColorInfo(hex) }
                .onFailure { /* ignore or log */ }
        }
    }

    // Query B: Analogous palette
    fun fetchAnalogous(count: Int = 5) {
        val hex = _selectedHex.value ?: "#1CC7F7"
        _analogous.value = Resource.Loading(data = emptyList())
        viewModelScope.launch {
            runCatching { colorRepo.getPalette(hex, "analogic", count) }
                .onSuccess { _analogous.value = Resource.Success(it) }
                .onFailure { _analogous.value = Resource.Error(it.toString()) }
        }
    }

    // Query C: Triad palette
    fun fetchTriad(count: Int = 5) {
        val hex = _selectedHex.value ?: "#1CC7F7"
        _triad.value = Resource.Loading(data = emptyList())
        viewModelScope.launch {
            runCatching { colorRepo.getPalette(hex, "triad", count) }
                .onSuccess { _triad.value = Resource.Success(it) }
                .onFailure { _triad.value = Resource.Error(it.toString()) }
        }
    }

    // Bonus: Complement palette (optional third distinct list to show)
    fun fetchComplement(count: Int = 5) {
        val hex = _selectedHex.value ?: "#1CC7F7"
        _complement.value = Resource.Loading(data = emptyList())
        viewModelScope.launch {
            runCatching { colorRepo.getPalette(hex, "complement", count) }
                .onSuccess { _complement.value = Resource.Success(it) }
                .onFailure { _complement.value = Resource.Error(it.toString()) }
        }
    }

    /**
     * Saves branding fields (instagram, about) and uploads logo if provided.
     * Business id == Firebase UID always.
     */
    fun saveBranding(instagramLink: String, aboutText: String, logoUri: Uri?) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) { _error.value = "Not authenticated"; return }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1) Upload logo if chosen
                val uploadedLogoUrl = if (logoUri != null) uploadLogo(uid, logoUri) else null

                // 2) Current business (Room→Firestore→Room)
                val current = businessRepo.getOrFetchByOwner(uid) ?: Business(
                    id = uid, ownerUid = uid,
                    name = "", phone = "",
                    address = null, industry = "",
                    logoUrl = null, description = null,
                    isPublic = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                // 3) Normalize Instagram
                val (igHandle, igUrl) = normalizeInstagram(instagramLink)

                // 4) Build update
                val updated = current.copy(
                    logoUrl = uploadedLogoUrl ?: current.logoUrl,
                    description = aboutText.ifBlank { current.description },
                    instagramHandle = igHandle,
                    instagramUrl = igUrl,
                    updatedAt = System.currentTimeMillis()
                )

                // 5) Persist (Firestore merge + Room mirror inside repo)
                businessRepo.updateBusiness(updated)

                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save branding"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun normalizeInstagram(raw: String): Pair<String?, String?> {
        var s = raw.trim()
        if (s.isBlank()) return null to null
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
            // full URL given → extract last path segment as handle if possible
            val handle = s.removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").removePrefix("instagram.com/")
                .substringBefore('?').trim('/').substringBefore('/')
                .takeIf { it.matches(Regex("^[A-Za-z0-9._]+$")) }
            val url = "https://instagram.com/${handle ?: ""}".trimEnd('/')
            return handle to url
        }
        // @name or name
        val handle = s.removePrefix("@")
            .removePrefix("instagram.com/").removePrefix("www.instagram.com/")
            .substringBefore('?').trim('/').substringBefore('/')
        val valid = handle.matches(Regex("^[A-Za-z0-9._]+$"))
        return if (valid) handle to "https://instagram.com/$handle" else null to null
    }


    /** Uploads to users/{uid}/business/main/logo.jpg and returns the download URL. */
    private suspend fun uploadLogo(uid: String, uri: Uri): String {
        val ref = storage.reference
            .child("users")
            .child(uid)
            .child("business")
            .child("main")
            .child("logo.jpg")

        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    fun clearMessages() {
        _error.value = null
        _success.value = null
    }
}
