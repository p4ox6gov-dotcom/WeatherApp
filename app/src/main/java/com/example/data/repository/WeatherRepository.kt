package com.example.data.repository

import com.example.data.api.GeocodingApiService
import com.example.data.api.WeatherApiService
import com.example.data.local.SavedCity
import com.example.data.local.SavedCityDao
import com.example.data.model.GeocodingResponse
import com.example.data.model.WeatherResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val weatherApi: WeatherApiService,
    private val geocodingApi: GeocodingApiService,
    private val savedCityDao: SavedCityDao
) {
    val savedCities: Flow<List<SavedCity>> = savedCityDao.getAllSavedCities()

    suspend fun getForecast(latitude: Double, longitude: Double): Result<WeatherResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = weatherApi.getForecast(latitude, longitude)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun searchCities(query: String): Result<GeocodingResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = geocodingApi.searchCities(query)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun saveCity(city: SavedCity) {
        withContext(Dispatchers.IO) {
            savedCityDao.insertCity(city)
        }
    }

    suspend fun deleteCity(city: SavedCity) {
        withContext(Dispatchers.IO) {
            savedCityDao.deleteCity(city)
        }
    }

    suspend fun deleteCityById(id: Long) {
        withContext(Dispatchers.IO) {
            savedCityDao.deleteCityById(id)
        }
    }

    fun isCitySavedFlow(id: Long): Flow<Boolean> = savedCityDao.isCitySavedFlow(id)

    suspend fun isCitySaved(id: Long): Boolean = withContext(Dispatchers.IO) {
        savedCityDao.isCitySaved(id)
    }
}
