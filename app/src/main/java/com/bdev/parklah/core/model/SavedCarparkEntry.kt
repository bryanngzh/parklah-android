package com.bdev.parklah.core.model

data class SavedCarparkEntry(
    val carparkCode: String,
    val carparkName: String,
    val dataSource: String,
    val lat: Double,
    val lon: Double,
    val parkingSystem: String?,
)
