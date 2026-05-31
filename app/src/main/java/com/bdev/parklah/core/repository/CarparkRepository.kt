package com.bdev.parklah.core.repository

import com.bdev.parklah.core.model.CarparkDetailDto
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

    suspend fun getDetail(code: String, source: String): CarparkDetailDto =
        api.getDetail(code = code, source = source).data

    suspend fun getRates(code: String, source: String): CarparkRatesDto =
        api.getRates(code = code, source = source).data
}
