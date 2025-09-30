package com.example.finalproject.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class Service(
    @PrimaryKey val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val durationMinutes: Int = 0,
    val priceShekels: Int = 0,
    val description: String? = null,
    val isActive: Boolean = true
) {
    // Required by Firestore
    constructor() : this("", "", "", 0, 0, null, true)
}
