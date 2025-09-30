package com.example.finalproject.data.models

import androidx.room.Entity

@Entity(
    tableName = "availability",
    primaryKeys = ["businessId","dayOfWeek"]
)
data class Availability(
    val businessId: String,
    val dayOfWeek: Int,     // 0..6 (match your WeekDay enum order)
    val isOpen: Boolean,
    val startMinutes: Int,  // minutes since midnight
    val endMinutes: Int     // minutes since midnight
) {
    // Firestore needs a no-arg ctor
    constructor() : this("", 0, false, 9*60, 17*60)
}
