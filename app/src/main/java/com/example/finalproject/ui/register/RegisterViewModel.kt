// app/src/main/java/com/example/finalproject/ui/register/RegisterViewModel.kt
package com.example.finalproject.ui.register

import android.util.Patterns
import androidx.lifecycle.*
import com.example.finalproject.data.models.User
import com.example.finalproject.data.repository.AuthRepository
import com.example.finalproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _userRegistrationStatus = MutableLiveData<Resource<User>>()
    val userRegistrationStatus: LiveData<Resource<User>> = _userRegistrationStatus

    // RegisterViewModel.kt (add this)
    suspend fun profileExists(uid: String): Boolean {
        return repository.userProfileExists(uid)
    }


    fun createUser(userName: String, userEmail: String, userPhone: String, userPass: String) {
        // If already signed in, don't try to create a new account
        if (FirebaseAuth.getInstance().currentUser != null) {
            _userRegistrationStatus.postValue(Resource.Error("Already logged in."))
            return
        }

        val error = when {
            userEmail.isBlank() || userName.isBlank() || userPass.isBlank() || userPhone.isBlank() -> "Empty Strings"
            !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> "Not a valid email"
            userPass.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (error != null) {
            _userRegistrationStatus.postValue(Resource.Error(error))
            return
        }
        _userRegistrationStatus.value = Resource.Loading()
        viewModelScope.launch {
            val result = repository.createUser(userName, userEmail, userPhone, userPass)
            _userRegistrationStatus.postValue(result)
        }
    }
}
