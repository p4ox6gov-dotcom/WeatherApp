package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.local.SavedCity
import com.example.data.local.WeatherDatabase
import com.example.data.model.GeocodingResult
import com.example.data.model.WeatherResponse
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val response: WeatherResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Searching : SearchUiState
    data class Success(val results: List<GeocodingResult>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

data class SelectedLocation(
    val id: Long?, // Null if resolved via GPS coordinate without geocoding hit
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?
)

@OptIn(FlowPreview::class)
class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {

    private val _selectedLocation = MutableStateFlow(DEFAULT_LOCATION)
    val selectedLocation: StateFlow<SelectedLocation> = _selectedLocation.asStateFlow()

    private val _weatherUiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherUiState: StateFlow<WeatherUiState> = _weatherUiState.asStateFlow()

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentLocationIsSaved = MutableStateFlow(false)
    val currentLocationIsSaved: StateFlow<Boolean> = _currentLocationIsSaved.asStateFlow()

    val savedCities: StateFlow<List<SavedCity>> = repository.savedCities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Automatically check if London (default) is in database or needs saving
        checkLocationSavedStatus(DEFAULT_LOCATION)
        // Load initial weather database values or default London weather
        fetchWeather(DEFAULT_LOCATION.latitude, DEFAULT_LOCATION.longitude)

        // Setup reactive instant searching with debounce for search queries
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .filter { it.trim().length >= 2 }
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchUiState.value = SearchUiState.Idle
        }
    }

    fun performSearch(query: String) {
        if (query.trim().isEmpty()) {
            _searchUiState.value = SearchUiState.Idle
            return
        }
        _searchUiState.value = SearchUiState.Searching
        viewModelScope.launch {
            repository.searchCities(query).fold(
                onSuccess = { response ->
                    val results = response.results ?: emptyList()
                    _searchUiState.value = SearchUiState.Success(results)
                },
                onFailure = { error ->
                    _searchUiState.value = SearchUiState.Error(error.localizedMessage ?: "Failed to find location")
                }
            )
        }
    }

    fun selectLocation(location: SelectedLocation) {
        _selectedLocation.value = location
        fetchWeather(location.latitude, location.longitude)
        checkLocationSavedStatus(location)
        // Clear search
        _searchQuery.value = ""
        _searchUiState.value = SearchUiState.Idle
    }

    fun selectLocationFromSaved(savedCity: SavedCity) {
        val location = SelectedLocation(
            id = savedCity.id,
            name = savedCity.name,
            latitude = savedCity.latitude,
            longitude = savedCity.longitude,
            country = savedCity.country,
            admin1 = savedCity.admin1
        )
        selectLocation(location)
    }

    fun fetchWeather(latitude: Double, longitude: Double) {
        _weatherUiState.value = WeatherUiState.Loading
        viewModelScope.launch {
            repository.getForecast(latitude, longitude).fold(
                onSuccess = { response ->
                    _weatherUiState.value = WeatherUiState.Success(response)
                },
                onFailure = { error ->
                    _weatherUiState.value = WeatherUiState.Error(error.localizedMessage ?: "Failed to connect to weather service")
                }
            )
        }
    }

    fun toggleLocationSaved() {
        val currentLoc = _selectedLocation.value
        val locId = currentLoc.id
        if (locId == null) {
            // Cannot bookmark a direct GPS coord without search id, let's create a deterministic custom hash ID
            val geoId = (currentLoc.latitude.toString() + currentLoc.longitude.toString()).hashCode().toLong()
            val newCity = SavedCity(
                id = geoId,
                name = currentLoc.name,
                latitude = currentLoc.latitude,
                longitude = currentLoc.longitude,
                country = currentLoc.country,
                admin1 = currentLoc.admin1
            )
            viewModelScope.launch {
                repository.saveCity(newCity)
                _currentLocationIsSaved.value = true
                _selectedLocation.value = currentLoc.copy(id = geoId)
            }
        } else {
            viewModelScope.launch {
                if (_currentLocationIsSaved.value) {
                    repository.deleteCityById(locId)
                    _currentLocationIsSaved.value = false
                } else {
                    val newCity = SavedCity(
                        id = locId,
                        name = currentLoc.name,
                        latitude = currentLoc.latitude,
                        longitude = currentLoc.longitude,
                        country = currentLoc.country,
                        admin1 = currentLoc.admin1
                    )
                    repository.saveCity(newCity)
                    _currentLocationIsSaved.value = true
                }
            }
        }
    }

    private fun checkLocationSavedStatus(location: SelectedLocation) {
        viewModelScope.launch {
            val locId = location.id
            if (locId == null) {
                // Try hashing coordinates to see if custom-saved
                val geoId = (location.latitude.toString() + location.longitude.toString()).hashCode().toLong()
                _currentLocationIsSaved.value = repository.isCitySaved(geoId)
            } else {
                _currentLocationIsSaved.value = repository.isCitySaved(locId)
            }
        }
    }

    fun fetchWeatherForCoordinates(latitude: Double, longitude: Double, label: String = "Current Location") {
        viewModelScope.launch {
            // Check if there is a city nearby or use standard coords
            val location = SelectedLocation(
                id = null, // GPS source initially
                name = label,
                latitude = latitude,
                longitude = longitude,
                country = "GPS Coordinates",
                admin1 = "Near you"
            )
            selectLocation(location)
        }
    }

    companion object {
        val DEFAULT_LOCATION = SelectedLocation(
            id = 2643743L,
            name = "London",
            latitude = 51.50853,
            longitude = -0.12574,
            country = "United Kingdom",
            admin1 = "England"
        )

        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = WeatherDatabase.getDatabase(context)
                val repo = WeatherRepository(
                    RetrofitClient.weatherApiService,
                    RetrofitClient.geocodingApiService,
                    db.savedCityDao()
                )
                return WeatherViewModel(repo) as T
            }
        }
    }
}
