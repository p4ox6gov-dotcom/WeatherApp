package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CurrentWeather
import com.example.data.model.DailyWeather
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

object WeatherConditionHelper {
    fun getDetails(code: Int, isDay: Boolean = true): WeatherConditionInfo {
        val bgGradient = if (isDay) {
            listOf(Color(0xFFE1E2E9), Color(0xFFDDE2F1))
        } else {
            listOf(Color(0xFF101214), Color(0xFF191C20))
        }

        val containerColor = if (isDay) Color(0xFFF1F0F7) else Color(0xFF222428)
        val textColorPrimary = if (isDay) Color(0xFF191C20) else Color(0xFFE1E2E9)
        val textColorSecondary = if (isDay) Color(0xFF44474E) else Color(0xFF90939A)
        val highlightColor = if (isDay) Color(0xFFDDE2F1) else Color(0xFF383A40)
        val baseAccentColor = if (isDay) Color(0xFF0061A4) else Color(0xFF82CFFF)

        val description: String
        val icon: ImageVector
        val animationType: WeatherAnimationType
        val accentColor: Color

        when (code) {
            0 -> {
                description = "Clear Sky"
                icon = Icons.Rounded.WbSunny
                animationType = WeatherAnimationType.SUNNY
                accentColor = if (isDay) Color(0xFFC77C00) else Color(0xFFFFD54F)
            }
            1, 2, 3 -> {
                description = when (code) {
                    1 -> "Mainly Clear"
                    2 -> "Partly Cloudy"
                    else -> "Overcast"
                }
                icon = Icons.Rounded.Cloud
                animationType = WeatherAnimationType.CLOUDY
                accentColor = baseAccentColor
            }
            45, 48 -> {
                description = "Foggy / Rime"
                icon = Icons.Rounded.Grain
                animationType = WeatherAnimationType.FOGGY
                accentColor = textColorSecondary
            }
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> {
                description = when (code) {
                    in 51..57 -> "Drizzle"
                    in 61..67 -> "Rain"
                    else -> "Rain Showers"
                }
                icon = Icons.Rounded.WaterDrop
                animationType = WeatherAnimationType.RAINY
                accentColor = baseAccentColor
            }
            71, 73, 75, 77, 85, 86 -> {
                description = "Snowfall"
                icon = Icons.Rounded.AcUnit
                animationType = WeatherAnimationType.SNOWY
                accentColor = if (isDay) Color(0xFF0061A4) else Color(0xFFE0F2FE)
            }
            95, 96, 99 -> {
                description = "Thunderstorm"
                icon = Icons.Rounded.FlashOn
                animationType = WeatherAnimationType.THUNDERSTORM
                accentColor = if (isDay) Color(0xFF8A6A00) else Color(0xFFFBE082)
            }
            else -> {
                description = "Mild"
                icon = Icons.Rounded.Thermostat
                animationType = WeatherAnimationType.CLOUDY
                accentColor = baseAccentColor
            }
        }

        return WeatherConditionInfo(
            description = description,
            icon = icon,
            bgGradient = bgGradient,
            accentColor = accentColor,
            animationType = animationType,
            containerColor = containerColor,
            textColorPrimary = textColorPrimary,
            textColorSecondary = textColorSecondary,
            highlightColor = highlightColor
        )
    }
}

enum class WeatherAnimationType {
    SUNNY, CLOUDY, RAINY, SNOWY, THUNDERSTORM, FOGGY
}

data class WeatherConditionInfo(
    val description: String,
    val icon: ImageVector,
    val bgGradient: List<Color>,
    val accentColor: Color,
    val animationType: WeatherAnimationType,
    val containerColor: Color,
    val textColorPrimary: Color,
    val textColorSecondary: Color,
    val highlightColor: Color
)

@Composable
fun WeatherAtmosphereEffect(type: WeatherAnimationType, isDay: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "weather_anim_transition")

    when (type) {
        WeatherAnimationType.SUNNY -> {
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sunny_glow"
            )

            Canvas(modifier = modifier) {
                // Draw warm radial sun flares in upper corner
                val centerOffset = Offset(size.width * 0.85f, size.height * 0.15f)
                val baseRadius = size.width * 0.35f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFEA79).copy(alpha = 0.35f),
                            Color(0xFFF59E0B).copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = baseRadius * glowScale
                    ),
                    center = centerOffset,
                    radius = baseRadius * glowScale
                )
            }
        }
        WeatherAnimationType.RAINY -> {
            val driftOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rain_fall"
            )

            Canvas(modifier = modifier) {
                // Draw raining lines drifting diagonally
                val rainCount = 45
                val width = size.width
                val height = size.height

                for (i in 0 until rainCount) {
                    // Unique predictable x offset for stability
                    val xBase = (width / rainCount) * i
                    val speedFactor = 1f + (i % 3) * 0.4f
                    val yPos = ((driftOffset * speedFactor) + (i * 107)) % height
                    val xPos = (xBase - (yPos * 0.15f)) % width

                    drawLine(
                        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                        start = Offset(xPos, yPos),
                        end = Offset(xPos - 4f, yPos + 32f),
                        strokeWidth = 2.5f
                    )
                }
            }
        }
        WeatherAnimationType.SNOWY -> {
            val angleState by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2f * Math.PI.toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(6000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snow_wave"
            )

            val fallOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 600f,
                animationSpec = infiniteRepeatable(
                    animation = tween(7000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "snow_fall"
            )

            Canvas(modifier = modifier) {
                val snowCount = 28
                val width = size.width
                val height = size.height

                for (i in 0 until snowCount) {
                    val xBase = (width / snowCount) * i
                    val speedFactor = 0.6f + (i % 4) * 0.2f
                    val yPos = ((fallOffset * speedFactor) + (i * 89)) % height
                    val wobble = sin(angleState + i) * 20f
                    val xPos = (xBase + wobble) % width

                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f),
                        radius = 4.5f + (i % 3) * 1.5f,
                        center = Offset(xPos, yPos)
                    )
                }
            }
        }
        WeatherAnimationType.CLOUDY, WeatherAnimationType.FOGGY -> {
            val driftOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "cloud_drift"
            )

            Canvas(modifier = modifier) {
                val width = size.width
                val height = size.height
                val centerOffset = Offset((driftOffset + width * 0.2f) % width, height * 0.25f)

                // Soft background atmospheric cloud humps
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = width * 0.28f,
                    center = centerOffset
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = width * 0.35f,
                    center = Offset((centerOffset.x + width * 0.35f) % width, height * 0.22f)
                )
            }
        }
        WeatherAnimationType.THUNDERSTORM -> {
            val flashTrigger by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 6000
                        0.0f at 0
                        0.0f at 4800
                        1.0f at 4850
                        0.0f at 4900
                        1.0f at 4930
                        0.0f at 5000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "lightning_flash"
            )

            val rainOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 900f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "storm_rain"
            )

            Canvas(modifier = modifier) {
                val width = size.width
                val height = size.height

                // Draw raining background
                val rainCount = 40
                for (i in 0 until rainCount) {
                    val xBase = (width / rainCount) * i
                    val yPos = ((rainOffset * 1.3f) + (i * 123)) % height
                    val xPos = (xBase - (yPos * 0.2f)) % width

                    drawLine(
                        color = Color(0xFF60A5FA).copy(alpha = 0.3f),
                        start = Offset(xPos, yPos),
                        end = Offset(xPos - 5f, yPos + 24f),
                        strokeWidth = 2f
                    )
                }

                // Thunder lightning overlay
                if (flashTrigger > 0.5f) {
                    drawRect(
                        color = Color(0xFFFBE082).copy(alpha = 0.15f * flashTrigger),
                        size = size
                    )

                    // Draw a majestic stylized lightning bolt down the sky
                    val boltStartX = width * 0.65f
                    val boltStartY = height * 0.08f
                    val pathPoints = listOf(
                        Offset(boltStartX, boltStartY),
                        Offset(boltStartX - 40f, boltStartY + 120f),
                        Offset(boltStartX + 10f, boltStartY + 100f),
                        Offset(boltStartX - 30f, boltStartY + 240f)
                    )

                    for (j in 0 until pathPoints.size - 1) {
                        drawLine(
                            color = Color(0xFFFEF08A),
                            start = pathPoints[j],
                            end = pathPoints[j + 1],
                            strokeWidth = 5f
                        )
                        // Add glow
                        drawLine(
                            color = Color(0xFFF59E0B).copy(alpha = 0.5f),
                            start = pathPoints[j],
                            end = pathPoints[j + 1],
                            strokeWidth = 12f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItemTile(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    iconColor: Color,
    containerColor: Color,
    textColorPrimary: Color,
    textColorSecondary: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(24.dp), // 3xl corners
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    color = textColorSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    color = textColorPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light, // Lightweight Display Font style
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    color = textColorSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun TemperatureSpanBar(
    minTemp: Double,
    maxTemp: Double,
    absoluteMin: Double,
    absoluteMax: Double,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = height / 2f

        // Draw background tracks for thermal span
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(radius)
        )

        // Calculate offset percentages
        val range = absoluteMax - absoluteMin
        if (range > 0) {
            val startPercent = ((minTemp - absoluteMin) / range).toFloat().coerceIn(0f, 1f)
            val endPercent = ((maxTemp - absoluteMin) / range).toFloat().coerceIn(0f, 1f)

            val startX = startPercent * width
            val endX = endPercent * width

            // Draw active temperature span with gradient (cool blue to warm orange)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF0061A4), Color(0xFFFFB300))
                ),
                topLeft = Offset(startX, 0f),
                size = Size(maxOf(radius, endX - startX), height),
                cornerRadius = CornerRadius(radius)
            )
        }
    }
}

@Composable
fun WeatherDetailCardsGrid(
    current: CurrentWeather,
    containerColor: Color,
    textColorPrimary: Color,
    textColorSecondary: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricItemTile(
                label = "Humidity",
                value = current.humidity.toInt().toString(),
                unit = "%",
                icon = Icons.Rounded.WaterDrop,
                iconColor = accentColor,
                containerColor = containerColor,
                textColorPrimary = textColorPrimary,
                textColorSecondary = textColorSecondary,
                modifier = Modifier.weight(1f)
            )
            MetricItemTile(
                label = "Wind Speed",
                value = "${current.windSpeed.toInt()}",
                unit = "km/h",
                icon = Icons.Rounded.Air,
                iconColor = accentColor,
                containerColor = containerColor,
                textColorPrimary = textColorPrimary,
                textColorSecondary = textColorSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricItemTile(
                label = "UV Index",
                value = current.uvIndex.toInt().toString(),
                unit = "",
                icon = Icons.Rounded.WbSunny,
                iconColor = accentColor,
                containerColor = containerColor,
                textColorPrimary = textColorPrimary,
                textColorSecondary = textColorSecondary,
                modifier = Modifier.weight(1f)
            )
            MetricItemTile(
                label = "Air Pressure",
                value = current.pressure.toInt().toString(),
                unit = "hPa",
                icon = Icons.Rounded.Compress,
                iconColor = accentColor,
                containerColor = containerColor,
                textColorPrimary = textColorPrimary,
                textColorSecondary = textColorSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun formatIsoTimeToHour(isoTime: String): String {
    return try {
        // Sample isoTime: "2026-05-20T22:00"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = inputFormat.parse(isoTime) ?: return "--"
        val outputFormat = SimpleDateFormat("h a", Locale.getDefault())
        outputFormat.format(date)
    } catch (e: Exception) {
        "--"
    }
}

fun formatIsoToDayOfWeek(isoTime: String): String {
    return try {
        // Sample isoTime: "2026-05-20"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(isoTime) ?: return "--"
        val outputFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val today = Calendar.getInstance().apply { time = Date() }
        val checkDay = Calendar.getInstance().apply { time = date }

        if (today.get(Calendar.DAY_OF_YEAR) == checkDay.get(Calendar.DAY_OF_YEAR) &&
            today.get(Calendar.YEAR) == checkDay.get(Calendar.YEAR)) {
            "Today"
        } else {
            outputFormat.format(date)
        }
    } catch (e: Exception) {
        "--"
    }
}
