package com.bdev.parklah.core.repository

import com.bdev.parklah.core.model.BatchRequest
import com.bdev.parklah.core.model.CarparkNearby
import com.bdev.parklah.core.model.CarparkRatesDto
import com.bdev.parklah.core.network.CarparkApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarparkRepository @Inject constructor(
    private val api: CarparkApi,
) {
    suspend fun getNearby(lat: Double, lon: Double): List<CarparkNearby> =
        api.getNearby(lat = lat, lon = lon).data

    suspend fun getBatch(codes: List<String>, lat: Double, lon: Double): List<CarparkNearby> =
        api.getBatch(lat = lat, lon = lon, body = BatchRequest(codes)).data

    suspend fun getRates(code: String, source: String): CarparkRatesDto =
        api.getRates(code = code, source = source).data
}
