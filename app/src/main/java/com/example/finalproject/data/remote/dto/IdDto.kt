package com.example.finalproject.data.remote.colorapi.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IdDto(
    val name: Name? = null,
    val hex: Hex? = null,
    val rgb: Rgb? = null
) {
    @JsonClass(generateAdapter = true)
    data class Name(val value: String? = null)

    @JsonClass(generateAdapter = true)
    data class Hex(val value: String? = null) // "#0047AB"

    @JsonClass(generateAdapter = true)
    data class Rgb(val r: Int? = null, val g: Int? = null, val b: Int? = null)
}
