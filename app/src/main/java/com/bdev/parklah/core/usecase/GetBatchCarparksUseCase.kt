package com.bdev.parklah.core.usecase

import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.repository.CarparkRepository
import javax.inject.Inject

class GetBatchCarparksUseCase @Inject constructor(
    private val repository: CarparkRepository,
) {
    suspend operator fun invoke(codes: List<String>, lat: Double, lon: Double): List<Carpark> =
        repository.getBatch(codes, lat, lon).map { it.toDomain() }
}
