package com.example.finalproject.colorsApi.repository

import com.example.finalproject.colorsApi.models.ColorInfo
import com.example.finalproject.colorsApi.models.ColorSwatch
import com.example.finalproject.data.remote.colorapi.ColorApiService
import javax.inject.Inject

class ColorRepositoryImpl @Inject constructor(
    private val api: ColorApiService
) : ColorRepository {

    override suspend fun getColorInfo(hex: String): ColorInfo {
        val res = api.getColorByHex(hex.removePrefix("#"))
        return ColorInfo(
            name = res.name?.value ?: "Unknown",
            hex = res.hex?.value ?: "#$hex",
            rgb = Triple(res.rgb?.r ?: 0, res.rgb?.g ?: 0, res.rgb?.b ?: 0)
        )
    }

    override suspend fun getPalette(hex: String, mode: String, count: Int): List<ColorSwatch> {
        val res = api.getScheme(hex.removePrefix("#"), mode, count)
        return res.colors.map {
            ColorSwatch(
                hex = it.hex?.value ?: "#000000",
                label = it.name?.value
            )
        }
    }
}
