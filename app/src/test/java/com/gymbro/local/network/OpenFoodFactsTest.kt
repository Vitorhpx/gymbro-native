package com.gymbro.local.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenFoodFactsTest {
    @Test fun parserReadsProductPer100gNutrition() {
        val product = OpenFoodFactsParser().parse("""{"status":1,"product":{"product_name":"Greek yogurt","nutriments":{"energy-kcal_100g":59.4,"proteins_100g":10.25}}}""")

        assertEquals("Greek yogurt", product.name)
        assertEquals(59.4, product.caloriesPer100g, 0.001)
        assertEquals(10.25, product.proteinPer100g, 0.001)
    }

    @Test fun parserExplainsMissingOrIncompleteProducts() {
        val missing = assertThrows(BarcodeLookupException::class.java) { OpenFoodFactsParser().parse("""{"status":0}""") }
        assertEquals("No Open Food Facts product was found for this barcode.", missing.message)
        val incomplete = assertThrows(BarcodeLookupException::class.java) { OpenFoodFactsParser().parse("""{"status":1,"product":{"product_name":"Mystery"}}""") }
        assertEquals("This product is missing calories or protein per 100 g.", incomplete.message)
    }

    @Test fun parserRejectsNutritionOutsidePhysicalPer100gLimits() {
        listOf(
            """{"status":1,"product":{"product_name":"Negative","nutriments":{"energy-kcal_100g":-1,"proteins_100g":10}}}""",
            """{"status":1,"product":{"product_name":"Too many calories","nutriments":{"energy-kcal_100g":901,"proteins_100g":10}}}""",
            """{"status":1,"product":{"product_name":"Too much protein","nutriments":{"energy-kcal_100g":100,"proteins_100g":101}}}""",
        ).forEach { body ->
            val error = assertThrows(BarcodeLookupException::class.java) { OpenFoodFactsParser().parse(body) }
            assertEquals("This product has invalid nutrition per 100 g.", error.message)
        }
        assertThrows(BarcodeLookupException::class.java) {
            OpenFoodFactsParser().parse("""{"status":1,"product":{"product_name":"Overflow","nutriments":{"energy-kcal_100g":1e999,"proteins_100g":10}}}""")
        }
    }

    @Test fun barcodeProductValidationRejectsNonFiniteNutritionBeforePersistence() {
        assertEquals(
            "This product has invalid nutrition per 100 g.",
            BarcodeProduct(name = "Invalid", caloriesPer100g = Double.NaN, proteinPer100g = 10.0).validationError(),
        )
    }

    @Test fun barcodeValidationRejectsNonNumericScannerContent() {
        assertEquals(null, BarcodeValidation.error("0123456789012"))
        assertEquals("The scanned barcode is not a supported product code.", BarcodeValidation.error("https://example.com"))
    }
}
