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

    @Test fun barcodeValidationRejectsNonNumericScannerContent() {
        assertEquals(null, BarcodeValidation.error("0123456789012"))
        assertEquals("The scanned barcode is not a supported product code.", BarcodeValidation.error("https://example.com"))
    }
}
