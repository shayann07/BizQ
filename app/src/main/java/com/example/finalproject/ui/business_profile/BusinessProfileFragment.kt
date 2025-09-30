package com.example.finalproject.ui.business_profile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.finalproject.R
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.models.Service
import com.example.finalproject.databinding.FragmentBusinessProfileBinding
import com.example.finalproject.ui.adapters.ServicesAdapter
import com.example.finalproject.ui.onboarding.OnboardingViewModel
import com.example.finalproject.utils.autoCleared
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Calendar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.finalproject.ui.all_services.AllServicesViewModel
import com.example.finalproject.ui.all_services.ValidationResult
import com.google.android.material.textfield.TextInputEditText

@AndroidEntryPoint
class BusinessProfileFragment : Fragment() {

    private var binding: FragmentBusinessProfileBinding by autoCleared()

    private val viewModel: BusinessProfileViewModel by viewModels()
    private val onboardingVM: OnboardingViewModel by viewModels()

    private lateinit var servicesAdapter: ServicesAdapter

    private val vm: AllServicesViewModel by viewModels()

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onLogoPicked(uri)
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onLogoPicked(uri)
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


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBusinessProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)


        btnLanguage.setOnClickListener { showLanguageOptions() }

        // ⬇️ Exit the app when back is pressed on this screen
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Prefer finishAndRemoveTask() on Lollipop+; fall back to finishAffinity()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        requireActivity().finishAndRemoveTask()
                    } else {
                        requireActivity().finishAffinity()
                    }
                    // If you want the app to go to background instead of fully finishing, use:
                    // requireActivity().moveTaskToBack(true)
                }
            }
        )

        setupRecycler()
        observeVm()
        setupClicks()

        // UID-only: hydrate screen
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.action_businessProfileFragment_to_homeFragment)
            return@with
        }
        viewModel.loadBusinessData(uid)

        // Optional: keep onboarding at last step if user lands here directly
        viewLifecycleOwner.lifecycleScope.launch {
            onboardingVM.setStep(uid, step = 6, isCompleted = true)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // UI wiring
    // ──────────────────────────────────────────────────────────────────────────────

    private fun setupRecycler() = with(binding) {
        servicesAdapter = ServicesAdapter(
            onEditClick = { service -> showEditServiceDialog(service) },
            onDeleteClick = { service -> showDeleteConfirmation(service) }
        )
        rvServices.layoutManager = LinearLayoutManager(requireContext())
        rvServices.adapter = servicesAdapter
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


    private fun observeVm() = with(binding) {
        // Business card
        viewModel.business.observe(viewLifecycleOwner) { biz ->
            if (biz != null) {
                tvBusinessName.text = biz.name
                tvAddress.text = biz.address.orEmpty()
                tvDescription.text = biz.description.orEmpty()
                if (!biz.logoUrl.isNullOrBlank()) loadImageInto(biz.logoUrl!!)
            } else {
                tvBusinessName.text = ""
                tvAddress.text = ""
                tvDescription.text = ""
            }
        }

        // Services list (Room-backed, Firestore mirrored)
        viewModel.services.observe(viewLifecycleOwner) { list: List<Service> ->
            servicesAdapter.submitList(list)
        }

        // Weekly hours
        viewModel.workingHours.observe(viewLifecycleOwner) { week ->
            updateOpenNowStatus(week)
            updateWeeklyHours(week)
        }

        // Progress / errors
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
        //    progress.isIndeterminate = loading
          //  progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }
        viewModel.successMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearMessages()
            }
        }
    }

    private fun setupClicks() = with(binding) {
        // Navigate to address in maps
        btnNavigate.setOnClickListener {
            val address = tvAddress.text?.toString()?.trim().orEmpty()
            if (address.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$address")))
            } else {
                Toast.makeText(requireContext(), "address missing", Toast.LENGTH_SHORT).show()
            }
        }

        // Dial business
        btnCall.setOnClickListener {
            val phone = viewModel.business.value?.phone.orEmpty().trim()
            if (phone.isNotEmpty()) {
                val uri = if (phone.startsWith("tel:", true)) Uri.parse(phone) else Uri.parse("tel:$phone")
                val intent = Intent(Intent.ACTION_DIAL, uri)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "No dialer app found", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(),"phone missing", Toast.LENGTH_SHORT).show()
            }
        }

        // Open Instagram link if available (app first, else browser)
        btnShare.setOnClickListener {
            // Prefer a dedicated instagramLink field if you have it; falling back to name as you do now
            val raw = viewModel.business.value?.let { b ->
                // try b.instagramLink ?: b.name if you store it
                b.instagramUrl // or however you store it; replace with your field
            }?.toString().orEmpty().trim()

            if (raw.isEmpty()) {
                Toast.makeText(requireContext(), "instagramLink missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (handle, finalBrowserUrl) = normalizeInstagramInput(raw)

            // If we extracted a handle, try opening the Instagram app directly
            if (handle != null) {
                val appUri = Uri.parse("http://instagram.com/_u/$handle") // works with package set
                val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                    setPackage("com.instagram.android")
                }
                val pm = requireContext().packageManager
                if (appIntent.resolveActivity(pm) != null) {
                    startActivity(appIntent)
                    return@setOnClickListener
                }
            }

            // Fallback: open the URL (either original full URL or https://instagram.com/<handle>) in any browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(finalBrowserUrl))
            val pm = requireContext().packageManager
            if (browserIntent.resolveActivity(pm) != null) {
                startActivity(browserIntent)
            } else {
                Toast.makeText(requireContext(), "No app found to open the link", Toast.LENGTH_SHORT).show()
            }
        }

        // Edit Hours → Availability (cameFromProfile=true)
        btnEditHours.setOnClickListener {
            val args = Bundle().apply { putBoolean("cameFromProfile", true) }
            safeNavigateTo(findNavController(), R.id.availabilityFragment, args, fallbackActionId = R.id.action_businessProfile_to_editHours)
        }

        // Add Service → AllServices (cameFromProfile=true)
        btnAddService.setOnClickListener {
            val args = Bundle().apply { putBoolean("cameFromProfile", true) }
            safeNavigateTo(findNavController(), R.id.allServicesFragment, args, fallbackActionId = R.id.action_businessProfile_to_addService)
        }

        // Edit description inline (simple alert text input)
        btnEditDescription.setOnClickListener { showEditDescriptionDialog() }

        // Upload / change logo
        ivBusinessLogo.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                galleryLauncher.launch("image/*")
            }
        }

        // Logout → back to Home
        btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_logout_title))
                .setMessage(getString(R.string.dialog_logout_message))
                .setPositiveButton(getString(R.string.dialog_logout_confirm)) { _, _ ->
                    // 1) Sign out
                    FirebaseAuth.getInstance().signOut()

                    // 2) Navigate with a GLOBAL action + clear back stack safely
                    if (!isAdded) return@setPositiveButton
                    val nav = findNavController()

                    val opts = androidx.navigation.navOptions {
                        // Clear everything above the root graph (my_nav), keep Home as root
                        popUpTo(R.id.my_nav) { inclusive = false }
                        launchSingleTop = true
                        restoreState = false
                    }

                    // Post to ensure we navigate after the dialog has fully dismissed
                    view?.post {
                        runCatching {
                            nav.navigate(R.id.action_global_homeFragment, null, opts)
                        }.onFailure {
                            // As an extra safety net, try navigating directly to the destination id
                            runCatching { nav.navigate(R.id.homeFragment, null, opts) }
                        }
                    }
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }

    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────────


    private fun normalizeInstagramInput(raw: String): Pair<String?, String> {
        var s = raw.trim()

        // If it's already a full URL, use it as-is for browser; no handle extraction needed
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) {
            return null to s
        }

        // Strip common prefixes and @
        s = s.removePrefix("@")
            .removePrefix("www.instagram.com/")
            .removePrefix("instagram.com/")
            .substringBefore('?')
            .trim()

        // If user pasted a path like "username/" or "username/some/extra", keep only the username
        val username = s.substringBefore('/')

        // Basic username validation (Instagram allows letters, numbers, periods, underscores)
        val isValid = username.isNotBlank() && username.matches(Regex("^[A-Za-z0-9._]+$"))
        val browserUrl = if (isValid) "https://instagram.com/$username" else "https://instagram.com/"

        return if (isValid) username to browserUrl else null to browserUrl
    }



    private fun onLogoPicked(uri: Uri) {
        // Preview immediately
        binding.ivBusinessLogo.setImageURI(uri)
        // Upload to Firebase Storage, then save logoUrl in Firestore + Room
        viewModel.uploadLogo(uri)
    }

    private fun showEditDescriptionDialog() {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val editText = android.widget.EditText(requireContext())
        editText.setText(binding.tvDescription.text)

        builder.setTitle(getString(R.string.dialog_edit_description_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                viewModel.updateBusinessDescription(editText.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun updateWeeklyHours(week: List<Availability>) = with(binding) {
        val labels = arrayOf(
            tvHoursSunday, tvHoursMonday, tvHoursTuesday, tvHoursWednesday,
            tvHoursThursday, tvHoursFriday, tvHoursSaturday
        )
        for (i in 0..6) labels[i].text = getString(R.string.closed)

        week.forEach { day ->
            if (day.dayOfWeek in 0..6) {
                labels[day.dayOfWeek].text =
                    if (!day.isOpen) getString(R.string.closed)
                    else "${minutesToHHmm(day.startMinutes)} - ${minutesToHHmm(day.endMinutes)}"
            }
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun updateOpenNowStatus(week: List<Availability>) = with(binding) {
        if (week.isEmpty()) {
            tvStatus.text = getString(R.string.hours_not_set)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            return@with
        }
        val cal = Calendar.getInstance()
        val todayIdx = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = week.firstOrNull { it.dayOfWeek == todayIdx }

        if (today == null || !today.isOpen) {
            tvStatus.text = getString(R.string.closed_today)
            tvStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            return@with
        }

        val openNow = now in today.startMinutes until today.endMinutes
        val window = "${minutesToHHmm(today.startMinutes)} - ${minutesToHHmm(today.endMinutes)}"
        tvStatus.text = if (openNow) getString(R.string.open_now_fmt, window) else getString(R.string.closed, window)
        tvStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (openNow) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
    }

    private fun minutesToHHmm(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return "%02d:%02d".format(h, mm)
    }

    private fun safeNavigateTo(
        nav: NavController,
        @IdRes destId: Int,
        args: Bundle? = null,
        @IdRes fallbackActionId: Int? = null
    ) {
        try {
            if (nav.graph.findNode(destId) != null) {
                nav.navigate(destId, args); return
            }
            if (fallbackActionId != null && nav.currentDestination?.getAction(fallbackActionId) != null) {
                nav.navigate(fallbackActionId, args); return
            }
            Log.e("BusinessProfileFragment", "Navigation target not found. dest=$destId action=$fallbackActionId from=${nav.currentDestination?.id}")
            Toast.makeText(requireContext(), R.string.navigation_unavailable, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalArgumentException) {
            Log.e("BusinessProfileFragment", "Navigation error: ${e.message}", e)
            Toast.makeText(requireContext(), "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e("BusinessProfileFragment", "Unexpected navigation error", t)
            Toast.makeText(requireContext(), "Unexpected navigation error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImageInto(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    val stream = URL(url).openStream()
                    BitmapFactory.decodeStream(stream)
                }
                binding.ivBusinessLogo.setImageBitmap(bmp)
            } catch (_: Exception) {
                // ignore; user may have poor network — UI will still work
            }
        }
    }
}
