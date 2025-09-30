package com.example.finalproject.data.remote.colorapi.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SchemeDto(
    val colors: List<Color> = emptyList()
) {
    @JsonClass(generateAdapter = true)
    data class Color(
        val hex: Hex? = null,
        val name: Name? = null
    ) {
        @JsonClass(generateAdapter = true)
        data class Hex(val value: String? = null)

        @JsonClass(generateAdapter = true)
        data class Name(val value: String? = null)
    }
}
