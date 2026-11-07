package ui.liveboard.visual

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import ui.design.generated.LiveboardMaterial as MaterialSpec
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardTokens as T

data class LiveboardSurfaceStyle(
    val material: MaterialSpec = LiveboardMaterials.tile,
    val radius: Dp = T.liveboardDimensionTileRadius,
    val border: Color = T.liveboardColorTileBorder,
    val highlight: Boolean = true,
    val edges: LiveboardSurfaceEdges = LiveboardSurfaceEdges(),
)
data class LiveboardSurfaceEdges(val right: Color = T.liveboardColorTileBorderRight, val bottom: Color = T
    .liveboardColorTileBorderBottom,
    val drawBottom: Boolean = true, val topOnly: Boolean = false)

/** Preserve each ordered inset/outset shadow, including the solid seven-dp bevel. */
@Composable
fun LiveboardSurface(
    modifier: Modifier = Modifier,
    style: LiveboardSurfaceStyle = LiveboardSurfaceStyle(),
    interaction: LiveboardSurfaceInteraction? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val radius = if (style.radius == T.liveboardDimensionTileRadius) LiveboardStyleBindings.dimension(".tile",
        "border-radius", LocalLiveboardViewport.current, style.radius) else style.radius
    val shape = if (style.edges.topOnly) RoundedCornerShape(topStart = radius, topEnd = radius
        ) else RoundedCornerShape(radius)
    val state = interaction?.let { rememberLiveboardInteraction(it) }
    val surface = when {
        interaction?.kind == LiveboardInteractionKind.Plus && state?.pressed == true -> liveboardPlusPressed(
            LocalLiveboardViewport.current)
        style.material !== LiveboardMaterials.tile -> style.material
        state?.pressed == true -> LiveboardMaterials.tileActive
        interaction?.selected == true -> LiveboardMaterials.tileSelected
        state?.hovered == true -> LiveboardMaterials.tileHover
        else -> style.material
    }
    val border = when {
        state?.focused == true -> T.liveboardColorNeutralAction
        interaction?.kind == LiveboardInteractionKind.Plus && state?.hovered == true -> LiveboardStyleBindings
            .color(".add-device-trigger:hover", "border-color", LocalLiveboardViewport.current, style.border)
        interaction?.selected == true && style.material === LiveboardMaterials.tile -> LiveboardStyleBindings.color(
            ".tile.is-selected", "border-color", LocalLiveboardViewport.current, style.border)
        state?.hovered == true && style.material === LiveboardMaterials.tile -> LiveboardStyleBindings.color(
            ".tile:hover", "border-color", LocalLiveboardViewport.current, style.border)
        else -> style.border
    }
    var material = if (state == null) modifier else modifier.liveboardInteract(state)
    surface.shadows.filterNot { it.inset }.asReversed().forEach { shadow ->
        material = material.dropShadow(shape, Shadow(radius = shadow.blur.dp, spread = shadow.spread.dp,
            color = shadow.color, offset = DpOffset(shadow.x.dp, shadow.y.dp)))
    }
    material = material.drawWithCache {
        val brush = liveboardGradient(if (surface.stops.isEmpty()) style.material else surface, size)
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, brush) }
    }
    surface.shadows.filter { it.inset }.asReversed().forEach { shadow ->
        material = material.innerShadow(shape, Shadow(radius = shadow.blur.dp, spread = shadow.spread.dp,
            color = shadow.color, offset = DpOffset(shadow.x.dp, shadow.y.dp)))
    }
    material = if (style.edges.drawBottom) material.border(1.dp, border, shape) else material.drawWithCache {
        val r = radius.toPx().coerceAtMost(size.width / 2)
        val path = Path().apply {
            moveTo(.5.dp.toPx(), size.height); lineTo(.5.dp.toPx(), r)
            arcTo(Rect(0f, 0f, r * 2, r * 2), HALF_TURN_DEGREES, QUARTER_TURN_DEGREES, false)
            lineTo(size.width - r, .5.dp.toPx())
            arcTo(Rect(size.width - r * 2, 0f, size.width, r * 2), THREE_QUARTER_TURN_DEGREES, QUARTER_TURN_DEGREES,
                false)
            lineTo(size.width - .5.dp.toPx(), size.height)
        }
        onDrawWithContent { drawContent(); drawPath(path, border, style = Stroke(1.dp.toPx())) }
    }
    if (style.highlight) material = material.drawWithCache {
        val highlight = liveboardGradient(LiveboardMaterials.tileHighlight, size)
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, highlight) }
    }
    material = material.drawWithContent {
        drawContent()
        val corner = radius.toPx().coerceAtMost(minOf(size.width, size.height) / 2)
        drawLine(style.edges.right, Offset(size.width - .5.dp.toPx(), corner), Offset(size.width - .5.dp.toPx(),
            if (style.edges.topOnly) size.height else size.height - corner), 1.dp.toPx())
        if (style.edges.drawBottom) drawLine(style.edges.bottom, Offset(corner, size.height - .5.dp.toPx()), Offset(
            size.width - corner, size.height - .5.dp.toPx()), 1.dp.toPx())
    }
    Box(material, content = content)
}

fun liveboardGradient(material: MaterialSpec, size: Size, shift: Offset = Offset.Zero): Brush {
    val angle = (material.angle ?: LiveboardMaterials.tile.angle!!) * kotlin.math.PI.toFloat() / HALF_TURN_DEGREES
    val direction = Offset(sin(angle), -cos(angle))
    val extent = (abs(size.width * direction.x) + abs(size.height * direction.y)) / 2f
    val center = Offset(size.width / 2, size.height / 2) + shift
    return Brush.linearGradient(colorStops = material.stops.ifEmpty { LiveboardMaterials.tile.stops }.map { it
        .offset to it.color }.toTypedArray(),
        start = center - direction * extent, end = center + direction * extent)
}

private const val HALF_TURN_DEGREES: Float = 180f
private const val QUARTER_TURN_DEGREES: Float = 90f
private const val THREE_QUARTER_TURN_DEGREES: Float = 270f
