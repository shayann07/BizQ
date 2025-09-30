package com.example.finalproject.data.models.onboarding

import com.example.finalproject.data.models.Service
import com.example.finalproject.data.models.WorkingHour

data class OnboardingDraft(
    val uid: String = "",
    val businessId: String? = null,
    val currentStep: Int = 1,
    val isCompleted: Boolean = false,
    val businessName: String? = null,
    val businessPhone: String? = null,
    val businessAddress: String? = null,
    val selectedBusinessTypes: List<String> = emptyList(),
    val services: List<Service> = emptyList(),
    val workingHours: List<WorkingHour> = emptyList(),
    val instagramLink: String? = null,
    val aboutText: String? = null,
    val logoUri: String? = null,
    val primaryColor: String? = null
) {
    // Extra safety for Firestore reflection
    constructor() : this(
        uid = "",
        businessId = null,
        currentStep = 1,
        isCompleted = false,
        businessName = null,
        businessPhone = null,
        businessAddress = null,
        selectedBusinessTypes = emptyList(),
        services = emptyList(),
        workingHours = emptyList(),
        instagramLink = null,
        aboutText = null,
        logoUri = null,
        primaryColor = null
    )
}
