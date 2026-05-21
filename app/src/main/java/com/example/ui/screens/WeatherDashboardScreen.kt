package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SavedCity
import com.example.data.model.CurrentWeather
import com.example.data.model.DailyWeather
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.example.ui.components.*
import java.util.Locale
import com.example.ui.viewmodel.SearchUiState
import com.example.ui.viewmodel.SelectedLocation
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import com.google.accompanist.permissions.*
import com.google.android.gms.location.LocationServices

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WeatherDashboardScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val selectedLocation by viewModel.selectedLocation.collectAsStateWithLifecycle()
    val weatherUiState by viewModel.weatherUiState.collectAsStateWithLifecycle()
    val searchUiState by viewModel.searchUiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLocationSaved by viewModel.currentLocationIsSaved.collectAsStateWithLifecycle()
    val savedCities by viewModel.savedCities.collectAsStateWithLifecycle()

    // Setup GPS permissions using Accompanist
    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var locationErrorMsg by remember { mutableStateOf<String?>(null) }

    @SuppressLint("MissingPermission")
    fun requestLocationWeather() {
        if (locationPermissionState.allPermissionsGranted) {
            locationErrorMsg = null
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.fetchWeatherForCoordinates(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            label = "Current Location"
                        )
                    } else {
                        // If last location is null, trigger a fresh location request or fallback
                        locationErrorMsg = "GPS is active but returned null coordinates. Try again."
                    }
                }
                .addOnFailureListener {
                    locationErrorMsg = "Unable to fetch GPS. Ensure location services are active."
                }
        } else {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    // Get styling attributes dynamically matching WMO code
    val currentWmoCode = when (val state = weatherUiState) {
        is WeatherUiState.Success -> state.response.current.weatherCode
        else -> 0
    }
    val isDayTime = when (val state = weatherUiState) {
        is WeatherUiState.Success -> state.response.current.isDay == 1
        else -> true
    }
    val weatherTheme = WeatherConditionHelper.getDetails(currentWmoCode, isDayTime)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(weatherTheme.bgGradient))
    ) {
        // High fidelity custom canvas rendering animated rain/snow/sun glow particles
        WeatherAtmosphereEffect(
            type = weatherTheme.animationType,
            isDay = isDayTime,
            modifier = Modifier.fillMaxSize()
        )

        // Main content column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Search header block
            Spacer(modifier = Modifier.height(12.dp))
            SearchAndHeaderArea(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onSearchTrigger = { viewModel.performSearch(it) },
                onGpsClick = { requestLocationWeather() },
                searchUiState = searchUiState,
                onResultClick = { result ->
                    viewModel.selectLocation(
                        SelectedLocation(
                            id = result.id,
                            name = result.name,
                            latitude = result.latitude,
                            longitude = result.longitude,
                            country = result.country,
                            admin1 = result.admin1
                        )
                    )
                    focusManager.clearFocus()
                },
                containerColor = weatherTheme.containerColor,
                textColorPrimary = weatherTheme.textColorPrimary,
                textColorSecondary = weatherTheme.textColorSecondary
            )

            // Dynamic list of saved favorite cities
            if (savedCities.isNotEmpty() && searchUiState is SearchUiState.Idle) {
                SavedCitiesHorizontalTray(
                    cities = savedCities,
                    currentSelectedId = selectedLocation.id,
                    onCityClick = { viewModel.selectLocationFromSaved(it) },
                    onDelete = { viewModel.toggleLocationSaved() }, // If deleting current, toggle or trigger delete via ID
                    containerColor = weatherTheme.containerColor,
                    highlightColor = weatherTheme.highlightColor,
                    textColorPrimary = weatherTheme.textColorPrimary,
                    textColorSecondary = weatherTheme.textColorSecondary
                )
            }

            // Small permission error feedback
            locationErrorMsg?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Main display state switcher
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxWidth()
            ) {
                when (val state = weatherUiState) {
                    is WeatherUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Gathering atmospheric data...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    is WeatherUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = "Error icon",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Meteorological Connection Offline",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.fetchWeather(selectedLocation.latitude, selectedLocation.longitude) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f))
                            ) {
                                Text("Retry Connection", color = Color.White)
                            }
                        }
                    }
                    is WeatherUiState.Success -> {
                        DetailedWeatherDashboard(
                            response = state.response,
                            location = selectedLocation,
                            isFavorite = isLocationSaved,
                            onFavoriteToggle = { viewModel.toggleLocationSaved() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchAndHeaderArea(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchTrigger: (String) -> Unit,
    onGpsClick: () -> Unit,
    searchUiState: SearchUiState,
    onResultClick: (GeocodingResult) -> Unit,
    containerColor: Color,
    textColorPrimary: Color,
    textColorSecondary: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Elegant search card wrapper
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search locations",
                        tint = textColorSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = {
                            Text(
                                "Search city name...",
                                color = textColorSecondary.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = textColorPrimary,
                            unfocusedTextColor = textColorPrimary,
                            cursorColor = textColorPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_input")
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = textColorSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Current coordinates GPS location trigger
            IconButton(
                onClick = onGpsClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(containerColor, CircleShape)
                    .testTag("gps_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Get GPS weather",
                    tint = textColorPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Animated geocoding search results panel
        AnimatedVisibility(
            visible = searchUiState !is SearchUiState.Idle,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                ),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                when (searchUiState) {
                    is SearchUiState.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = textColorPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                    is SearchUiState.Error -> {
                        Text(
                            text = searchUiState.message,
                            color = Color(0xFFBA1A1A),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    is SearchUiState.Idle -> {}
                    is SearchUiState.Success -> {
                        if (searchUiState.results.isEmpty()) {
                            Text(
                                text = "No cities matched your query.",
                                color = textColorSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                            ) {
                                items(searchUiState.results) { city ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onResultClick(city) }
                                            .padding(horizontal = 18.dp, vertical = 14.dp)
                                            .testTag("city_search_result"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = "Location Pin",
                                            tint = textColorSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = city.name,
                                                color = textColorPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val details = listOfNotNull(
                                                city.admin1,
                                                city.country
                                            ).joinToString(", ")
                                            if (details.isNotEmpty()) {
                                                Text(
                                                    text = details,
                                                    color = textColorSecondary.copy(alpha = 0.8f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", city.latitude)}°, ${String.format(Locale.US, "%.1f", city.longitude)}°",
                                            color = textColorSecondary.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    HorizontalDivider(color = textColorSecondary.copy(alpha = 0.12f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedCitiesHorizontalTray(
    cities: List<SavedCity>,
    currentSelectedId: Long?,
    onCityClick: (SavedCity) -> Unit,
    onDelete: (SavedCity) -> Unit,
    containerColor: Color,
    highlightColor: Color,
    textColorPrimary: Color,
    textColorSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cities.forEach { city ->
            val isCurrent = city.id == currentSelectedId
            val chipColor = if (isCurrent) {
                highlightColor
            } else {
                containerColor
            }

            Surface(
                color = chipColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable { onCityClick(city) }
                    .testTag("saved_city_chip_${city.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isCurrent) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isCurrent) Color(0xFFBA1A1A) else textColorSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = city.name,
                        color = textColorPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun DetailedWeatherDashboard(
    response: WeatherResponse,
    location: SelectedLocation,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit
) {
    val current = response.current
    val hourly = response.hourly
    val daily = response.daily

    val weatherTheme = WeatherConditionHelper.getDetails(current.weatherCode, current.isDay == 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Large Current Temp and Condition info block
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = location.name,
                        color = weatherTheme.textColorPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light, // Sleek lightweight heading
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val locDetails = listOfNotNull(location.admin1, location.country).joinToString(", ")
                    if (locDetails.isNotEmpty()) {
                        Text(
                            text = locDetails,
                            color = weatherTheme.textColorSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bookmark/favorite selection toggler
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .background(weatherTheme.containerColor, CircleShape)
                        .testTag("bookmark_toggle")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Bookmark city",
                        tint = if (isFavorite) Color(0xFFBA1A1A) else weatherTheme.textColorPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Massive Dynamic Vector Icon
            Icon(
                imageVector = weatherTheme.icon,
                contentDescription = weatherTheme.description,
                tint = weatherTheme.accentColor,
                modifier = Modifier
                    .size(110.dp)
                    .testTag("main_weather_icon")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Celsius Gauge Temp
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = current.temperature.toInt().toString(),
                    color = weatherTheme.textColorPrimary,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    modifier = Modifier.testTag("main_temperature_text")
                )
                Text(
                    text = "°C",
                    color = weatherTheme.textColorSecondary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp, start = 2.dp)
                )
            }

            Text(
                text = weatherTheme.description,
                color = weatherTheme.textColorPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("weather_description_text")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feels like ${current.apparentTemperature.toInt()}°",
                    color = weatherTheme.textColorSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                val dailyMax0 = daily.tempMax.firstOrNull()?.toInt() ?: 0
                val dailyMin0 = daily.tempMin.firstOrNull()?.toInt() ?: 0
                Text(
                    text = "H: $dailyMax0°  L: $dailyMin0°",
                    color = weatherTheme.textColorSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Hourly Forecast Horizontal Section
        Text(
            text = "Hourly Forecast",
            color = weatherTheme.textColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Take the next 24 hours of predictions
            val limit = minOf(hourly.time.size, 24)
            items(List(limit) { it }) { index ->
                val timeStr = formatIsoTimeToHour(hourly.time[index])
                val temp = hourly.temperature[index].toInt()
                val code = hourly.weatherCode[index]
                val hourIcon = WeatherConditionHelper.getDetails(code).icon
                val hourIconTint = WeatherConditionHelper.getDetails(code).accentColor

                val isNow = index == 0
                val hourlyCardBg = if (isNow) weatherTheme.highlightColor else weatherTheme.containerColor

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = hourlyCardBg
                    ),
                    shape = RoundedCornerShape(24.dp), // 3xl round corners
                    modifier = Modifier
                        .width(76.dp)
                        .testTag("hourly_tile_$index")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (index == 0) "Now" else timeStr,
                            color = weatherTheme.textColorSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Icon(
                            imageVector = hourIcon,
                            contentDescription = null,
                            tint = hourIconTint,
                            modifier = Modifier.size(22.dp)
                        )
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = temp.toString(),
                                color = weatherTheme.textColorPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "°",
                                color = weatherTheme.textColorPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2x2 Grid Highlights
        Text(
            text = "Meteorological Highlights",
            color = weatherTheme.textColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        WeatherDetailCardsGrid(
            current = current,
            containerColor = weatherTheme.containerColor,
            textColorPrimary = weatherTheme.textColorPrimary,
            textColorSecondary = weatherTheme.textColorSecondary,
            accentColor = weatherTheme.accentColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 7-day Forecast Section
        Text(
            text = "7-Day Forecast",
            color = weatherTheme.textColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = weatherTheme.containerColor
            ),
            shape = RoundedCornerShape(24.dp), // 3xl corners
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val absMin = daily.tempMin.minOrNull() ?: 0.0
                val absMax = daily.tempMax.maxOrNull() ?: 100.0

                daily.time.forEachIndexed { idx, time ->
                    val dayName = formatIsoToDayOfWeek(time)
                    val code = daily.weatherCode[idx]
                    val dayDetails = WeatherConditionHelper.getDetails(code)
                    val maxTemp = daily.tempMax[idx]
                    val minTemp = daily.tempMin[idx]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("daily_forecast_row_$idx"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Day name
                        Text(
                            text = dayName,
                            color = weatherTheme.textColorPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.1f)
                        )

                        // Condition icon
                        Box(
                            modifier = Modifier.weight(0.5f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = dayDetails.icon,
                                contentDescription = dayDetails.description,
                                tint = dayDetails.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Temp Min
                        Text(
                            text = "${minTemp.toInt()}°",
                            color = weatherTheme.textColorSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.End
                        )

                        // Thermometer dynamic custom span bar
                        TemperatureSpanBar(
                            minTemp = minTemp,
                            maxTemp = maxTemp,
                            absoluteMin = absMin,
                            absoluteMax = absMax,
                            trackColor = weatherTheme.highlightColor,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(6.dp)
                                .padding(horizontal = 8.dp)
                        )

                        // Temp Max
                        Text(
                            text = "${maxTemp.toInt()}°",
                            color = weatherTheme.textColorPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.End
                        )
                    }

                    if (idx < daily.time.lastIndex) {
                        HorizontalDivider(color = weatherTheme.highlightColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
