package com.gymbro.local.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Nutrition returned by Open Food Facts is normalized per 100 g. */
data class BarcodeProduct(
    val barcode: String = "",
    val name: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
) {
    fun validationError(): String? =
        if (
            caloriesPer100g.isFinite() && caloriesPer100g in 0.0..900.0 &&
            proteinPer100g.isFinite() && proteinPer100g in 0.0..100.0
        ) null else INVALID_NUTRITION

    companion object {
        const val INVALID_NUTRITION = "This product has invalid nutrition per 100 g."
    }
}

class BarcodeLookupException(message: String, cause: Throwable? = null) : IOException(message, cause)

object BarcodeValidation {
    fun error(value: String): String? =
        if (value.length in 8..14 && value.all(Char::isDigit)) null
        else "The scanned barcode is not a supported product code."
}

@Serializable private data class OffResponse(val status: Int = 0, val product: OffProduct? = null)
@Serializable private data class OffProduct(
    @SerialName("product_name") val name: String = "",
    val nutriments: OffNutriments = OffNutriments(),
)
@Serializable private data class OffNutriments(
    @SerialName("energy-kcal_100g") val calories: Double? = null,
    @SerialName("proteins_100g") val protein: Double? = null,
)

class OpenFoodFactsParser(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun parse(body: String): BarcodeProduct {
        val response = try { json.decodeFromString<OffResponse>(body) }
        catch (e: SerializationException) { throw BarcodeLookupException("Open Food Facts returned an unreadable response.", e) }
        catch (e: IllegalArgumentException) { throw BarcodeLookupException("Open Food Facts returned an unreadable response.", e) }
        if (response.status != 1 || response.product == null) {
            throw BarcodeLookupException("No Open Food Facts product was found for this barcode.")
        }
        val product = response.product
        val calories = product.nutriments.calories
        val protein = product.nutriments.protein
        if (product.name.isBlank() || calories == null || protein == null) {
            throw BarcodeLookupException("This product is missing calories or protein per 100 g.")
        }
        val parsed = BarcodeProduct(name = product.name, caloriesPer100g = calories, proteinPer100g = protein)
        parsed.validationError()?.let { throw BarcodeLookupException(it) }
        return parsed
    }
}

fun interface BarcodeProductResolver { suspend fun resolve(barcode: String): BarcodeProduct }

class HttpOpenFoodFactsResolver(
    private val parser: OpenFoodFactsParser = OpenFoodFactsParser(),
    private val connectionFactory: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) : BarcodeProductResolver {
    override suspend fun resolve(barcode: String): BarcodeProduct {
        BarcodeValidation.error(barcode)?.let { throw BarcodeLookupException(it) }
        val url = "$ENDPOINT/$barcode.json?fields=product_name,nutriments"
        val connection = connectionFactory(url)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "GymBro-Android/${com.gymbro.local.BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status !in 200..299) throw BarcodeLookupException("Product lookup failed (HTTP $status).")
            parser.parse(connection.inputStream.bufferedReader().use { it.readText() }).copy(barcode = barcode)
        } catch (e: BarcodeLookupException) {
            throw e
        } catch (e: IOException) {
            throw BarcodeLookupException("Could not reach Open Food Facts. Check your connection.", e)
        } finally {
            connection.disconnect()
        }
    }

    companion object { const val ENDPOINT = "https://world.openfoodfacts.org/api/v2/product" }
}
