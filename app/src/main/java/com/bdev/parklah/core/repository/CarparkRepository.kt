package com.bdev.parklah.core.repository

import com.bdev.parklah.core.model.CarparkNearby
import com.bdev.parklah.core.network.CarparkApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarparkRepository @Inject constructor(
    private val api: CarparkApi,
) {
    suspend fun getNearby(lat: Double, lon: Double): List<CarparkNearby> =
        api.getNearby(lat = lat, lon = lon).data
}
