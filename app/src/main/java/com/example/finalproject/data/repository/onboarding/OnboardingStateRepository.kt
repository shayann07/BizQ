// app/src/main/java/com/example/finalproject/data/repository/onboarding/OnboardingStateRepository.kt
package com.example.finalproject.data.repository.onboarding

import com.example.finalproject.data.models.onboarding.OnboardingState
import androidx.lifecycle.LiveData
import com.example.finalproject.utils.Resource

interface OnboardingStateRepository {
    fun observe(uid: String): LiveData<OnboardingState?>
    suspend fun getLocal(uid: String): OnboardingState?
    suspend fun upsertLocal(state: OnboardingState)
    suspend fun setStep(uid: String, step: Int, isCompleted: Boolean): Resource<Unit>
    suspend fun fetchRemote(uid: String): Resource<OnboardingState?>
    suspend fun upsertRemote(state: OnboardingState): Resource<Unit>
}
