package com.example.finalproject.colorsApi.models

data class ColorInfo(
    val name: String,
    val hex: String,
    val rgb: Triple<Int, Int, Int>
)
