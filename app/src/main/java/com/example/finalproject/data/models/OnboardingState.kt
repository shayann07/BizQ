// app/src/main/java/com/example/finalproject/data/models/onboarding/OnboardingState.kt
package com.example.finalproject.data.models.onboarding

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "onboarding_state")
data class OnboardingState(
    @PrimaryKey val uid: String,
    val currentStep: Int = 1,
    val isCompleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
