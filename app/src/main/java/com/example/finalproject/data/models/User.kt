package com.example.finalproject.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val uid: String = "",   // Firebase UID as PK
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",          // optional, usually not stored locally
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Convenience constructor (no password)
    constructor(userName: String, userEmail: String, userPhone: String, uid: String) : this(
        uid = uid,
        username = userName,
        email = userEmail,
        phone = userPhone,
        password = ""
    )

    // Empty constructor required for Firebase toObject()
    constructor() : this("", "", "", "", "", "", 0L, 0L)
}
