package com.example.finalproject.ui.login

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
import com.example.finalproject.databinding.FragmentLoginBinding
import com.example.finalproject.ui.register.BusinessRegistrationViewModel
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var binding: FragmentLoginBinding by autoCleared()

    private val loginVM: LoginViewModel by viewModels()
    private val wizardVM: BusinessRegistrationViewModel by activityViewModels()

    private var routingInProgress = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // --- UI actions ---
        buttonLogin.setOnClickListener {
            val email = editTextLoginEmail.editText?.text?.toString().orEmpty().trim()
            val pass  = editTextLoginPass.editText?.text?.toString().orEmpty()
            loginVM.signInUser(email, pass)
        }

        noAcountTv.setOnClickListener {
            navigateSafe(R.id.action_login_to_register)
        }

        // --- Observe login result ---
        loginVM.userSignInStatus.observe(viewLifecycleOwner) { res ->
            when (res) {
                is Resource.Loading -> setLoading(true)
                is Resource.Success -> {
                    setLoading(false)
                    routeAfterLogin()
                }
                is Resource.Error -> {
                    setLoading(false)
                    Toast.makeText(requireContext(), res.message ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun routeAfterLogin() {
        if (routingInProgress) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            return
        }

        routingInProgress = true
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Cache UID in the shared wizard VM (in-memory only, no persistence)
                wizardVM.setUserFirebaseUid(uid)

                val hasProfile = loginVM.profileExists(uid)
                if (hasProfile) {
                    // ✅ Business profile exists → go directly to profile
                    navigateSafe(R.id.action_loginFragment_to_businessProfileFragment)
                } else {
                    // ❌ No profile yet → enter registration graph (Step 1 → Step 2)
                    navigateSafe(R.id.action_login_to_register)
                }
            } finally {
                setLoading(false)
                routingInProgress = false
            }
        }
    }

    private fun setLoading(loading: Boolean) = with(binding) {
        loginProgressBar.isVisible = loading
        buttonLogin.isEnabled = !loading
    }

    private fun navigateSafe(destId: Int) {
        val nav = findNavController()
        val canNavigate = nav.currentDestination?.getAction(destId) != null || nav.graph.findNode(destId) != null
        if (canNavigate) nav.navigate(destId)
        else Toast.makeText(requireContext(), getString(R.string.navigation_unavailable), Toast.LENGTH_SHORT).show()
    }
}
