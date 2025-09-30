// app/src/main/java/com/example/finalproject/data/repository/FirebaseImpl/OnboardingStateRepositoryImpl.kt
package com.example.finalproject.data.repository.FirebaseImpl

import androidx.lifecycle.LiveData
import com.example.finalproject.data.local.dao.OnboardingStateDao
import com.example.finalproject.data.repository.onboarding.OnboardingStateRepository
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.safeCall
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

import com.example.finalproject.data.models.onboarding.OnboardingState

@Singleton
class OnboardingStateRepositoryImpl @Inject constructor(
    private val onboardingDao: OnboardingStateDao
) : OnboardingStateRepository {

    private val onboardingRef = FirebaseFirestore.getInstance().collection("onboarding_state")

    override fun observe(uid: String): LiveData<OnboardingState?> =
        onboardingDao.observe(uid)

    override suspend fun getLocal(uid: String): OnboardingState? =
        onboardingDao.get(uid)

    override suspend fun upsertLocal(state: OnboardingState) {
        onboardingDao.upsert(state)
    }

    override suspend fun setStep(uid: String, step: Int, isCompleted: Boolean): Resource<Unit> = safeCall {
        val state = OnboardingState(uid, step, isCompleted, System.currentTimeMillis())
        onboardingDao.upsert(state)
        onboardingRef.document(uid).set(state).await()
        Resource.Success(Unit)
    }

    override suspend fun fetchRemote(uid: String): Resource<OnboardingState?> = safeCall {
        val snap = onboardingRef.document(uid).get().await()
        val remote = snap.toObject(OnboardingState::class.java)
        Resource.Success(remote)
    }

    override suspend fun upsertRemote(state: OnboardingState): Resource<Unit> = safeCall {
        onboardingRef.document(state.uid).set(state).await()
        Resource.Success(Unit)
    }
}
