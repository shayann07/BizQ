package com.example.finalproject.ui.availability

import android.app.TimePickerDialog
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.R
import com.example.finalproject.data.models.Availability
import com.example.finalproject.databinding.FragmentAvailabilityBinding
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.ui.register.BusinessRegistrationViewModel
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class AvailabilityFragment : Fragment() {

    private var binding: FragmentAvailabilityBinding by autoCleared()

    private val sharedViewModel: BusinessRegistrationViewModel by activityViewModels()
    private val onboardingVM: OnboardingViewModel by viewModels()
    private val availabilityVM: AvailabilityViewModel by viewModels()

    private lateinit var adapter: DayAvailabilityAdapter
    private var cameFromProfile: Boolean = false
    private var isSaving: Boolean = false

    // Step progress config (align with your flow)
    private val TOTAL_STEPS = 6
    private val CURRENT_STEP = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameFromProfile = arguments?.getBoolean("cameFromProfile", false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // ── Step visuals (determinate, always visible — same style as other screens)
        sharedViewModel.moveToStep(CURRENT_STEP)
        progress.isIndeterminate = false
        progress.visibility = View.VISIBLE
        renderStepProgress(CURRENT_STEP)
        toolbar.subtitle = buildStepSubtitle(CURRENT_STEP)

        // If your shared VM also emits a percent, you can still reflect it (optional)
        sharedViewModel.progressPercentage.observe(viewLifecycleOwner) { pct ->
            if (pct in 0..100) progress.setProgressCompat(pct, true)
        }

        // CTA based on origin
        btnContinue.text = if (cameFromProfile) getString(R.string.done) else getString(R.string.continue_label)

        // UID strictly from FirebaseAuth
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), R.string.need, Toast.LENGTH_SHORT).show()
            btnContinue.isEnabled = false
            return@with
        }

        // Mark step locally
        viewLifecycleOwner.lifecycleScope.launch {
            onboardingVM.setStep(uid, step = CURRENT_STEP, isCompleted = false)
        }

        // Recycler + adapter
        adapter = DayAvailabilityAdapter(
            onTimeClicked = ::showTimePicker,
            onSwitchChanged = sharedViewModel::toggleDayEnabled
        )
        rvWorkingHours.layoutManager = LinearLayoutManager(requireContext())
        rvWorkingHours.adapter = adapter

        // Make sure we have initial hours in shared VM
        sharedViewModel.ensureWorkingHoursSeeded()

        // UID-only: set and then bootstrap (Room-first → Firestore → Room)
        availabilityVM.setBusinessId(uid)
        availabilityVM.bootstrap()

        // Keep temp UI list in sync with shared VM
        sharedViewModel.workingHours.observe(viewLifecycleOwner) { hours ->
            adapter.submitList(hours.toList())
        }

        // If repo hydrated Room from Firestore, reflect into shared VM if changed
        availabilityVM.week.observe(viewLifecycleOwner) { days ->
            if (days.isNullOrEmpty()) return@observe
            val mapped = AvailabilityUiMapper.availabilityToWorkingHours(requireContext(), days)
            val current = sharedViewModel.workingHours.value.orEmpty()

            val needsUpdate =
                current.size != mapped.size ||
                        current.zip(mapped).any { (a, b) ->
                            a.isWorking != b.isWorking || a.startTime != b.startTime || a.endTime != b.endTime
                        }

            if (needsUpdate) {
                mapped.forEachIndexed { index, wh ->
                    sharedViewModel.updateHour(index, true, wh.startTime)
                    sharedViewModel.updateHour(index, false, wh.endTime)
                    sharedViewModel.toggleDayEnabled(index, wh.isWorking)
                }
            }
        }

        // Save & navigate — only when user taps (nothing writes to Firestore on direct edit)
        btnContinue.setOnClickListener {
            if (isSaving) return@setOnClickListener
            if (!sharedViewModel.validateWorkingHoursOrShowError(requireContext())) return@setOnClickListener

            val week: List<Availability> =
                AvailabilityUiMapper.workingHoursToAvailability(uid, sharedViewModel.workingHours.value.orEmpty())

            setSaving(true)
            viewLifecycleOwner.lifecycleScope.launch {
                when (val res = availabilityVM.saveWeekAwait(week)) {
                    is com.example.finalproject.utils.Resource.Success -> {
                        // Advance step (still not completed; Branding is step 6)
                        onboardingVM.setStep(uid, step = CURRENT_STEP + 1, isCompleted = false)
                        setSaving(false)
                        if (cameFromProfile) {
                            findNavController().popBackStack()
                        } else {
                            findNavController().navigate(R.id.action_availability_to_branding)
                        }
                    }
                    is com.example.finalproject.utils.Resource.Error -> {
                        setSaving(false)
                        Toast.makeText(
                            requireContext(),
                            res.message ?: getString(R.string.error_generic),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.example.finalproject.utils.Resource.Loading -> Unit
                }
            }
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    // Keep step bar determinate; only the button reflects saving state
    private fun setSaving(saving: Boolean) = with(binding) {
        isSaving = saving
        btnContinue.isEnabled = !saving
        // DO NOT flip the step bar to indeterminate; keep the determinate step UX consistent
        btnContinue.text = when {
            saving -> getString(R.string.wait_label)
            cameFromProfile -> getString(R.string.done)
            else -> getString(R.string.continue_label)
        }
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

    private fun showTimePicker(position: Int, isStart: Boolean) {
        val item = adapter.currentList.getOrNull(position) ?: return
        val parts = (if (isStart) item.startTime else item.endTime).split(":")
        val h0 = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m0 = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(requireContext(), { _, h, m ->
            val newTime = "%02d:%02d".format(h, m)
            sharedViewModel.updateHour(position, isStart, newTime)
        }, h0, m0, true).show()
    }
}
