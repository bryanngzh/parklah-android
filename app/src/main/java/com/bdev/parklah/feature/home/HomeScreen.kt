package com.bdev.parklah.feature.home

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.bdev.parklah.core.model.AvailabilityStatus
import com.bdev.parklah.core.model.Carpark
import com.bdev.parklah.core.model.CarparkRatesDto
import com.bdev.parklah.ui.theme.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression as Exp
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.*

private const val MAP_STYLE        = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
private const val SOURCE_ID        = "carparks-source"
private const val LAYER_ID         = "carparks-layer"
private const val USER_SOURCE_ID   = "user-location-source"
private const val USER_LAYER_ID    = "user-location-layer"
private const val RADIUS_SOURCE_ID = "radius-source"
private const val RADIUS_FILL_ID   = "radius-fill-layer"
private const val RADIUS_LINE_ID   = "radius-line-layer"
private const val BUBBLE_GOOD      = "bubble-good"    // green
private const val BUBBLE_BAD       = "bubble-bad"     // red (low + full)
private const val BUBBLE_UNKNOWN   = "bubble-unknown" // blue

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val carparks        by viewModel.carparks.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val userLocation    by viewModel.userLocation.collectAsState()
    val mapCenter       by viewModel.mapCenter.collectAsState()
    val expandedCarpark by viewModel.expandedCarpark.collectAsState()

    val context = LocalContext.current
    val density = context.resources.displayMetrics.density

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.onLocationPermissionGranted()
        else viewModel.onLocationPermissionDenied()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshNearby()
        }
    }

    var mapLibreMap    by remember { mutableStateOf<MapLibreMap?>(null) }
    var hasFlownToUser by remember { mutableStateOf(false) }
    val selectedTabState = remember { mutableIntStateOf(0) }
    var selectedTab by selectedTabState

    // Fly to user on first GPS fix only
    LaunchedEffect(userLocation, mapLibreMap) {
        if (hasFlownToUser) return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 15.0), 800)
        hasFlownToUser = true
    }

    // Sync carpark pins + user dot
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(carparks, userLocation, mapLibreMap) }
            .collect { (cps, loc, map) ->
                val m = map ?: return@collect
                m.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)?.let { src ->
                    val features = cps.map { cp ->
                        Feature.fromGeometry(Point.fromLngLat(cp.lon, cp.lat)).also { f ->
                            f.addStringProperty("code", cp.carparkCode)
                            f.addNumberProperty("available", cp.lotsAvailable)
                            f.addNumberProperty("total", cp.totalLots)
                            f.addNumberProperty("fraction", cp.availabilityFraction)
                        }
                    }
                    src.setGeoJson(FeatureCollection.fromFeatures(features))
                }
                if (loc != null) {
                    m.style?.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
                        ?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.second, loc.first)))
                }
            }
    }

    // Radius ring
    LaunchedEffect(Unit) {
        snapshotFlow { Pair(mapCenter, mapLibreMap) }
            .collect { (center, map) ->
                val c = center ?: return@collect
                val m = map ?: return@collect
                m.style?.getSourceAs<GeoJsonSource>(RADIUS_SOURCE_ID)
                    ?.setGeoJson(Feature.fromGeometry(radiusPolygon(c.first, c.second)))
            }
    }

    // Camera idle listener + bubble tap listener
    LaunchedEffect(mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.addOnCameraIdleListener {
            val target = map.cameraPosition.target ?: return@addOnCameraIdleListener
            viewModel.onMapCenterChanged(target.latitude, target.longitude)
        }
        map.addOnMapClickListener { latLng ->
            val screenPt = map.projection.toScreenLocation(latLng)
            val r = 40f * density
            val touchBox = RectF(screenPt.x - r, screenPt.y - r * 1.3f, screenPt.x + r, screenPt.y + r * 0.3f)
            val features = map.queryRenderedFeatures(touchBox, LAYER_ID)
            if (features.isNotEmpty()) {
                val code = features[0].getStringProperty("code")
                if (code != null) {
                    viewModel.onCarparkCodeTapped(code)
                    selectedTabState.intValue = 0
                }
                true
            } else {
                false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Map — top half
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            ParkLahMapView(
                modifier = Modifier.fillMaxSize(),
                onMapReady = { mapLibreMap = it },
            )

            Text(
                text = "ParkLah!",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                color = NightPrimary,
                fontFamily = GeistFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.5).sp,
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NightSurface)
                    .clickable {
                        val loc = userLocation
                        if (loc != null) {
                            mapLibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.first, loc.second), 15.0),
                                600,
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "My location",
                    tint = NightPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Sheet — fixed bottom half
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(NightSurface),
        ) {
            // Drag handle visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(NightBorder),
                )
            }

            SheetContent(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                carparks = carparks,
                isLoading = isLoading,
                expandedCarpark = expandedCarpark,
                onCarparkToggled = viewModel::onCarparkToggled,
                onVehicleSelected = viewModel::onVehicleSelected,
            )
        }
    }
}

// ── Map view ──────────────────────────────────────────────────────────────────

@Composable
private fun ParkLahMapView(modifier: Modifier = Modifier, onMapReady: (MapLibreMap) -> Unit) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dm        = context.resources.displayMetrics.density

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(MAP_STYLE) { style ->
                    // Bubble marker icons: green / red / blue
                    style.addImage(BUBBLE_GOOD,    bubbleBitmap(dm, 0xFF7DF0A6.toInt()))
                    style.addImage(BUBBLE_BAD,     bubbleBitmap(dm, 0xFFFF7D7D.toInt()))
                    style.addImage(BUBBLE_UNKNOWN, bubbleBitmap(dm, 0xFF64B5F6.toInt()))

                    // Radius ring
                    style.addSource(GeoJsonSource(RADIUS_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(FillLayer(RADIUS_FILL_ID, RADIUS_SOURCE_ID).withProperties(
                        fillColor("#5EE7D6"), fillOpacity(0.10f),
                    ))
                    style.addLayer(LineLayer(RADIUS_LINE_ID, RADIUS_SOURCE_ID).withProperties(
                        lineColor("#5EE7D6"), lineOpacity(0.5f), lineWidth(1.5f),
                    ))

                    // Carpark bubble markers with carpark code text
                    style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(
                        SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
                            iconImage(
                                Exp.switchCase(
                                    Exp.gte(Exp.get("fraction"), Exp.literal(0.3f)), Exp.literal(BUBBLE_GOOD),
                                    Exp.gt(Exp.get("total"),     Exp.literal(0)),    Exp.literal(BUBBLE_BAD),
                                    Exp.literal(BUBBLE_UNKNOWN),
                                )
                            ),
                            iconAnchor("bottom"),
                            iconAllowOverlap(true),
                            textField(Exp.get("code")),
                            textColor("#0B0F1A"),
                            textFont(arrayOf("Open Sans Bold", "Arial Unicode MS Bold")),
                            textSize(9f),
                            textAnchor("center"),
                            textOffset(arrayOf(0f, -2.9f)),
                            textAllowOverlap(true),
                        )
                    )

                    // User location dot
                    style.addSource(GeoJsonSource(USER_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
                    style.addLayer(CircleLayer(USER_LAYER_ID, USER_SOURCE_ID).withProperties(
                        circleRadius(8f),
                        circleColor("#5EE7D6"),
                        circleStrokeWidth(3f),
                        circleStrokeColor("#0B0F1A"),
                    ))

                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(1.3521, 103.8198))
                        .zoom(14.0)
                        .build()
                    onMapReady(map)
                }
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
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

// ── Sheet content ─────────────────────────────────────────────────────────────

@Composable
private fun SheetContent(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    carparks: List<Carpark>,
    isLoading: Boolean,
    expandedCarpark: ExpandedCarparkUiState,
    onCarparkToggled: (Carpark) -> Unit,
    onVehicleSelected: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SheetTabRow(selectedTab, onTabSelected)
        when (selectedTab) {
            0 -> MapTab(carparks, isLoading, expandedCarpark, onCarparkToggled, onVehicleSelected)
            1 -> SavedTab()
            2 -> SettingsTab()
        }
    }
}

@Composable
private fun SheetTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple(Icons.Filled.Map,      Icons.Filled.Map,      "Map"),
        Triple(Icons.Filled.Star,     Icons.Default.Star,    "Saved"),
        Triple(Icons.Filled.Settings, Icons.Filled.Settings, "Settings"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NightSurface)
            .padding(horizontal = 8.dp),
    ) {
        tabs.forEachIndexed { i, (_, icon, label) ->
            val active = selectedTab == i
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(i) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) NightPrimary else NightInkFaint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    color = if (active) NightInk else NightInkFaint,
                    fontFamily = GeistFontFamily,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 12.sp,
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(NightPrimary),
                    )
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
    HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
}

@Composable
private fun MapTab(
    carparks: List<Carpark>,
    isLoading: Boolean,
    expandedCarpark: ExpandedCarparkUiState,
    onCarparkToggled: (Carpark) -> Unit,
    onVehicleSelected: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll to the expanded carpark when it changes
    LaunchedEffect(expandedCarpark) {
        val targetCode = when (expandedCarpark) {
            is ExpandedCarparkUiState.Loading -> expandedCarpark.carpark.carparkCode
            is ExpandedCarparkUiState.Success -> expandedCarpark.carpark.carparkCode
            else -> return@LaunchedEffect
        }
        val index = carparks.indexOfFirst { it.carparkCode == targetCode }
        if (index >= 0) {
            listState.animateScrollToItem(index + 1) // +1 for header item
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (carparks.isEmpty()) "Nearby carparks" else "${carparks.size} carparks nearby",
                    color = NightInkDim,
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (isLoading) {
                    CircularProgressIndicator(color = NightPrimary, modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                } else if (carparks.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(NightGood))
                        Text("Live", color = NightGood, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
        }

        if (carparks.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No carparks nearby", color = NightInkFaint, fontFamily = GeistFontFamily, fontSize = 14.sp)
                }
            }
        } else {
            items(carparks, key = { it.carparkCode }) { carpark ->
                CarparkItem(
                    carpark = carpark,
                    expandedCarpark = expandedCarpark,
                    onToggle = onCarparkToggled,
                    onVehicleSelected = onVehicleSelected,
                )
            }
        }
    }
}

@Composable
private fun CarparkItem(
    carpark: Carpark,
    expandedCarpark: ExpandedCarparkUiState,
    onToggle: (Carpark) -> Unit,
    onVehicleSelected: (String) -> Unit,
) {
    val isLoadingThis = expandedCarpark is ExpandedCarparkUiState.Loading &&
            expandedCarpark.carpark.carparkCode == carpark.carparkCode
    val successState = (expandedCarpark as? ExpandedCarparkUiState.Success)
        ?.takeIf { it.carpark.carparkCode == carpark.carparkCode }
    val isExpanded = isLoadingThis || successState != null

    val statusColor = when (carpark.availabilityStatus) {
        AvailabilityStatus.GOOD    -> NightGood
        AvailabilityStatus.LOW     -> NightWarn
        AvailabilityStatus.FULL    -> NightWarn
        AvailabilityStatus.UNKNOWN -> NightBlue
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(carpark) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = carpark.carparkCode.take(4),
                    color = statusColor,
                    fontFamily = GeistMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = carpark.carparkName.lowercase().split(" ")
                        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
                    color = NightInk,
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(carpark.dataSource.uppercase(), color = NightInkDim, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    if (carpark.parkingSystem != null) {
                        Text("·", color = NightInkFaint, fontSize = 10.sp)
                        Text(
                            text = carpark.parkingSystem.lowercase().replaceFirstChar(Char::uppercase),
                            color = NightInkDim,
                            fontFamily = GeistFontFamily,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            if (carpark.availabilityStatus != AvailabilityStatus.UNKNOWN) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${carpark.lotsAvailable}",
                            color = statusColor,
                            fontFamily = GeistMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            text = "/${carpark.totalLots}",
                            color = statusColor.copy(alpha = 0.55f),
                            fontFamily = GeistMonoFontFamily,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text("LOTS", color = NightInkFaint, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 0.3.sp)
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = NightInkFaint,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                when {
                    isLoadingThis -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = NightPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    successState != null -> {
                        InlineRates(
                            rates = successState.rates,
                            selectedVehicle = successState.selectedVehicle,
                            carpark = carpark,
                            onVehicleSelected = onVehicleSelected,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
    }
}

@Composable
private fun InlineRates(
    rates: CarparkRatesDto,
    selectedVehicle: String,
    carpark: Carpark,
    onVehicleSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val vehicleTypes = rates.shortTerm.map { it.vehicleType }.distinct()
    val shortTermForVehicle = rates.shortTerm.filter { it.vehicleType == selectedVehicle }
    val seasonForVehicle = rates.season.filter { it.vehicleType == selectedVehicle }
    val currentRate = shortTermForVehicle.firstOrNull { it.isCurrent }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Vehicle selector
        if (vehicleTypes.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(NightSurfaceAlt)
                    .padding(4.dp),
            ) {
                vehicleTypes.forEach { type ->
                    val on = selectedVehicle == type
                    val (icon, label) = when (type) {
                        "C"  -> Icons.Filled.DirectionsCar to "Cars"
                        "M"  -> Icons.Filled.TwoWheeler  to "Motorcycles"
                        else -> Icons.Filled.LocalParking to "Heavy"
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) NightSurface else Color.Transparent)
                            .clickable { onVehicleSelected(type) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, tint = if (on) NightPrimary else NightInkDim, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(label, color = if (on) NightInk else NightInkDim, fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Current rate
        Surface(shape = RoundedCornerShape(12.dp), color = NightAccentSoft) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("CURRENT RATE", color = NightPrimary, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        text = if (currentRate != null) "${currentRate.startTime} – ${currentRate.endTime}" else "No active rate",
                        color = NightInk, fontFamily = GeistFontFamily, fontSize = 13.sp,
                    )
                }
                if (currentRate != null) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (currentRate.ratePerHalfHour == 0.0) "Free" else "$${String.format("%.2f", currentRate.ratePerHalfHour)}",
                            color = if (currentRate.ratePerHalfHour == 0.0) NightGood else NightInk,
                            fontFamily = GeistMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.8).sp,
                        )
                        if (currentRate.ratePerHalfHour > 0.0) {
                            Text("per 30 min", color = NightInkDim, fontFamily = GeistFontFamily, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text("—", color = NightInkDim, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
        }

        // Weekly schedule
        if (shortTermForVehicle.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weekly schedule", color = NightInk, fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Surface(shape = RoundedCornerShape(12.dp), color = NightSurfaceAlt, border = BorderStroke(1.dp, NightBorder)) {
                    Column {
                        val order = listOf("weekday", "saturday", "sunday_ph", "all")
                        val grouped = shortTermForVehicle.groupBy { it.dayType }
                        val sortedGroups = order.mapNotNull { k -> grouped[k]?.let { k to it } } +
                                grouped.filter { it.key !in order }.entries.map { it.key to it.value }

                        sortedGroups.forEachIndexed { gi, (dayType, rows) ->
                            rows.forEachIndexed { ri, row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (row.isCurrent) NightAccentSoft else Color.Transparent)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (ri == 0) dayTypeLabel(dayType) else "",
                                        color = NightInk,
                                        fontFamily = GeistFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(72.dp),
                                    )
                                    Text(
                                        text = "${row.startTime} – ${row.endTime}",
                                        color = NightInkDim,
                                        fontFamily = GeistMonoFontFamily,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (row.ratePerHalfHour == 0.0) {
                                        Text("Free", color = NightGood, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Text(
                                            text = "$${String.format("%.2f", row.ratePerHalfHour)}",
                                            color = if (row.isCurrent) NightPrimary else NightInk,
                                            fontFamily = GeistMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                        )
                                        Text(" /30m", color = NightInkDim, fontFamily = GeistFontFamily, fontSize = 11.sp)
                                    }
                                }
                                val isLastRow = gi == sortedGroups.lastIndex && ri == rows.lastIndex
                                if (!isLastRow) HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // Season rates
        if (seasonForVehicle.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Season parking", color = NightInk, fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Surface(shape = RoundedCornerShape(12.dp), color = NightSurfaceAlt, border = BorderStroke(1.dp, NightBorder)) {
                    Column {
                        seasonForVehicle.forEachIndexed { i, rate ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(rate.ticketType, color = NightInk, fontFamily = GeistFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(rate.parkingHrs, color = NightInkDim, fontFamily = GeistMonoFontFamily, fontSize = 11.sp)
                                }
                                Text("$${String.format("%.0f", rate.monthlyRate)}", color = NightInk, fontFamily = GeistMonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("/mo", color = NightInkDim, fontFamily = GeistFontFamily, fontSize = 11.sp)
                            }
                            if (i < seasonForVehicle.lastIndex) HorizontalDivider(color = NightBorder, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Save + Navigate buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NightBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NightInkDim),
            ) {
                Icon(Icons.Filled.Star, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Button(
                onClick = {
                    val lat = carpark.lat
                    val lon = carpark.lon
                    val name = Uri.encode(carpark.carparkName)
                    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($name)")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NightPrimary, contentColor = NightOnPrimary),
            ) {
                Icon(Icons.Filled.Navigation, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Navigate", fontFamily = GeistFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

// ── Placeholder tabs ──────────────────────────────────────────────────────────

@Composable
private fun SavedTab() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Star, null, tint = NightInkFaint, modifier = Modifier.size(32.dp))
            Text("No saved carparks yet", color = NightInkFaint, fontFamily = GeistFontFamily, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsTab() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Settings, null, tint = NightInkFaint, modifier = Modifier.size(32.dp))
            Text("Settings coming soon", color = NightInkFaint, fontFamily = GeistFontFamily, fontSize = 14.sp)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun bubbleBitmap(dm: Float, color: Int): Bitmap {
    val r    = (18 * dm).toInt()
    val tail = (8 * dm).toInt()
    val pad  = (2 * dm).toInt()
    val w    = r * 2 + pad * 2
    val h    = r * 2 + tail + pad
    val bmp  = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c    = AndroidCanvas(bmp)
    val p    = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx   = w / 2f
    val cy   = r.toFloat() + pad

    p.color = color
    p.style = Paint.Style.FILL
    c.drawCircle(cx, cy, r.toFloat(), p)

    val path = Path()
    path.moveTo(cx - 6 * dm, cy + r - 3 * dm)
    path.lineTo(cx + 6 * dm, cy + r - 3 * dm)
    path.lineTo(cx, h.toFloat())
    path.close()
    c.drawPath(path, p)

    return bmp
}

private fun dayTypeLabel(dayType: String) = when (dayType) {
    "weekday"   -> "Mon – Fri"
    "saturday"  -> "Sat"
    "sunday_ph" -> "Sun & PH"
    "all"       -> "All days"
    else        -> dayType.replaceFirstChar(Char::uppercase)
}

private fun radiusPolygon(lat: Double, lon: Double, radiusM: Double = 600.0): Polygon {
    val latRad = lat * PI / 180.0
    val coords = (0 until 64).map { i ->
        val angle = 2.0 * PI * i / 64
        Point.fromLngLat(
            lon + (radiusM * sin(angle)) / (111_320.0 * cos(latRad)),
            lat + (radiusM * cos(angle)) / 111_320.0,
        )
    }.toMutableList().also { it.add(it[0]) }
    return Polygon.fromLngLats(listOf(coords))
}
