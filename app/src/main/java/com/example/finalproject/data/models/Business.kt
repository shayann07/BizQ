package com.example.finalproject.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "business")
data class Business(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val name: String,
    val phone: String,
    val address: String? = null,
    val industry: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val isPublic: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val instagramHandle: String? = null,    // e.g. "haile_salon"
    val instagramUrl: String? = null
)