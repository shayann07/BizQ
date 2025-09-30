// app/src/main/java/com/example/finalproject/ui/register/RegistrationGateViewModel.kt
package com.example.finalproject.ui.register

import androidx.lifecycle.ViewModel
import com.example.finalproject.data.repository.BusinessRepository
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class RegistrationGateViewModel @Inject constructor(
    private val businessRepo: BusinessRepository
) : ViewModel() {

    /** Returns true if there’s already a business for this UID (Room or Firestore). */
    suspend fun userHasBusiness(uid: String): Boolean {
        return businessRepo.getOrFetchByOwner(uid) != null
    }
}
