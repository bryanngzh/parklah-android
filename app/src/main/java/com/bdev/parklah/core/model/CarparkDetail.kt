package com.bdev.parklah.core.model

import com.google.gson.annotations.SerializedName

data class CarparkDetailResponse(
    val data: CarparkDetailDto,
)

data class CarparkDetailDto(
    @SerializedName("carpark_code")   val carparkCode: String,
    @SerializedName("carpark_name")   val carparkName: String,
    @SerializedName("data_source")    val dataSource: String,
    @SerializedName("carpark_type")   val carparkType: String?,
    @SerializedName("parking_system") val parkingSystem: String?,
    val lat: Double?,
    val lon: Double?,
    @SerializedName("total_lots")     val totalLots: Int?,
    val features: CarparkFeaturesDto?,
    val availability: List<CarparkAvailabilityDto>,
)

data class CarparkFeaturesDto(
    @SerializedName("short_term_parking") val shortTermParking: String,
    @SerializedName("free_parking")       val freeParking: String,
    @SerializedName("night_parking")      val nightParking: Boolean,
    @SerializedName("car_park_decks")     val carParkDecks: Int,
    @SerializedName("gantry_height")      val gantryHeight: Double,
    @SerializedName("car_park_basement")  val carParkBasement: Boolean,
    @SerializedName("is_central_area")    val isCentralArea: Boolean,
    @SerializedName("is_peak_hour_carpark") val isPeakHourCarpark: Boolean,
)

data class CarparkAvailabilityDto(
    @SerializedName("vehicle_type")    val vehicleType: String,
    @SerializedName("lots_available")  val lotsAvailable: Int,
    @SerializedName("total_lots")      val totalLots: Int?,
    @SerializedName("snapshot_time")   val snapshotTime: String,
)
