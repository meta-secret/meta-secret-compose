package ui.scenes.registration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import core.appString
import org.jetbrains.compose.resources.vectorResource
import ui.design.generated.OrbitTokens as T
import kotlin.math.PI
import kotlin.math.sin

/** Animates named groups from generated source vectors, preserving shared geometry. */
@Composable
internal fun OrbitAnimatedArt(action: OrbitAction, modifier: Modifier) {
    val vector = vectorResource(action.glyph.resource())
    val p by rememberInfiniteTransition(label = "icon groups").animateFloat(0f, 1f,
        infiniteRepeatable(tween(if (action.glyph == OrbitGlyph.Request) 2000 else 2400, easing = LinearEasing)), label = "glyph phase")
    val builder = ImageVector.Builder(defaultWidth = vector.defaultWidth, defaultHeight = vector.defaultHeight,
        viewportWidth = vector.viewportWidth, viewportHeight = vector.viewportHeight)
    copyGroup(builder, vector.root, if (action.enabled) p else 0f, 1f)
    Image(rememberVectorPainter(builder.build()), appString(action.label), modifier,
        colorFilter = ColorFilter.tint(T.orbitColorControl))
}

private data class GroupMotion(val x: Float = 0f, val y: Float = 0f, val opacity: Float = 1f)

private fun copyGroup(builder: ImageVector.Builder, group: VectorGroup, progress: Float, opacity: Float) {
    val motion = groupMotion(group.name, progress)
    builder.addGroup(name = group.name, rotate = group.rotation, pivotX = group.pivotX, pivotY = group.pivotY,
        scaleX = group.scaleX, scaleY = group.scaleY, translationX = group.translationX + motion.x,
        translationY = group.translationY + motion.y, clipPathData = group.clipPathData)
    group.forEach { node -> when (node) {
        is VectorGroup -> copyGroup(builder, node, progress, opacity * motion.opacity)
        is VectorPath -> builder.addPath(pathData = node.pathData, pathFillType = node.pathFillType, name = node.name,
            fill = node.fill, fillAlpha = node.fillAlpha * opacity * motion.opacity,
            stroke = node.stroke, strokeAlpha = node.strokeAlpha * opacity * motion.opacity,
            strokeLineWidth = node.strokeLineWidth, strokeLineCap = node.strokeLineCap,
            strokeLineJoin = node.strokeLineJoin, strokeLineMiter = node.strokeLineMiter,
            trimPathStart = node.trimPathStart, trimPathEnd = node.trimPathEnd, trimPathOffset = node.trimPathOffset)
    } }
    builder.clearGroup()
}

private fun groupMotion(name: String, p: Float): GroupMotion {
    val shift = if (p < .3f) p / .3f else if (p < .65f) 1 - (p - .3f) / .35f else 0f
    return when (name) {
        "join_upper" -> GroupMotion(-1.2f * shift, 1.2f * shift)
        "join_lower" -> GroupMotion(1.2f * shift, -1.2f * shift)
        "back" -> GroupMotion(-2 * shift)
        "chevron_one", "chevron_two" -> {
            val phase = (p + if (name == "chevron_two") .0625f else 0f) % 1
            val q = if (phase < .3f) phase / .3f else if (phase < .65f) 1 - (phase - .3f) / .35f else 0f
            GroupMotion(y = 2 * q, opacity = 1 - .45f * q)
        }
        "letter_a", "letter_b", "letter_c" -> {
            val phase = (p + when (name) { "letter_b" -> .054f; "letter_c" -> .108f; else -> 0f }) % 1
            val q = if (phase < .35f) phase / .35f else if (phase < .85f) 1 - (phase - .35f) / .5f else 0f
            GroupMotion(opacity = 1 - .65f * q)
        }
        "request_line_one", "request_line_two", "request_line_three" -> {
            val phase = (p + when (name) { "request_line_two" -> .06f; "request_line_three" -> .12f; else -> 0f }) % 1
            when {
                phase < .25f -> GroupMotion(-4 * phase / .25f, opacity = 1 - phase / .25f)
                phase < .45f -> GroupMotion(4 * (1 - (phase - .25f) / .2f), opacity = (phase - .25f) / .2f)
                else -> GroupMotion()
            }
        }
        else -> if (name.startsWith("dot_")) GroupMotion(opacity = (.4 + .6 * (sin(p * 2 * PI) + 1) / 2).toFloat()) else GroupMotion()
    }
}
