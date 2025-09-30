// app/src/main/java/com/example/finalproject/data/local/onboarding/OnboardingEntity.kt
package com.example.finalproject.data.local.onboarding

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "onboarding_drafts")
data class OnboardingEntity(
    @PrimaryKey val uid: String,
    val businessId: String? = null,
    val currentStep: Int = 1,
    val isCompleted: Boolean = false,
    val businessName: String? = null,
    val businessPhone: String? = null,
    val businessAddress: String? = null,
    val selectedBusinessTypesCsv: String? = null,
    val servicesJson: String? = null,
    val workingHoursJson: String? = null,
    val instagramLink: String? = null,
    val aboutText: String? = null,
    val logoUri: String? = null,
    val primaryColor: String? = null
)
