package com.bdev.parklah.core.model

import com.google.gson.annotations.SerializedName

data class CarparkRatesResponse(
    val data: CarparkRatesDto,
)

data class CarparkRatesDto(
    @SerializedName("short_term") val shortTerm: List<ShortTermRateDto>,
    val season: List<SeasonRateDto>,
)

data class ShortTermRateDto(
    @SerializedName("vehicle_type")    val vehicleType: String,
    @SerializedName("day_type")        val dayType: String,       // "weekday" | "saturday" | "sunday_ph" | "all"
    @SerializedName("start_time")      val startTime: String,     // "HH:mm"
    @SerializedName("end_time")        val endTime: String,       // "HH:mm"
    @SerializedName("rate_per_30min")  val ratePerHalfHour: Double,
    @SerializedName("min_duration")    val minDuration: String?,
    @SerializedName("is_current")      val isCurrent: Boolean,
)

data class SeasonRateDto(
    @SerializedName("vehicle_type")  val vehicleType: String,
    @SerializedName("ticket_type")   val ticketType: String,
    @SerializedName("parking_hrs")   val parkingHrs: String,
    @SerializedName("monthly_rate")  val monthlyRate: Double,
)
