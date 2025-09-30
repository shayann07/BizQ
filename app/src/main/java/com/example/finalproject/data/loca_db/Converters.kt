package com.example.finalproject.data.local_db

import androidx.room.TypeConverter
import com.example.finalproject.data.models.Service
import com.example.finalproject.data.models.WorkingHour
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    private val gson = Gson()

    @TypeConverter
    @JvmStatic
    fun fromStringList(list: List<String>?): String =
        gson.toJson(list ?: emptyList<String>())

    @TypeConverter
    @JvmStatic
    fun toStringList(json: String?): List<String> =
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<String>>() {}.type)

    @TypeConverter
    @JvmStatic
    fun fromServiceList(list: List<Service>?): String =
        gson.toJson(list ?: emptyList<Service>())

    @TypeConverter
    @JvmStatic
    fun toServiceList(json: String?): List<Service> =
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<Service>>() {}.type)

    @TypeConverter
    @JvmStatic
    fun fromWorkingHourList(list: List<WorkingHour>?): String =
        gson.toJson(list ?: emptyList<WorkingHour>())

    @TypeConverter
    @JvmStatic
    fun toWorkingHourList(json: String?): List<WorkingHour> =
        if (json.isNullOrBlank()) emptyList()
        else gson.fromJson(json, object : TypeToken<List<WorkingHour>>() {}.type)
}
