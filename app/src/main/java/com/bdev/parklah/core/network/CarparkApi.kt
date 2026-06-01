package com.bdev.parklah.core.network

import com.bdev.parklah.core.model.CarparkDetailResponse
import com.bdev.parklah.core.model.CarparkRatesResponse
import com.bdev.parklah.core.model.NearbyResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CarparkApi {

    @GET("v1/carparks/nearby")
    suspend fun getNearby(
        @Query("lat")    lat: Double,
        @Query("lon")    lon: Double,
        @Query("radius") radius: Int = 600,
        @Query("limit")  limit: Int  = 20,
    ): NearbyResponse

    @GET("v1/carparks/{code}")
    suspend fun getDetail(
        @Path("code")    code: String,
        @Query("source") source: String,
    ): CarparkDetailResponse

    @GET("v1/carparks/{code}/rates")
    suspend fun getRates(
        @Path("code")          code: String,
        @Query("source")       source: String,
        @Query("vehicle_type") vehicleType: String = "C",
    ): CarparkRatesResponse
}
