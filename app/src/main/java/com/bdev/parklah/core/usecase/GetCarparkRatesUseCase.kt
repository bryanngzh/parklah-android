package com.bdev.parklah.core.usecase

import com.bdev.parklah.core.model.CarparkRatesDto
import com.bdev.parklah.core.repository.CarparkRepository
import javax.inject.Inject

class GetCarparkRatesUseCase @Inject constructor(
    private val repository: CarparkRepository,
) {
    suspend operator fun invoke(code: String, source: String): CarparkRatesDto =
        repository.getRates(code, source)
}
