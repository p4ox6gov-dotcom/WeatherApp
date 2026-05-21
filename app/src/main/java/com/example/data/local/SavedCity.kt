package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_cities")
data class SavedCity(
    @PrimaryKey val id: Long, // Use the unique ID from Open-Meteo geocoding to prevent duplicates
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val admin1: String?, // State/Province
    val addedAt: Long = System.currentTimeMillis()
)
