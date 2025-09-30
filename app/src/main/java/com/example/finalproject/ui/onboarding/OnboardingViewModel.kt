package com.example.finalproject.ui.onboarding

import android.app.Application
import android.content.Context
import androidx.annotation.IdRes
import androidx.lifecycle.ViewModel
import com.example.finalproject.R
import com.example.finalproject.data.repository.onboarding.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnboardingState(
    val uid: String,
    val currentStep: Int = 1,    // 1..6
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val app: Application,
    private val onboardingRepo: OnboardingRepository // kept for future use if you also want to sync state remotely
) : ViewModel() {

    private val prefs by lazy {
        app.getSharedPreferences("onboarding_state", Context.MODE_PRIVATE)
    }

    // ------- Public API used by your fragments -------

    suspend fun getOrFetch(uid: String): OnboardingState? = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext null
        readLocalState(uid)
    }

    /**
     * Persist step locally first (fast, survives app kill).
     * You can extend this later to also mirror the state remotely if you want multi-device resume.
     */
    suspend fun setStepLocalFirst(uid: String, step: Int, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        if (uid.isBlank()) return@withContext
        writeLocalState(uid, step.coerceIn(1, 6), isCompleted)
    }

    /**
     * Alias used by some screens; currently same as local-first.
     */
    suspend fun setStep(uid: String, step: Int, isCompleted: Boolean) {
        setStepLocalFirst(uid, step, isCompleted)
    }

    /** For routing from Home or guards: which destination should we go to now? */
    @IdRes
    fun destinationFor(state: OnboardingState?): Int {
        state ?: return R.id.homeFragment
        if (state.isCompleted) return R.id.businessProfileFragment
        return stepToDestination(state.currentStep)
    }

    /** For routing *inside* the registration graph only. */
    @IdRes
    fun destinationWithinRegistration(state: OnboardingState?): Int {
        state ?: return R.id.registerFragment
        if (state.isCompleted) return R.id.businessProfileFragment
        return stepToDestination(state.currentStep)
    }


    fun clearLocal(uid: String) {
        if (uid.isBlank()) return
        prefs.edit()
            .remove(keyStep(uid))
            .remove(keyCompleted(uid))
            .apply()
    }


    // ------- Internal helpers -------

    private fun readLocalState(uid: String): OnboardingState {
        val step = prefs.getInt(keyStep(uid), 1)
        val completed = prefs.getBoolean(keyCompleted(uid), false)
        return OnboardingState(uid = uid, currentStep = step, isCompleted = completed)
    }

    private fun writeLocalState(uid: String, step: Int, completed: Boolean) {
        prefs.edit()
            .putInt(keyStep(uid), step)
            .putBoolean(keyCompleted(uid), completed)
            .apply()
    }

    private fun keyStep(uid: String) = "step_$uid"
    private fun keyCompleted(uid: String) = "completed_$uid"

    @IdRes
    private fun stepToDestination(step: Int): Int = when (step.coerceIn(1, 6)) {
        1 -> R.id.registerFragment
        2 -> R.id.businessAboutFragment
        3 -> R.id.businessTypeFragment
        4 -> R.id.allServicesFragment
        5 -> R.id.availabilityFragment
        6 -> R.id.brandingFragment
        else -> R.id.registerFragment
    }
}
