package com.bdev.parklah.core.model

import com.google.gson.annotations.SerializedName

data class CarparkAvailabilityDto(
    @SerializedName("vehicle_type")   val vehicleType: String,
    @SerializedName("lots_available") val lotsAvailable: Int,
    @SerializedName("total_lots")     val totalLots: Int?,
    @SerializedName("snapshot_time")  val snapshotTime: String,
)

/** Raw API response — no business logic. */
data class CarparkNearby(
    @SerializedName("carpark_code")   val carparkCode: String,
    @SerializedName("carpark_name")   val carparkName: String,
    @SerializedName("data_source")    val dataSource: String,
    val lat: Double,
    val lon: Double,
    @SerializedName("distance_m")     val distanceM: Int,
    @SerializedName("parking_system") val parkingSystem: String?,
    val availability: List<CarparkAvailabilityDto>,
)

/** Domain model — computed fields are resolved once by the use case. */
data class Carpark(
    val carparkCode: String,
    val carparkName: String,
    val dataSource: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Int,
    val parkingSystem: String?,
    val lotsAvailable: Int,
    val totalLots: Int,
    val snapshotTime: String?,
    val availabilityFraction: Float,
    val availabilityStatus: AvailabilityStatus,
    val formattedDistance: String,
)

enum class AvailabilityStatus { GOOD, LOW, FULL, UNKNOWN }

data class BatchRequest(
    val codes: List<String>,
)

data class BatchMeta(
    val count: Int,
)

data class BatchResponse(
    val data: List<CarparkNearby>,
    val meta: BatchMeta,
)

data class NearbyMeta(
    val count: Int,
    @SerializedName("radius_m")
    val radiusM: Int,
)

data class NearbyResponse(
    val data: List<CarparkNearby>,
    val meta: NearbyMeta,
)
