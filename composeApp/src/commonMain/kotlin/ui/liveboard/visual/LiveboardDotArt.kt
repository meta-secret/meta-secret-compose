package ui.liveboard.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.design.generated.LiveboardGeometry
import ui.design.generated.LiveboardTokens as T

@Composable
fun LiveboardDots(text: String, modifier: Modifier = Modifier, height: Dp = 22.dp, color: Color = T.liveboardColorInk) {
    val label = text.uppercase()
    val glyphs = LiveboardGeometry.glyphs
    if (label.any { it.toString() !in glyphs }) {
        Text(text, modifier, style = LiveboardTypography.text(), color = color)
        return
    }
    val width = label.sumOf { (glyphs.getValue(it.toString()).firstOrNull()?.length ?: 0) + 1 }.coerceAtLeast(1) - 1
    Canvas(modifier.height(height).aspectRatio(width / GLYPH_ROW_COUNT_FLOAT).semantics { contentDescription = text }) {
        val unit = minOf(size.width / width.coerceAtLeast(1), size.height / GLYPH_ROW_COUNT)
        var x = (size.width - width * unit) / 2
        label.forEach { char ->
            val glyph = glyphs.getValue(char.toString())
            glyph.forEachIndexed { row, dots -> dots.forEachIndexed { column, dot ->
                if (dot == '1') {
                    drawCircle(color, LiveboardGeometry.glyphRadius * unit, Offset(x + (column + PIXEL_CENTER_OFFSET
                        ) * unit, (row + PIXEL_CENTER_OFFSET) * unit))
                }
            } }
            x += ((glyph.firstOrNull()?.length ?: 0) + LiveboardGeometry.glyphGap) * unit
        }
    }
}

@Composable
fun LiveboardArt(name: String, modifier: Modifier = Modifier, tint: Color = T.liveboardColorDevice) {
    val art = LiveboardGeometry.art[name] ?: return
    Canvas(modifier) {
        val scale = minOf(size.width / art.viewBox[2], size.height / art.viewBox[VIEW_BOX_HEIGHT_INDEX])
        val origin = Offset((size.width - art.viewBox[2] * scale) / 2, (size.height - art.viewBox[
            VIEW_BOX_HEIGHT_INDEX] * scale) / 2)
        art.dots.forEach { dot ->
            val color = if (dot.color == "currentColor") tint else liveboardColor(dot.color, tint)
            drawCircle(color, dot.radius * scale, origin + Offset(dot.x - art.viewBox[0], dot.y - art.viewBox[1]) *
                scale)
        }
    }
}

private const val GLYPH_ROW_COUNT_FLOAT: Float = 7f
private const val GLYPH_ROW_COUNT: Int = 7
private const val PIXEL_CENTER_OFFSET: Float = .5f
private const val VIEW_BOX_HEIGHT_INDEX: Int = 3
