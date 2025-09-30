package com.example.finalproject.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.finalproject.R
import com.example.finalproject.databinding.FragmentSetupCompleteBinding
import com.example.finalproject.ui.business_profile.BusinessProfileViewModel
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetupCompleteFragment : Fragment() {

    private var binding: FragmentSetupCompleteBinding by autoCleared()

    private val onboardingVM: OnboardingViewModel by viewModels()
    // Use the profile VM to optionally pre-hydrate Room from Firestore before we navigate
    private val profileVM: BusinessProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSetupCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // Button enabled state mirrors nothing else here; cloud writes happened in previous steps
        btnViewPage.isEnabled = true

        // Finalize: mark onboarding complete with UID only, optionally warm local caches, then navigate
        btnViewPage.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (uid.isBlank()) {
                // No logged-in user — go Home safely
                findNavController().navigate(R.id.action_global_homeFragment)
                return@setOnClickListener
            }

            btnViewPage.isEnabled = false
            btnViewPage.text = getString(R.string.wait_label)

            viewLifecycleOwner.lifecycleScope.launch {
                // 1) Mark onboarding complete (UID only; no device ids, no randoms)
                onboardingVM.setStep(uid, step = 6, isCompleted = true)

                // 2) (Optional but smooth) Pre-hydrate Room from Firestore so profile opens with data
                //    This is idempotent and safe even if already cached.
                profileVM.loadBusinessData(uid)

                // 3) Navigate to Business Profile
                findNavController().navigate(R.id.action_global_businessProfileFragment)

                // UI clean-up (in case we return here via back)
                btnViewPage.isEnabled = true
                btnViewPage.text = getString(R.string.view_business_page)
            }
        }

        // Start over: sign out and go Home
        tvStartOver.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            findNavController().navigate(R.id.action_global_homeFragment)
        }
    }
}
