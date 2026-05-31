package com.bdev.parklah.feature.home

import android.annotation.SuppressLint
import android.content.Context
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.usecase.GetNearbyCarparksUseCase
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

private const val DEFAULT_LAT = 1.3521
private const val DEFAULT_LON = 103.8198

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getNearbyCarparks: GetNearbyCarparksUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val fusedLocation = LocationServices.getFusedLocationProviderClient(context)

    private val _carparks = MutableStateFlow<List<Carpark>>(emptyList())
    val carparks: StateFlow<List<Carpark>> = _carparks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** GPS position — drives the user dot and initial camera fly-to. */
    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    /** Current map viewport center — drives the 600 m radius ring and carpark fetch. */
    private val _mapCenter = MutableStateFlow<Pair<Double, Double>?>(null)
    val mapCenter: StateFlow<Pair<Double, Double>?> = _mapCenter.asStateFlow()

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
    }

    fun onLocationPermissionDenied() {
        fetchNearbyAt(DEFAULT_LAT, DEFAULT_LON)
    }

    /** Called whenever the map camera comes to rest. */
    fun onMapCenterChanged(lat: Double, lon: Double) {
        fetchNearbyAt(lat, lon)
    }

    private fun fetchNearbyAt(lat: Double, lon: Double) {
        _mapCenter.value = Pair(lat, lon)
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _carparks.value = getNearbyCarparks(lat, lon)
            } catch (e: Exception) {
                _error.value = "Could not load carparks"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
