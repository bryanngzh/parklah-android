package com.bdev.parklah.feature.home

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bdev.parklah.core.model.AvailabilityStatus
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.ui.theme.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.tooling.preview.Preview
import com.bdev.parklah.ui.theme.ParklahTheme
import kotlin.math.*

private const val MAP_STYLE        = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val SOURCE_ID        = "carparks-source"
private const val LAYER_ID         = "carparks-layer"
private const val USER_SOURCE_ID   = "user-location-source"
private const val USER_LAYER_ID    = "user-location-layer"
private const val RADIUS_SOURCE_ID = "radius-source"
private const val RADIUS_FILL_ID   = "radius-fill-layer"
private const val RADIUS_LINE_ID   = "radius-line-layer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val carparks by viewModel.carparks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.onLocationPermissionGranted()
        else viewModel.onLocationPermissionDenied()
    }

    // Unit doesn't re-run this onViewCreated()
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    val sheetState = rememberBottomSheetScaffoldState()
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    // Update carpark pins whenever the list changes
    LaunchedEffect(carparks) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val source = map.style?.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return@LaunchedEffect
        val features = carparks.map { cp ->
            Feature.fromGeometry(Point.fromLngLat(cp.lon, cp.lat)).also { f ->
                f.addStringProperty("code", cp.carparkCode)
                f.addNumberProperty("available", cp.lotsAvailable)
                f.addNumberProperty("total", cp.totalLots)
            }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // GPS dot + camera fly-to — only when user location is first known or re-fetched
    LaunchedEffect(userLocation, mapLibreMap) {
        val loc = userLocation ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 15.0),
            800,
        )
        map.style?.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
            ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.second, loc.first)))
    }

    // Radius ring follows the map viewport center, not the GPS position
    LaunchedEffect(mapCenter, mapLibreMap) {
        val center = mapCenter ?: return@LaunchedEffect
        val style = mapLibreMap?.style ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(RADIUS_SOURCE_ID)
            ?.setGeoJson(Feature.fromGeometry(createRadiusPolygon(center.first, center.second)))
    }

    // Register camera idle listener — fires whenever the map comes to rest after a pan/zoom/animation
    LaunchedEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.addOnCameraIdleListener {
            val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
            viewModel.onMapCenterChanged(target.latitude, target.longitude)
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 220.dp,
        containerColor = NightBg,
        sheetContainerColor = NightSurface,
        sheetDragHandle = { DragHandle() },
        sheetContent = {
            NearbySheetContent(carparks = carparks, isLoading = isLoading)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ParkLahMapView(
                modifier = Modifier.fillMaxSize(),
                onMapReady = { mapLibreMap = it },
            )

            // Re-centre FAB
            FloatingActionButton(
                onClick = {
                    val loc = userLocation
                    if (loc != null) {
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 15.0),
                            800,
                        )
                    } else {
                        viewModel.onLocationPermissionGranted()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 100.dp, end = 16.dp)
                    .size(44.dp),
                containerColor = NightSurface,
                contentColor = NightPrimary,
                elevation = FloatingActionButtonDefaults.elevation(2.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "My location", modifier = Modifier.size(20.dp))
            }

            // Top overlay: search bar + filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchBarButton()
                FilterChipsRow()
            }
        }
    }
}

@Composable
private fun ParkLahMapView(
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(MAP_STYLE) { style ->
                    // Radius ring (bottommost)
                    style.addSource(GeoJsonSource(RADIUS_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(
                        FillLayer(RADIUS_FILL_ID, RADIUS_SOURCE_ID).withProperties(
                            fillColor("#5EE7D6"),
                            fillOpacity(0.12f),
                        )
                    )
                    style.addLayer(
                        LineLayer(RADIUS_LINE_ID, RADIUS_SOURCE_ID).withProperties(
                            lineColor("#5EE7D6"),
                            lineOpacity(0.5f),
                            lineWidth(1.5f),
                        )
                    )

                    // Carpark pins
                    style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(
                        CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
                            circleRadius(9f),
                            circleColor("#7DF0A6"),
                            circleStrokeWidth(2f),
                            circleStrokeColor("#0B0F1A"),
                        )
                    )

                    // User location dot (topmost)
                    style.addSource(GeoJsonSource(USER_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(
                        CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                            circleRadius(8f),
                            circleColor("#5EE7D6"),
                            circleStrokeWidth(3f),
                            circleStrokeColor("#0B0F1A"),
                        )
                    )
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(1.3521, 103.8198))
                    .zoom(14.0)
                    .build()
                onMapReady(map)
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun previewCarpark(
    code: String,
    name: String,
    available: Int,
    total: Int,
    distanceM: Int,
): Carpark {
    val fraction = if (total > 0) available.toFloat() / total else 0f
    val status = when {
        fraction > 0.3f -> AvailabilityStatus.GOOD
        fraction > 0.1f -> AvailabilityStatus.LOW
        else            -> AvailabilityStatus.FULL
    }
    val distance = if (distanceM >= 1000) "%.1f km".format(distanceM / 1000f) else "$distanceM m"
    return Carpark(
        carparkCode = code, carparkName = name, dataSource = "hdb",
        lat = 1.39, lon = 103.89, distanceM = distanceM, parkingSystem = "COUPON",
        lotsAvailable = available, totalLots = total, snapshotTime = "",
        availabilityFraction = fraction, availabilityStatus = status, formattedDistance = distance,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun CarparkListItemGoodPreview() {
    ParklahTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarparkListItem(previewCarpark("SK1", "SENGKANG WEST AVE 1", 87, 120, 240))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun CarparkListItemLowPreview() {
    ParklahTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarparkListItem(previewCarpark("BT8", "BUKIT TIMAH PLAZA", 12, 100, 1340))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A)
@Composable
private fun CarparkListItemFullPreview() {
    ParklahTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarparkListItem(previewCarpark("TP5", "TAMPINES CENTRAL", 0, 80, 580))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A, heightDp = 500)
@Composable
private fun NearbySheetLoadingPreview() {
    ParklahTheme {
        NearbySheetContent(carparks = emptyList(), isLoading = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F1A, heightDp = 600)
@Composable
private fun NearbySheetListPreview() {
    ParklahTheme {
        NearbySheetContent(
            isLoading = false,
            carparks = listOf(
                previewCarpark("SK1", "SENGKANG WEST AVE 1", 87, 120, 240),
                previewCarpark("BT8", "BUKIT TIMAH SHOPPING CENTRE", 12, 100, 580),
                previewCarpark("TP5", "TAMPINES CENTRAL 5", 0, 80, 1340),
            ),
        )
    }
}

// ── Map helpers ──────────────────────────────────────────────────────────────

private fun createRadiusPolygon(lat: Double, lon: Double, radiusMeters: Double = 600.0): Polygon {
    val latRad = lat * PI / 180.0
    val coords = (0 until 64).map { i ->
        val angle = 2.0 * PI * i / 64
        val dLat = (radiusMeters * cos(angle)) / 111_320.0
        val dLon = (radiusMeters * sin(angle)) / (111_320.0 * cos(latRad))
        Point.fromLngLat(lon + dLon, lat + dLat)
    }.toMutableList().also { it.add(it[0]) }
    return Polygon.fromLngLats(listOf(coords))
}

@Composable
private fun SearchBarButton() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(24.dp),
        color = NightSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.FilterList, contentDescription = null, tint = NightInkDim, modifier = Modifier.size(20.dp))
            Text(
                text = "Where to?",
                color = NightInkFaint,
                fontFamily = GeistFontFamily,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun FilterChipsRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("Available", fontFamily = GeistFontFamily, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NightPrimary,
                    selectedLabelColor = NightOnPrimary,
                ),
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = {},
                label = { Text("Sheltered", fontFamily = GeistFontFamily, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Umbrella, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = NightSurface,
                    labelColor = NightInk,
                ),
            )
        }
        item {
            FilterChip(
                selected = false,
                onClick = {},
                label = { Text("EV", fontFamily = GeistFontFamily, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.ElectricBolt, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = NightSurface,
                    labelColor = NightInk,
                ),
            )
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(NightBorder)
        )
    }
}

@Composable
private fun NearbySheetContent(
    carparks: List<Carpark>,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (carparks.isEmpty()) "Nearby" else "Nearby · ${carparks.size}",
                color = NightInk,
                fontFamily = GeistFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (carparks.isNotEmpty()) NightGood else NightInkFaint)
                )
                Text(
                    text = if (carparks.isNotEmpty()) "Live" else "—",
                    color = if (carparks.isNotEmpty()) NightGood else NightInkFaint,
                    fontFamily = GeistMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = NightPrimary, modifier = Modifier.size(24.dp))
            }
        } else {
            carparks.forEach { carpark ->
                CarparkListItem(carpark = carpark)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CarparkListItem(carpark: Carpark) {
    val statusColor = when (carpark.availabilityStatus) {
        AvailabilityStatus.GOOD -> NightGood
        AvailabilityStatus.LOW  -> NightPrimary
        AvailabilityStatus.FULL -> NightWarn
    }
    val statusSoft = when (carpark.availabilityStatus) {
        AvailabilityStatus.GOOD -> NightGoodSoft
        AvailabilityStatus.LOW  -> NightAccentSoft
        AvailabilityStatus.FULL -> NightWarnSoft
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NightSurfaceAlt,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    // Code badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NightSurface,
                    ) {
                        Text(
                            text = carpark.carparkCode,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = NightInkDim,
                            fontFamily = GeistMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = carpark.carparkName.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            color = NightInk,
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(carpark.formattedDistance, color = NightInkDim, fontFamily = GeistFontFamily, fontSize = 12.sp)
                            Text("·", color = NightInkFaint, fontSize = 12.sp)
                            Text(carpark.dataSource.uppercase(), color = NightInkDim, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                }

                // Availability badge
                Surface(shape = RoundedCornerShape(6.dp), color = statusSoft) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${carpark.lotsAvailable}",
                            color = statusColor,
                            fontFamily = GeistMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "/ ${carpark.totalLots}",
                            color = statusColor.copy(alpha = 0.6f),
                            fontFamily = GeistMonoFontFamily,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Availability bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NightSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(carpark.availabilityFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(statusColor)
                )
            }
        }
    }
}
