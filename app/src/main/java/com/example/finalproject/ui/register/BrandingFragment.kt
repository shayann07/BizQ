package com.example.finalproject.ui.register

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.R
import com.example.finalproject.colorsApi.adopters.ColorTileAdapter
import com.example.finalproject.colorsApi.models.ColorTile
import com.example.finalproject.colorsApi.util.HSpacing
import com.example.finalproject.databinding.BrandingFragmentBinding
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.Resource
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class BrandingFragment : Fragment() {

    private var binding: BrandingFragmentBinding by autoCleared()
    private val vm: BrandingViewModel by viewModels()
    private val onboardingVM: OnboardingViewModel by viewModels()

    private var pickedLogo: Uri? = null
    private lateinit var colorAdapter: ColorTileAdapter

    // Step progress config (align with your flow)
    private val TOTAL_STEPS = 6
    private val CURRENT_STEP = 6

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                pickedLogo = uri
                binding.selectedLogo.setImageURI(uri)
                binding.selectedLogo.visibility = View.VISIBLE
                binding.placeholderLayout.visibility = View.GONE
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                pickedLogo = uri
                binding.selectedLogo.setImageURI(uri)
                binding.selectedLogo.visibility = View.VISIBLE
                binding.placeholderLayout.visibility = View.GONE
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = BrandingFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        // ── Step visuals (determinate, visible; same style as BusinessAbout)
        progress.isIndeterminate = false
        progress.visibility = View.VISIBLE
        renderStepProgress(CURRENT_STEP)
        toolbar.subtitle = buildStepSubtitle(CURRENT_STEP)

        // Enforce UID-only flow
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_LONG).show()
            btnContinue.isEnabled = false
            return@with
        }

        // Mark step=6 (not complete yet)
        viewLifecycleOwner.lifecycleScope.launch {
            onboardingVM.setStep(uid, step = 6, isCompleted = false)
        }

        // Pick logo box
        boxLogo.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                galleryLauncher.launch("image/*")
            }
        }

        // Recycler setup (color strip)
        colorAdapter = ColorTileAdapter { tile ->
            val bgColor = Color.parseColor(tile.hex)
            root.setBackgroundColor(bgColor)
            cardInstagram.strokeColor = bgColor
            vm.setSelectedHex(tile.hex)
        }

        rvBackgroundImages.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = colorAdapter
            addItemDecoration(HSpacing(resources.getDimensionPixelSize(R.dimen.swatch_spacing)))
            clipToPadding = false
            setPadding(16, 0, 16, 0)
        }

        // Observe VM state (do NOT flip the step bar to indeterminate)
        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            btnContinue.isEnabled = !loading
            btnContinue.text = if (loading) getString(R.string.wait_label) else getString(R.string.continue_label)
            // keep the step bar determinate & visible; no changes here
        }
        vm.error.observe(viewLifecycleOwner) { e ->
            e?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                vm.clearMessages()
            }
        }
        vm.success.observe(viewLifecycleOwner) { ok ->
            if (ok == true) {
                vm.clearMessages()
                viewLifecycleOwner.lifecycleScope.launch {
                    onboardingVM.setStep(uid, step = 6, isCompleted = true)
                    findNavController().navigate(R.id.action_branding_to_setupCompleteFragment)
                }
            }
        }

        // Brand color reactive stroke
        vm.selectedHex.observe(viewLifecycleOwner) { hex ->
            runCatching { Color.parseColor(hex) }.onSuccess { c ->
                cardInstagram.strokeColor = c
            }
        }

        // === FETCH FROM THE COLOR API ===
        vm.fetchColorInfoIfNeeded()
        vm.fetchAnalogous(count = 6)
        vm.fetchTriad(count = 6)
        vm.fetchComplement(count = 6)

        // Hook API results into the RecyclerView (show analogous by default)
        vm.analogous.observe(viewLifecycleOwner) { res ->
            when (res) {
                is Resource.Success -> {
                    val tiles = res.data?.map { ColorTile(it.hex) }
                    colorAdapter.submitList(tiles)
                    // Optional: preselect first if you want an immediate preview
                    tiles?.let { if (it.isNotEmpty()) vm.setSelectedHex(tiles.first().hex) }
                }
                is Resource.Error -> {
                    Toast.makeText(requireContext(), res.message ?: "API error", Toast.LENGTH_SHORT).show()
                    colorAdapter.submitList(defaultTiles())
                }
                is Resource.Loading -> Unit
            }
        }

        // Continue → upload logo + save instagram/about (+ brand color inside VM)
        btnContinue.setOnClickListener {
            val instagram = editTextInstagram.text?.toString()?.trim().orEmpty()
            val about = editTextAbout.text?.toString()?.trim().orEmpty()
            vm.saveBranding(instagram, about, pickedLogo)
        }

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
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

    private fun defaultTiles(): List<ColorTile> = listOf(
        ColorTile("#1CC7F7"), ColorTile("#0EA5E9"), ColorTile("#22C55E"),
        ColorTile("#F59E0B"), ColorTile("#EF4444"),
        ColorTile("#8B5CF6"), ColorTile("#14B8A6"),
        ColorTile("#1CC7F7", isGradient = true, hex2 = "#0EA5E9"),
        ColorTile("#22C55E", isGradient = true, hex2 = "#84CC16")
    )
}
