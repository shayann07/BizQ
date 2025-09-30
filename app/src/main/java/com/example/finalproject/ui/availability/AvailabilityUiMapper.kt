package com.example.finalproject.ui.availability

import android.content.Context
import com.example.finalproject.R
import com.example.finalproject.data.models.Availability
import com.example.finalproject.data.models.WeekDay
import com.example.finalproject.data.models.WorkingHour

object AvailabilityUiMapper {

    private fun minutesToHHmm(m: Int): String {
        val h = m / 60
        val mm = m % 60
        return "%02d:%02d".format(h, mm)
    }

    private fun hhmmToMinutes(s: String): Int {
        val p = s.split(":")
        val h = p.getOrNull(0)?.toIntOrNull() ?: 9
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    fun availabilityToWorkingHours(ctx: Context, days: List<Availability>): List<WorkingHour> {
        // dayOfWeek 0..6 → Sunday..Saturday
        val names = listOf(
            ctx.getString(R.string.day_sunday),
            ctx.getString(R.string.day_monday),
            ctx.getString(R.string.day_tuesday),
            ctx.getString(R.string.day_wednesday),
            ctx.getString(R.string.day_thursday),
            ctx.getString(R.string.day_friday),
            ctx.getString(R.string.day_saturday),
        )

        val weekDayEnum = listOf(
            WeekDay.SUNDAY, WeekDay.MONDAY, WeekDay.TUESDAY,
            WeekDay.WEDNESDAY, WeekDay.THURSDAY, WeekDay.FRIDAY, WeekDay.SATURDAY
        )

        // Defaults (your app’s defaults)
        val defaults = (0..6).map { i ->
            WorkingHour(
                dayName = names[i],
                isWorking = i in 1..5, // Mon–Fri true, Sun/Sat false
                startTime = "09:00",
                endTime = if (i == 5) "15:00" else "17:00",
                day = weekDayEnum[i]
            )
        }.toMutableList()

        // Apply remote/local saved availability over defaults
        days.forEach { d ->
            if (d.dayOfWeek in 0..6) {
                defaults[d.dayOfWeek] = WorkingHour(
                    dayName = names[d.dayOfWeek],
                    isWorking = d.isOpen,
                    startTime = minutesToHHmm(d.startMinutes),
                    endTime = minutesToHHmm(d.endMinutes),
                    day = weekDayEnum[d.dayOfWeek]
                )
            }
        }
        return defaults
    }

    fun workingHoursToAvailability(businessId: String, hours: List<WorkingHour>): List<Availability> {
        return hours.map { wh ->
            val dayIdx = when (wh.day) {
                WeekDay.SUNDAY -> 0
                WeekDay.MONDAY -> 1
                WeekDay.TUESDAY -> 2
                WeekDay.WEDNESDAY -> 3
                WeekDay.THURSDAY -> 4
                WeekDay.FRIDAY -> 5
                WeekDay.SATURDAY -> 6
            }
            Availability(
                businessId = businessId,
                dayOfWeek = dayIdx,
                isOpen = wh.isWorking,
                startMinutes = hhmmToMinutes(wh.startTime),
                endMinutes = hhmmToMinutes(wh.endTime)
            )
        }
    }
}
