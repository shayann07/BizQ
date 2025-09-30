package com.example.finalproject.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.finalproject.R
import com.example.finalproject.databinding.BusinessAboutFragmentBinding
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class BusinessAboutFragment : Fragment() {

    private var binding: BusinessAboutFragmentBinding by autoCleared()
    private val sharedVM: BusinessRegistrationViewModel by activityViewModels()
    private val onboardingVM: OnboardingViewModel by viewModels()

    // === Step progress config (adjust totals if your flow changes) ===
    private val TOTAL_STEPS = 6
    private val CURRENT_STEP = 2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BusinessAboutFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // ── Step visuals (UI only)
        sharedVM.moveToStep(CURRENT_STEP)
        // Determinate step progress (always visible on this screen)
        progress.isIndeterminate = false
        progress.isVisible = true
        renderStepProgress(CURRENT_STEP)

        // If you still want to listen to VM % (optional), map it to the same renderer:
        sharedVM.progressPercentage.observe(viewLifecycleOwner) { pct ->
            // Only update if VM sends a meaningful value; otherwise keep our step calc.
            if (pct in 0..100) progress.setProgressCompat(pct, true)
        }

        // ── UID-only: derive from FirebaseAuth
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return@with
        }
        sharedVM.setUserFirebaseUid(uid)

        // ── Prefill fields from VM (keeps config-change safe)
        sharedVM.businessName.observe(viewLifecycleOwner) { name ->
            if (etBusinessName.text?.toString() != name && name != null) etBusinessName.setText(name)
        }
        sharedVM.businessPhone.observe(viewLifecycleOwner) { phone ->
            if (etBusinessPhone.text?.toString() != phone && phone != null) etBusinessPhone.setText(phone)
        }
        sharedVM.businessAddress.observe(viewLifecycleOwner) { address ->
            if (etBusinessAddress.text?.toString() != address && address != null) etBusinessAddress.setText(address)
        }

        // ── Optional dropdowns (no persistence dependency)
        runCatching {
            val calendarOptions = resources.getStringArray(R.array.calendar_options)
            actvCalendar.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, calendarOptions)
            )
            val sourceOptions = resources.getStringArray(R.array.source_options)
            actvSource.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sourceOptions)
            )
        }

        // ── Live-edit mirrors into VM (draft-in-memory only)
        etBusinessName.doOnTextChanged { text, _, _, _ ->
            sharedVM.saveBusinessDetails(text?.toString().orEmpty(), etBusinessPhone.text?.toString().orEmpty(), etBusinessAddress.text?.toString())
        }
        etBusinessPhone.doOnTextChanged { text, _, _, _ ->
            sharedVM.saveBusinessDetails(etBusinessName.text?.toString().orEmpty(), text?.toString().orEmpty(), etBusinessAddress.text?.toString())
        }
        etBusinessAddress.doOnTextChanged { text, _, _, _ ->
            sharedVM.saveBusinessDetails(etBusinessName.text?.toString().orEmpty(), etBusinessPhone.text?.toString().orEmpty(), text?.toString())
        }

        // ── Loading state from VM saves (when finalize button is reused)
        sharedVM.isLoading.observe(viewLifecycleOwner) { setLoading(it) }

        // ── Continue → validate → persist to Firestore (merge) + Room → Step 3
        btnContinue.setOnClickListener {
            val name = etBusinessName.text?.toString()?.trim().orEmpty()
            val phone = etBusinessPhone.text?.toString()?.trim().orEmpty()
            val address = etBusinessAddress.text?.toString()

            tilBusinessName.error = if (name.isBlank()) getString(R.string.need) else null
            tilBusinessPhone.error = if (phone.isBlank()) getString(R.string.need) else null
            if (name.isBlank() || phone.isBlank()) return@setOnClickListener

            setLoading(true)
            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = sharedVM.saveOrUpdateBusinessBasic(name, phone, address)) {
                    is Resource.Success -> {
                        // Mark step locally (no remote state)
                        onboardingVM.setStep(uid, step = 3, isCompleted = false)
                        setLoading(false)
                        navigateSafe(R.id.action_businessAbout_to_businessType)
                    }
                    is Resource.Error -> {
                        setLoading(false)
                        Toast.makeText(requireContext(), res.message ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }

        toolbar.subtitle = buildStepSubtitle(CURRENT_STEP)
        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun renderStepProgress(step: Int) = with(binding) {
        val pct = ((step.toFloat() / TOTAL_STEPS) * 100f).roundToInt().coerceIn(0, 100)
        progress.setProgressCompat(pct, true)
        // also reflect in toolbar subtitle to show exact step & percent
        toolbar.subtitle = buildStepSubtitle(step, pct)
    }

    private fun buildStepSubtitle(step: Int, pct: Int? = null): String {
        return if (pct != null) "Step $step of $TOTAL_STEPS ($pct%)"
        else "Step $step of $TOTAL_STEPS"
    }

    private fun setLoading(b: Boolean) = with(binding) {
        btnContinue.isEnabled = !b
        // Keep the step progress determinate & visible; don't flip it to indeterminate here.
        // Just change the button label for saving state.
        btnContinue.text = if (b) getString(R.string.wait_label) else getString(R.string.continue_label)
    }

    private fun navigateSafe(destId: Int) {
        val nav = findNavController()
        val can = nav.currentDestination?.getAction(destId) != null || nav.graph.findNode(destId) != null
        if (can) nav.navigate(destId)
        else Toast.makeText(requireContext(), getString(R.string.navigation_unavailable), Toast.LENGTH_SHORT).show()
    }
}
