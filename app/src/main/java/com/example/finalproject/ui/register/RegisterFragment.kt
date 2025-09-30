package com.example.finalproject.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.finalproject.R
import com.example.finalproject.databinding.FragmentRegisterBinding
import com.example.finalproject.ui.register.BusinessRegistrationViewModel
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var binding: FragmentRegisterBinding by autoCleared()

    private val registerVM: RegisterViewModel by viewModels()
    private val sharedWizardVM: BusinessRegistrationViewModel by activityViewModels()
    private val gateVM: RegistrationGateViewModel by viewModels()

    private var routingInProgress = false
    private var handledSuccess = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // Wizard step visuals only (no identity is stored here)
        sharedWizardVM.moveToStep(1)
        sharedWizardVM.progressPercentage.observe(viewLifecycleOwner) { pct ->
            progress.progress = pct
        }

        // If already authenticated → route directly based on existing business
        maybeRouteIfAuthenticated()

        // Observe registration result
        registerVM.userRegistrationStatus.observe(viewLifecycleOwner) { res ->
            when (res) {
                is Resource.Loading -> setLoading(true)
                is Resource.Success -> {
                    if (handledSuccess) return@observe
                    handledSuccess = true
                    setLoading(false)

                    // Auth just created; get UID and proceed to Step 2
                    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                    if (uid.isBlank()) {
                        Toast.makeText(requireContext(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                        return@observe
                    }

                    sharedWizardVM.setUserFirebaseUid(uid)
                    navigateSafe(R.id.action_register_to_businessAbout)
                }
                is Resource.Error -> {
                    setLoading(false)
                    Toast.makeText(
                        requireContext(),
                        res.message ?: getString(R.string.error_generic),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Continue -> create account if not authenticated; else jump to step 2
        btnContinue.setOnClickListener {
            val current = FirebaseAuth.getInstance().currentUser
            if (current == null) {
                // Not logged in → create account
                createAccount()
            } else {
                // Already logged in → no account creation here; proceed to Step 2
                val uid = current.uid
                sharedWizardVM.setUserFirebaseUid(uid)
                navigateSafe(R.id.action_register_to_businessAbout)
            }
        }

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun createAccount() = with(binding) {
        val name = inputFullName.text?.toString()?.trim().orEmpty()
        val email = inputEmail.text?.toString()?.trim().orEmpty()
        val phone = inputPhone.text?.toString()?.trim().orEmpty()
        val pass = inputPassword.text?.toString().orEmpty()

        setLoading(true)
        registerVM.createUser(name, email, phone, pass)
        // Loading state cleared by observer
    }

    private fun maybeRouteIfAuthenticated() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        if (routingInProgress) return
        routingInProgress = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                setLoading(true)
                // Hard gate: if a Business already exists (in Room/Firestore) → go profile
                val hasBusiness = gateVM.userHasBusiness(uid)
                if (hasBusiness) {
                    navigateSafe(R.id.action_global_businessProfileFragment)
                } else {
                    // No business yet → proceed with wizard (Step 1 UI, then Continue goes to Step 2)
                    sharedWizardVM.setUserFirebaseUid(uid)
                }
            } finally {
                setLoading(false)
                routingInProgress = false
            }
        }
    }

    private fun setLoading(isLoading: Boolean) = with(binding) {
        btnContinue.isEnabled = !isLoading
        btnContinue.text = if (isLoading) getString(R.string.wait_label) else getString(R.string.continue_label)
        progress.isVisible = isLoading
    }

    private fun navigateSafe(destId: Int) {
        val nav = findNavController()
        val canNavigate = nav.currentDestination?.getAction(destId) != null || nav.graph.findNode(destId) != null
        if (canNavigate) nav.navigate(destId)
        else Toast.makeText(requireContext(), getString(R.string.navigation_unavailable), Toast.LENGTH_SHORT).show()
    }
}
