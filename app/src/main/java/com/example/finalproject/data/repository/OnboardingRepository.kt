// app/src/main/java/com/example/finalproject/data/repository/onboarding/OnboardingRepository.kt
package com.example.finalproject.data.repository.onboarding

import com.example.finalproject.data.models.onboarding.OnboardingDraft

interface OnboardingRepository {
    suspend fun saveDraftLocal(draft: OnboardingDraft)
    suspend fun getDraftLocal(uid: String): OnboardingDraft?
    suspend fun deleteDraftLocal(uid: String)

    suspend fun saveDraftRemote(draft: OnboardingDraft): Any?
    suspend fun getDraftRemote(uid: String): OnboardingDraft?

    suspend fun getOrFetchDraft(uid: String): OnboardingDraft?
}
