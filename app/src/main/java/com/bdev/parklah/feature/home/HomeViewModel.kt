package com.bdev.parklah.feature.home

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.model.CarparkRatesDto
import com.bdev.parklah.core.usecase.GetCarparkRatesUseCase
import com.bdev.parklah.core.usecase.GetNearbyCarparksUseCase
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

sealed class ExpandedCarparkUiState {
    data object None : ExpandedCarparkUiState()
    data class Loading(val carpark: Carpark) : ExpandedCarparkUiState()
    data class Success(
        val carpark: Carpark,
        val rates: CarparkRatesDto,
        val selectedVehicle: String = "C",
    ) : ExpandedCarparkUiState()
}

private const val DEFAULT_LAT = 1.3521
private const val DEFAULT_LON = 103.8198

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNearbyCarparks: GetNearbyCarparksUseCase,
    private val getCarparkRates: GetCarparkRatesUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    private val _carparks = MutableStateFlow<List<Carpark>>(emptyList())
    val carparks: StateFlow<List<Carpark>> = _carparks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _mapCenter = MutableStateFlow<Pair<Double, Double>?>(null)
    val mapCenter: StateFlow<Pair<Double, Double>?> = _mapCenter.asStateFlow()

    private val _expandedCarpark = MutableStateFlow<ExpandedCarparkUiState>(ExpandedCarparkUiState.None)
    val expandedCarpark: StateFlow<ExpandedCarparkUiState> = _expandedCarpark.asStateFlow()

    @SuppressLint("MissingPermission")
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val location = fusedLocation
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()
                if (location != null) {
                    _userLocation.value = Pair(location.latitude, location.longitude)
                    fetchNearbyAt(location.latitude, location.longitude)
                } else {
                    fetchNearbyAt(DEFAULT_LAT, DEFAULT_LON)
                }
            } catch (e: Exception) {
                fetchNearbyAt(DEFAULT_LAT, DEFAULT_LON)
            }
        }

        if (locationCallback != null) return
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    _userLocation.value = Pair(loc.latitude, loc.longitude)
                }
            }
        }
        locationCallback = cb
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .build()
        fusedLocation.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    fun onLocationPermissionDenied() {
        fetchNearbyAt(DEFAULT_LAT, DEFAULT_LON)
    }

    fun onMapCenterChanged(lat: Double, lon: Double) {
        fetchNearbyAt(lat, lon)
    }

    fun refreshNearby() {
        val center = _mapCenter.value ?: return
        fetchNearbyAt(center.first, center.second)
    }

    fun onCarparkToggled(carpark: Carpark) {
        val current = _expandedCarpark.value
        val sameCode = when (current) {
            is ExpandedCarparkUiState.Loading -> current.carpark.carparkCode == carpark.carparkCode
            is ExpandedCarparkUiState.Success -> current.carpark.carparkCode == carpark.carparkCode
            else -> false
        }
        if (sameCode) {
            _expandedCarpark.value = ExpandedCarparkUiState.None
            return
        }
        _expandedCarpark.value = ExpandedCarparkUiState.Loading(carpark)
        viewModelScope.launch {
            try {
                val rates = getCarparkRates(carpark.carparkCode, carpark.dataSource)
                val firstVehicle = rates.shortTerm.firstOrNull()?.vehicleType ?: "C"
                _expandedCarpark.value = ExpandedCarparkUiState.Success(carpark, rates, firstVehicle)
            } catch (e: Exception) {
                _expandedCarpark.value = ExpandedCarparkUiState.None
            }
        }
    }

    fun onCarparkCodeTapped(code: String) {
        val carpark = _carparks.value.find { it.carparkCode == code } ?: return
        onCarparkToggled(carpark)
    }

    fun onVehicleSelected(vehicle: String) {
        val current = _expandedCarpark.value as? ExpandedCarparkUiState.Success ?: return
        _expandedCarpark.value = current.copy(selectedVehicle = vehicle)
    }

    override fun onCleared() {
        super.onCleared()
        locationCallback?.let { fusedLocation.removeLocationUpdates(it) }
    }

    private fun fetchNearbyAt(lat: Double, lon: Double) {
        _mapCenter.value = Pair(lat, lon)
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _carparks.value = getNearbyCarparks(lat, lon)
            } catch (e: Exception) {
                // silently keep previous results
            } finally {
                _isLoading.value = false
            }
        }
    }
}
