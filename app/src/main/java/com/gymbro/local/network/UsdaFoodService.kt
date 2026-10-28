package com.gymbro.local.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class UsdaFood(
    val fdcId: Long,
    val description: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
)

class FoodSearchException(message: String, cause: Throwable? = null) : IOException(message, cause)

object FoodSearchValidation {
    fun queryError(query: String): String? = when {
        query.trim().length < 2 -> "Enter at least 2 characters."
        query.trim().length > 100 -> "Keep the search under 100 characters."
        else -> null
    }
    fun gramsError(grams: String): String? =
        if (grams.toIntOrNull() in 1..5000) null else "Enter 1–5000 g."
}

@Serializable private data class SearchResponse(val foods: List<ApiFood> = emptyList())
@Serializable private data class ApiFood(
    val fdcId: Long,
    val description: String,
    val foodNutrients: List<ApiNutrient> = emptyList(),
)
@Serializable private data class ApiNutrient(
    val nutrientId: Int? = null,
    val nutrientName: String = "",
    val unitName: String = "",
    val value: Double? = null,
)

class UsdaFoodParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(body: String): List<UsdaFood> = try {
        json.decodeFromString<SearchResponse>(body).foods.mapNotNull { food ->
            val kcal = food.foodNutrients.firstOrNull {
                (it.nutrientId == 1008 || it.nutrientName.equals("Energy", true)) && it.unitName.equals("KCAL", true)
            }?.value
            val protein = food.foodNutrients.firstOrNull {
                it.nutrientId == 1003 || it.nutrientName.equals("Protein", true)
            }?.value
            if (kcal == null || protein == null) null
            else UsdaFood(food.fdcId, food.description, kcal, protein)
        }
    } catch (e: SerializationException) {
        throw FoodSearchException("USDA returned an unreadable response.", e)
    } catch (e: IllegalArgumentException) {
        throw FoodSearchException("USDA returned an unreadable response.", e)
    }
}

interface FoodSearchService { suspend fun search(query: String, apiKey: String): List<UsdaFood> }

class HttpUsdaFoodSearchService(
    private val parser: UsdaFoodParser = UsdaFoodParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) : FoodSearchService {
    override suspend fun search(query: String, apiKey: String): List<UsdaFood> {
        FoodSearchValidation.queryError(query)?.let { throw FoodSearchException(it) }
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = "$SEARCH_ENDPOINT?query=$encoded&pageSize=25&api_key=${URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())}"
        val connection = connectionFactory(url)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw FoodSearchException(
                if (status == 429) "USDA rate limit reached. Add a personal key in Settings or try later."
                else "USDA search failed (HTTP $status)."
            )
            parser.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: FoodSearchException) {
            throw e
        } catch (e: IOException) {
            throw FoodSearchException("Could not reach USDA. Check your connection.", e)
        } finally {
            connection.disconnect()
        }
    }

    companion object { const val SEARCH_ENDPOINT = "https://api.nal.usda.gov/fdc/v1/foods/search" }
}