package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_cities ORDER BY addedAt DESC")
    fun getAllSavedCities(): Flow<List<SavedCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: SavedCity)

    @Delete
    suspend fun deleteCity(city: SavedCity)

    @Query("DELETE FROM saved_cities WHERE id = :id")
    suspend fun deleteCityById(id: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_cities WHERE id = :id)")
    fun isCitySavedFlow(id: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_cities WHERE id = :id)")
    suspend fun isCitySaved(id: Long): Boolean
}
