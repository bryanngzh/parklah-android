package com.bdev.parklah.core.usecase

import com.bdev.parklah.core.model.AvailabilityStatus
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.model.CarparkNearby
import com.bdev.parklah.core.repository.CarparkRepository
import javax.inject.Inject

class GetNearbyCarparksUseCase @Inject constructor(
    private val repository: CarparkRepository,
) {
    suspend operator fun invoke(lat: Double, lon: Double): List<Carpark> =
        repository.getNearby(lat, lon).map { it.toDomain() }
}

private fun CarparkNearby.toDomain(): Carpark {
    val available = lotsAvailable ?: 0
    val total = totalLots ?: 0
    val fraction = if (total > 0) available.toFloat() / total else 0f
    val status = when {
        total == 0      -> AvailabilityStatus.UNKNOWN   // no data (coupon / URA)
        fraction > 0.3f -> AvailabilityStatus.GOOD
        fraction > 0.1f -> AvailabilityStatus.LOW
        else            -> AvailabilityStatus.FULL
    }
    val distance = if (distanceM >= 1000) "%.1f km".format(distanceM / 1000f) else "$distanceM m"
    return Carpark(
        carparkCode          = carparkCode,
        carparkName          = carparkName,
        dataSource           = dataSource,
        lat                  = lat,
        lon                  = lon,
        distanceM            = distanceM,
        parkingSystem        = parkingSystem,
        lotsAvailable        = available,
        totalLots            = total,
        snapshotTime         = snapshotTime,
        availabilityFraction = fraction,
        availabilityStatus   = status,
        formattedDistance    = distance,
    )
}
