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
    val fraction = if (totalLots > 0) lotsAvailable.toFloat() / totalLots else 0f
    val status = when {
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
        lotsAvailable        = lotsAvailable,
        totalLots            = totalLots,
        snapshotTime         = snapshotTime,
        availabilityFraction = fraction,
        availabilityStatus   = status,
        formattedDistance    = distance,
    )
}
