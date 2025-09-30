// com/example/finalproject/data/models/WorkingHour.kt
package com.example.finalproject.data.models

import androidx.annotation.Keep
import com.example.finalproject.R

@Keep
data class WorkingHour(
    var dayName: String = "",
    var isWorking: Boolean = false,
    var startTime: String = "09:00",
    var endTime: String = "17:00",
    var day: WeekDay = WeekDay.MONDAY
)

@Keep
enum class WeekDay(val stringRes: Int) {
    SUNDAY(R.string.day_sunday),
    MONDAY(R.string.day_monday),
    TUESDAY(R.string.day_tuesday),
    WEDNESDAY(R.string.day_wednesday),
    THURSDAY(R.string.day_thursday),
    FRIDAY(R.string.day_friday),
    SATURDAY(R.string.day_saturday)
}
