package com.gymbro.local.update

import androidx.core.content.FileProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class GymBroFileProviderTest {
    @Test
    fun usesDedicatedNonExportedFileProviderClass() {
        assertEquals(FileProvider::class.java, GymBroFileProvider::class.java.superclass)
    }
}
