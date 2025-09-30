// app/src/main/java/com/example/finalproject/ui/login/LoginViewModel.kt
package com.example.finalproject.ui.login

import androidx.lifecycle.*
import com.example.finalproject.data.repository.AuthRepository
import com.example.finalproject.data.models.User
import com.example.finalproject.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRep: AuthRepository
) : ViewModel() {

    private val _userSignInStatus = MutableLiveData<Resource<User>>()
    val userSignInStatus: LiveData<Resource<User>> = _userSignInStatus

    private val _currentUser = MutableLiveData<Resource<User>>()
    val currentUser: LiveData<Resource<User>> = _currentUser

    init {
        viewModelScope.launch {
            _currentUser.postValue(Resource.Loading())
            _currentUser.postValue(authRep.currentUser())
        }
    }


    // NEW: suspend helper to check Firestore/Room for user profile
    suspend fun profileExists(uid: String): Boolean {
        return authRep.userProfileExists(uid)
    }

    fun signInUser(userEmail: String, userPass: String) {
        if (userEmail.isEmpty() || userPass.isEmpty()) {
            _userSignInStatus.postValue(Resource.Error("Empty email or password"))
            return
        }
        _userSignInStatus.postValue(Resource.Loading())
        viewModelScope.launch {
            val loginResult = authRep.login(userEmail, userPass)
            _userSignInStatus.postValue(loginResult)
        }
    }
}
