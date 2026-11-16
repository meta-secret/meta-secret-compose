package ui.liveboard.visual

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.design.generated.LiveboardStyles
import ui.design.generated.LiveboardTokens as T

/** The exported cascade is resolved only for the named Liveboard component selectors. */
data class LiveboardViewport(val width: Float, val height: Float, val creatorWidth: Float = width) {
    val compact get() = width <= T.liveboardDimensionBreakpointCompact.value
    val wide get() = width >= T.liveboardDimensionBreakpointWide.value
    val expanded get() = width >= T.liveboardDimensionBreakpointExpanded.value
    val short get() = height < T.liveboardDimensionShortHeight.value
}

val LocalLiveboardViewport = staticCompositionLocalOf { LiveboardViewport(DEFAULT_VIEWPORT_WIDTH,
    DEFAULT_VIEWPORT_HEIGHT) }

object LiveboardStyleBindings {
    fun properties(selector: String, viewport: LiveboardViewport): Map<String, String> = buildMap {
        LiveboardStyles.rules.forEach { rule ->
            if (rule.selector.split(',').any { it.trim() == selector } && matches(rule.condition, viewport, selector
                .contains(":hover"))) {
                putAll(rule.declarations)
            }
        }
    }

    fun dimension(selector: String, property: String, viewport: LiveboardViewport, fallback: Dp): Dp =
        properties(selector, viewport)[property]?.removeSuffix("px")?.toFloatOrNull()?.dp ?: fallback

    fun color(selector: String, property: String, viewport: LiveboardViewport, fallback: Color): Color =
        Regex("#[0-9a-fA-F]{3,8}\\b").find(properties(selector, viewport)[property].orEmpty())?.value?.let {
            liveboardColor(it, fallback) } ?: fallback

    private fun matches(condition: String, viewport: LiveboardViewport, pointer: Boolean): Boolean {
        if (condition.isEmpty()) return true
        if (condition.contains("hover") && !pointer || condition.contains("prefers-reduced-motion")) return false
        val width = if (condition.contains("creator")) viewport.creatorWidth else viewport.width
        return Regex("(min|max)-(width|height):\\s*(\\d+)px").findAll(condition).all { match ->
            val actual = if (match.groupValues[2] == "height") viewport.height else width
            val limit = match.groupValues[CSS_LIMIT_GROUP_INDEX].toFloat()
            if (match.groupValues[1] == "min") actual >= limit else actual <= limit
        }
    }
}

data class LiveboardInsets(val top: Dp, val right: Dp, val bottom: Dp, val left: Dp)

fun liveboardInsets(selector: String, viewport: LiveboardViewport, fallback: LiveboardInsets): LiveboardInsets {
    val text = LiveboardStyleBindings.properties(selector, viewport)["padding"] ?: return fallback
    val values = text.split(' ').map { it.removeSuffix("px").toFloatOrNull()?.dp ?: return fallback }
    return when (values.size) {
        1 -> LiveboardInsets(values[0], values[0], values[0], values[0])
        2 -> LiveboardInsets(values[0], values[1], values[0], values[1])
        INSET_COMPONENT_COUNT -> LiveboardInsets(values[0], values[1], values[2], values[LAST_INSET_INDEX])
        else -> fallback
    }
}

@Composable
fun liveboardTilePadding(): PaddingValues {
    val padding = liveboardInsets(".tile", LocalLiveboardViewport.current, LiveboardInsets(T
        .liveboardDimensionTilePadY, T.liveboardDimensionTilePadX, T.liveboardDimensionTilePadY, T
        .liveboardDimensionTilePadX))
    return PaddingValues(start = padding.left, top = padding.top, end = padding.right, bottom = padding.bottom)
}

/** SVG/CSS export colors use trailing alpha; Compose's packed colors use leading alpha. */
fun liveboardColor(value: String, fallback: Color = T.liveboardColorInk): Color {
    val text = value.removePrefix("#")
    return when (text.length) {
        SHORT_COLOR_DIGITS -> runCatching { Color(("FF" + text.flatMap { listOf(it, it) }.joinToString("")).toLong(
            HEX_RADIX)) }.getOrDefault(fallback)
        SHORT_RGBA_HEX_DIGITS -> liveboardColor("#" + text.flatMap { listOf(it, it) }.joinToString(""), fallback)
        RGB_HEX_DIGITS -> runCatching { Color(("FF$text").toLong(HEX_RADIX)) }.getOrDefault(fallback)
        RGBA_HEX_DIGITS -> runCatching { Color((text.takeLast(2) + text.take(RGB_HEX_DIGITS)).toLong(HEX_RADIX)) }
            .getOrDefault(fallback)
        else -> fallback
    }
}

private const val DEFAULT_VIEWPORT_WIDTH: Float = 390f
private const val DEFAULT_VIEWPORT_HEIGHT: Float = 844f
private const val CSS_LIMIT_GROUP_INDEX: Int = 3
private const val INSET_COMPONENT_COUNT: Int = 4
private const val LAST_INSET_INDEX: Int = 3
private const val SHORT_COLOR_DIGITS: Int = 3
private const val HEX_RADIX: Int = 16
private const val SHORT_RGBA_HEX_DIGITS: Int = 4
private const val RGB_HEX_DIGITS: Int = 6
private const val RGBA_HEX_DIGITS: Int = 8
