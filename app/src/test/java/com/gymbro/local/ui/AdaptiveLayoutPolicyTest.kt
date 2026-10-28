package com.gymbro.local.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutPolicyTest {
    @Test fun `compact phone uses single pane bottom navigation`() {
        val mode = AdaptiveLayoutPolicy.forWidth(599)
        assertEquals(NavigationMode.BOTTOM, mode.navigation)
        assertFalse(mode.twoPane)
    }

    @Test fun `medium window uses rail without stretching content`() {
        val mode = AdaptiveLayoutPolicy.forWidth(700)
        assertEquals(NavigationMode.RAIL, mode.navigation)
        assertFalse(mode.twoPane)
        assertEquals(720, mode.maxContentWidthDp)
    }

    @Test fun `single pane windows cap main content width`() {
        assertEquals(720, AdaptiveLayoutPolicy.forWidth(599).maxContentWidthDp)
        assertEquals(720, AdaptiveLayoutPolicy.forWidth(839).maxContentWidthDp)
    }

    @Test fun `expanded and foldable-sized windows use supporting pane without a main content cap`() {
        val mode = AdaptiveLayoutPolicy.forWidth(1000)
        assertEquals(NavigationMode.RAIL, mode.navigation)
        assertTrue(mode.twoPane)
        assertNull(mode.maxContentWidthDp)
    }
}
