package com.gymbro.local.ui

enum class NavigationMode { BOTTOM, RAIL }
data class AdaptiveLayoutMode(
    val navigation: NavigationMode,
    val twoPane: Boolean,
    val maxContentWidthDp: Int?,
)

object AdaptiveLayoutPolicy {
    fun forWidth(widthDp: Int) = AdaptiveLayoutMode(
        navigation = if (widthDp < 600) NavigationMode.BOTTOM else NavigationMode.RAIL,
        twoPane = widthDp >= 840,
        maxContentWidthDp = if (widthDp < 840) 720 else null,
    )
}
