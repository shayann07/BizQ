package com.example.finalproject.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.finalproject.R
import com.example.finalproject.data.models.BusinessType
import com.example.finalproject.data.repository.BusinessRepository
import com.example.finalproject.databinding.BusinessTypeFragmentBinding
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class BusinessTypeFragment : Fragment() {

    private var binding: BusinessTypeFragmentBinding by autoCleared()

    private val sharedVM: BusinessRegistrationViewModel by activityViewModels()
    private val onboardingVM: OnboardingViewModel by viewModels()

    @Inject lateinit var businessRepo: BusinessRepository

    private var selectedType: String? = null
    private lateinit var adapter: BusinessTypeAdapter
    private var isSaving = false

    // Step progress config (align with your flow)
    private val TOTAL_STEPS = 6
    private val CURRENT_STEP = 3

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BusinessTypeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // ── Step visuals (determinate, visible — same as others)
        sharedVM.moveToStep(CURRENT_STEP)
        progress.isIndeterminate = false
        progress.visibility = View.VISIBLE
        renderStepProgress(CURRENT_STEP)
        toolbar.subtitle = buildStepSubtitle(CURRENT_STEP)

        // Optional: if your sharedVM emits a percent, mirror it (keeps consistent UX)
        sharedVM.progressPercentage.observe(viewLifecycleOwner) { pct ->
            if (pct in 0..100) progress.setProgressCompat(pct, true)
        }

        // UID-only discipline
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            findNavController().navigateUp()
            return@with
        }
        sharedVM.setUserFirebaseUid(uid)

        // Base list
        val base = listOf(
            BusinessType(
                title = getString(R.string.nails),
                iconPath = "https://cdn-icons-png.flaticon.com/512/599/599752.png"
            ),
            BusinessType(
                title = getString(R.string.beauty_saloon),
                iconPath = "https://w7.pngwing.com/pngs/969/104/png-transparent-beauty-parlour-hairstyle-logo-hair-mammal-face-people-thumbnail.png"
            ),
            BusinessType(
                title = getString(R.string.fitness_trainer),
                iconPath = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRHMFwYpyorYeHprzd_eNwh6OENAJ3aom-RXQ&s"
            ),
            BusinessType(
                title = getString(R.string.others),
                iconPath = "https://cdn-icons-png.freepik.com/256/8001/8001664.png"
            )
        )

        // Prefill from VM when returning
        val preselectedTitles = sharedVM.getSelectedBusinessTypes()
        selectedType = preselectedTitles.firstOrNull()
        val preselected = base.map { it.copy(isSelected = it.title == selectedType) }

        // Recycler
        adapter = BusinessTypeAdapter { title ->
            // single-select, no saving yet (only on Continue)
            selectedType = title
            sharedVM.onBusinessTypeSelected(title)
        }
        rvBusinessTypes.layoutManager = GridLayoutManager(requireContext(), 2)
        rvBusinessTypes.adapter = adapter
        adapter.submitList(preselected)

        // Keep list in sync with VM selection
        sharedVM.selectedBusinessTypes.observe(viewLifecycleOwner) { selected ->
            selectedType = selected.firstOrNull()
            val refreshed = base.map { it.copy(isSelected = selected.contains(it.title)) }
            adapter.submitList(refreshed)
        }

        // Continue → save to Firestore (merge) + Room → advance to next step
        btnContinue.setOnClickListener {
            if (isSaving) return@setOnClickListener

            val picked = selectedType
            if (picked.isNullOrBlank()) {
                Toast.makeText(requireContext(), R.string.need, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setSaving(true)
            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = businessRepo.upsertIndustry(ownerUid = uid, businessId = uid, industry = picked)) {
                    is Resource.Success -> {
                        onboardingVM.setStep(uid, step = CURRENT_STEP + 1, isCompleted = false)
                        setSaving(false)
                        navigateSafe(R.id.action_businessType_to_allServices)
                    }
                    is Resource.Error -> {
                        setSaving(false)
                        Toast.makeText(requireContext(), res.message ?: getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                    }
                    is Resource.Loading -> Unit
                }
            }
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    // --- UX helpers ---

    private fun setSaving(saving: Boolean) = with(binding) {
        isSaving = saving
        btnContinue.isEnabled = !saving
        btnContinue.text = if (saving) getString(R.string.wait_label) else getString(R.string.continue_label)
        // Keep the step bar determinate; don't flip to indeterminate here.
    }

    private fun renderStepProgress(step: Int) = with(binding) {
        val pct = ((step.toFloat() / TOTAL_STEPS) * 100f).roundToInt().coerceIn(0, 100)
        progress.setProgressCompat(pct, true)
        toolbar.subtitle = buildStepSubtitle(step, pct)
    }

    private fun buildStepSubtitle(step: Int, pct: Int? = null): String {
        return if (pct != null) "Step $step of $TOTAL_STEPS ($pct%)"
        else "Step $step of $TOTAL_STEPS"
    }

    private fun navigateSafe(destId: Int) {
        val nav = findNavController()
        val canNavigate = nav.currentDestination?.getAction(destId) != null || nav.graph.findNode(destId) != null
        if (canNavigate) nav.navigate(destId)
        else Toast.makeText(requireContext(), getString(R.string.navigation_unavailable), Toast.LENGTH_SHORT).show()
    }
}
