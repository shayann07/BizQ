package com.example.finalproject.colorsApi.models

data class ColorTile(
    val hex: String,          // e.g. "#1CC7F7"
    val isGradient: Boolean = false,
    val hex2: String? = null  // optional second color for gradient
)
