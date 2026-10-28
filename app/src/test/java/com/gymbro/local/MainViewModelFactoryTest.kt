package com.gymbro.local

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelFactoryTest {
    @Test
    fun exposesApplicationOnlyConstructorForAndroidViewModelFactory() {
        val hasApplicationConstructor = MainViewModel::class.java.constructors.any { constructor ->
            constructor.parameterTypes.contentEquals(arrayOf(Application::class.java))
        }

        assertTrue("AndroidViewModelFactory requires an (Application) constructor", hasApplicationConstructor)
    }
}
