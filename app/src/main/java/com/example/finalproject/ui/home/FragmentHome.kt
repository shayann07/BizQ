package com.example.finalproject.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.finalproject.R
import com.example.finalproject.databinding.FragmentHomeBinding
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.ui.register.RegistrationGateViewModel
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var binding: FragmentHomeBinding by autoCleared()
    private val onboardingVM: OnboardingViewModel by viewModels()
    private val gateVM: RegistrationGateViewModel by viewModels() // kept in case you need elsewhere

    private var routingInProgress = false
    private val TOTAL_STEPS = 6

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        routeIfLoggedIn()
    }

    private fun setupClickListeners() = with(binding) {
        btnNewBusiness.setOnClickListener { findNavController().navigate(R.id.action_home_to_register) }
        btnLogin.setOnClickListener { findNavController().navigate(R.id.action_home_to_login) }
        tvExistingAccount.setOnClickListener { findNavController().navigate(R.id.action_home_to_login) }
        btnLanguage.setOnClickListener { showLanguageOptions() }
    }

    /**
     * New routing rules:
     * - Prefer CLOUD onboarding state when available.
     * - If NOT completed and step in 1..(TOTAL_STEPS-1): wipe & logout (abandoned setup).
     * - If completed: go to profile.
     * - Else: go to registration.
     */
    private fun routeIfLoggedIn() {
        if (routingInProgress) return
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        routingInProgress = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1) Try cloud onboarding state first
                val cloud = fetchCloudOnboarding(uid) // may be null
                // 2) Fallback to local prefs if cloud missing
                val local = onboardingVM.getOrFetch(uid)

                val effectiveStep = cloud?.step ?: (local?.currentStep ?: 0)
                val effectiveCompleted = cloud?.completed ?: (local?.isCompleted ?: false)

                // Abandoned registration → wipe & logout
                if (!effectiveCompleted && effectiveStep in 1 until TOTAL_STEPS) {
                    cleanupUserEverywhere(uid)
                    return@launch
                }

                // Completed → profile
                if (effectiveCompleted) {
                    if (findNavController().currentDestination?.id != R.id.businessProfileFragment) {
                        findNavController().navigate(R.id.action_homeFragment_to_businessProfileFragment)
                    }
                    return@launch
                }

                // Otherwise → registration
                if (findNavController().currentDestination?.id != R.id.nav_registration) {
                    findNavController().navigate(R.id.action_home_to_register)
                }
            } finally {
                routingInProgress = false
            }
        }
    }

    // -------- Cloud onboarding fetch (checks both possible locations) --------
    private suspend fun fetchCloudOnboarding(uid: String): CloudOnbState? {
        val db = FirebaseFirestore.getInstance()

        // Try global collection: /onboarding/{uid}
        runCatching {
            val d = db.collection("onboarding").document(uid).get().await()
            if (d.exists()) {
                val step = (d.getLong("currentStep") ?: d.getLong("step") ?: 0).toInt()
                val completed = d.getBoolean("isCompleted") ?: d.getBoolean("completed") ?: false
                return CloudOnbState(step, completed)
            }
        }

        // Try nested doc: /users/{uid}/onboarding/state
        runCatching {
            val d = db.collection("users").document(uid)
                .collection("onboarding").document("state").get().await()
            if (d.exists()) {
                val step = (d.getLong("currentStep") ?: d.getLong("step") ?: 0).toInt()
                val completed = d.getBoolean("isCompleted") ?: d.getBoolean("completed") ?: false
                return CloudOnbState(step, completed)
            }
        }

        return null
    }

    private data class CloudOnbState(val step: Int, val completed: Boolean)

    /**
     * Wipes Firestore docs + best-effort Auth deletion + sign out.
     * Leaves user on Home (logged-out).
     */
    private suspend fun cleanupUserEverywhere(uid: String) {
        val db = FirebaseFirestore.getInstance()
        val userDoc = db.collection("users").document(uid)

        // Cloud cleanup (best-effort)
        runCatching { userDoc.collection("business").document("main").delete().await() }
        runCatching { deleteAllDocs(userDoc.collection("services")) }
        runCatching { deleteAllDocs(userDoc.collection("availability")) }
        runCatching { db.collection("onboarding").document(uid).delete().await() }
        runCatching { userDoc.collection("onboarding").document("state").delete().await() }
        runCatching { userDoc.delete().await() }

        // Local cleanup
        runCatching { onboardingVM.clearLocal(uid) }

        // Auth deletion (requires recent login; ignore failure and still sign out)
        FirebaseAuth.getInstance().currentUser?.let { runCatching { it.delete().await() } }
        FirebaseAuth.getInstance().signOut()
    }

    private suspend fun deleteAllDocs(colRef: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val snap = colRef.limit(400).get().await()
            if (snap.isEmpty) break
            for (doc in snap.documents) runCatching { doc.reference.delete().await() }
            if (snap.size() < 400) break
        }
    }

    private fun showLanguageOptions() {
        val languages = arrayOf("עברית", "English")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("בחר שפה / Choose Language")
            .setItems(languages) { _, which ->
                when (which) {
                    0 -> updateLanguage("he")
                    1 -> updateLanguage("en")
                }
            }
            .show()
    }

    private fun updateLanguage(languageCode: String) {
        val locale = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(locale)
    }

    override fun onResume() {
        super.onResume()
        routeIfLoggedIn()
    }
}
