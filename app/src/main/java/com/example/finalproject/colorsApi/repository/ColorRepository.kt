package com.example.finalproject.colorsApi.repository

import com.example.finalproject.colorsApi.models.ColorInfo
import com.example.finalproject.colorsApi.models.ColorSwatch


interface ColorRepository {
    suspend fun getColorInfo(hex: String): ColorInfo
    suspend fun getPalette(hex: String, mode: String, count: Int = 5): List<ColorSwatch>
}
