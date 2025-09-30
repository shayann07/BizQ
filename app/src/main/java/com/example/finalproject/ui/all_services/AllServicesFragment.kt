package com.example.finalproject.ui.all_services

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.R
import com.example.finalproject.data.models.Service
import com.example.finalproject.databinding.FragmentAllServicesBinding
import com.example.finalproject.ui.adapters.ServicesAdapter
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.autoCleared
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class AllServicesFragment : Fragment() {

    private var binding: FragmentAllServicesBinding by autoCleared()
    private val onboardingVM: OnboardingViewModel by viewModels()
    private val vm: AllServicesViewModel by viewModels()

    private lateinit var servicesAdapter: ServicesAdapter
    private var cameFromProfile: Boolean = false

    // Step progress config (align with the rest of the flow)
    private val TOTAL_STEPS = 6
    private val CURRENT_STEP = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameFromProfile = arguments?.getBoolean("cameFromProfile", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentAllServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // Step UI (determinate, visible) — same style as other screens
        btnContinue.text = if (cameFromProfile) getString(R.string.done) else getString(R.string.continue_label)
        progress.isIndeterminate = false
        progress.visibility = View.VISIBLE
        renderStepProgress(CURRENT_STEP)
        toolbar.subtitle = buildStepSubtitle(CURRENT_STEP)

        // Strictly from FirebaseAuth
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
            btnContinue.isEnabled = false
            return@with
        }

        // Mark step (no redirect here)
        viewLifecycleOwner.lifecycleScope.launch {
            onboardingVM.setStep(uid, step = CURRENT_STEP, isCompleted = false)
        }

        setupRecycler()
        observeVm()

        // Bind by UID and hydrate Room from Firestore if empty (cold start safe)
        vm.setBusinessId(uid)
        vm.bootstrap()

        tvAddService.setOnClickListener { showAddServiceDialog() }

        btnContinue.setOnClickListener {
            if (cameFromProfile) {
                findNavController().popBackStack(); return@setOnClickListener
            }
            val list = vm.services.value.orEmpty()
            if (list.isEmpty()) {
                Toast.makeText(requireContext(), R.string.add_at_least_one_service, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                onboardingVM.setStep(uid, step = CURRENT_STEP + 1, isCompleted = false)
                findNavController().navigate(R.id.action_allServices_to_availability)
            }
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupRecycler() {
        servicesAdapter = ServicesAdapter(
            onEditClick = { service -> showEditServiceDialog(service) },
            onDeleteClick = { service -> showDeleteConfirmation(service) }
        )
        binding.rvServices.apply {
            adapter = servicesAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeVm() = with(binding) {
        vm.services.observe(viewLifecycleOwner) { services ->
            servicesAdapter.submitList(services)
            updateUI(services)
        }
        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            btnContinue.isEnabled = !loading
            // Keep step bar determinate; don't flip to indeterminate here.
            // If you want a spinner, add it inside the button separately.
        }
        vm.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                vm.clearMessages()
            }
        }
        vm.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                vm.clearMessages()
            }
        }
    }

    private fun updateUI(services: List<Service>) = with(binding) {
        val hasAny = services.isNotEmpty()
        btnContinue.isEnabled = hasAny || cameFromProfile
        tvAddService.text = if (hasAny) getString(R.string.add_another_service) else getString(R.string.add_first_service)
        btnContinue.alpha = if (hasAny || cameFromProfile) 1f else 0.5f
    }

    private fun showAddServiceDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.add_service_dialog, null)
        val nameEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServiceName)
        val durationEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServiceDuration)
        val priceEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServicePrice)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_add_service_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_add_service_positive)) { _, _ ->
                val name = nameEt.text?.toString().orEmpty().trim()
                val duration = durationEt.text?.toString().orEmpty().trim()
                val price = priceEt.text?.toString().orEmpty().trim()
                when (val v = vm.validateService(name, duration, price)) {
                    is ValidationResult.Success -> vm.addService(name, duration.toInt(), price.toInt())
                    is ValidationResult.Error -> Toast.makeText(requireContext(), v.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_add_service_negative), null)
            .show()
    }

    private fun showEditServiceDialog(service: Service) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.add_service_dialog, null)
        val nameEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServiceName)
        val durationEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServiceDuration)
        val priceEt = dialogView.findViewById<TextInputEditText>(R.id.editTextServicePrice)

        nameEt.setText(service.name)
        durationEt.setText(service.durationMinutes.toString())
        priceEt.setText(service.priceShekels.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_edit_service_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_edit_service_positive)) { _, _ ->
                val name = nameEt.text?.toString().orEmpty().trim()
                val duration = durationEt.text?.toString().orEmpty().trim()
                val price = priceEt.text?.toString().orEmpty().trim()
                when (val v = vm.validateService(name, duration, price)) {
                    is ValidationResult.Success -> {
                        vm.updateService(
                            service.copy(
                                name = name,
                                durationMinutes = duration.toInt(),
                                priceShekels = price.toInt()
                            )
                        )
                    }
                    is ValidationResult.Error -> Toast.makeText(requireContext(), v.message, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_edit_service_negative, null)
            .show()
    }

    private fun showDeleteConfirmation(service: Service) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_service_title))
            .setMessage(getString(R.string.dialog_delete_service_message, service.name))
            .setPositiveButton(getString(R.string.dialog_delete_service_positive)) { _, _ ->
                vm.deleteService(service)
            }
            .setNegativeButton(getString(R.string.dialog_delete_service_negative), null)
            .show()
    }

    // ── Progress helpers (same pattern used across your flow)
    private fun renderStepProgress(step: Int) = with(binding) {
        val pct = ((step.toFloat() / TOTAL_STEPS) * 100f).roundToInt().coerceIn(0, 100)
        progress.setProgressCompat(pct, true)
        toolbar.subtitle = buildStepSubtitle(step, pct)
    }

    private fun buildStepSubtitle(step: Int, pct: Int? = null): String {
        return if (pct != null) "Step $step of $TOTAL_STEPS ($pct%)"
        else "Step $step of $TOTAL_STEPS"
    }
}
