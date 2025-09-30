package com.example.finalproject.data.remote.colorapi

import com.example.finalproject.data.remote.colorapi.dto.IdDto
import com.example.finalproject.data.remote.colorapi.dto.SchemeDto
import retrofit2.http.GET
import retrofit2.http.Query

interface ColorApiService {
    // Query A: Color details
    @GET("id")
    suspend fun getColorByHex(@Query("hex") hexWithoutHash: String): IdDto

    // Query B/C: Palettes (analogic, triad, complement, etc.)
    @GET("scheme")
    suspend fun getScheme(
        @Query("hex") hexWithoutHash: String,
        @Query("mode") mode: String,
        @Query("count") count: Int = 5
    ): SchemeDto
}
