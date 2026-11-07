package ui.liveboard.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.design.generated.LiveboardGeometry
import ui.design.generated.LiveboardTokens as T

private val animations = mapOf(
    "secret-key" to "secret-key-turn", "secret-key-slash" to "secret-key-slash",
    "secret-clock-hands" to "secret-clock-turn", "device-clock-hands" to "device-clock-tick",
    "secret-copy-sheet" to "secret-copy-expand", "secret-bin-lid" to "secret-bin-lid",
    "secret-bin-cross" to "secret-bin-cross", "device-bin-lid" to "device-bin-tip",
    "device-bin-cross" to "device-bin-mark", "current-glyph" to "device-current-turn",
    "approve-glyph" to "notification-check", "decline-glyph first" to "notification-cross",
        "decline-glyph second" to "notification-cross",
    "create-cancel-arrow" to "create-cancel-step", "create-plus" to "create-plus-breathe",
)

@Composable
fun LiveboardIcon(name: String, modifier: Modifier = Modifier, color: Color = T.liveboardColorNeutralAction,
    moving: Boolean = true, index: Int = 0) {
    val vector = LiveboardGeometry.icons[name] ?: return
    val paths = remember(vector) { vector.parts.associateWith { part -> part.primitives.map { PathParser()
        .parsePathString(it.path).toPath() } } }
    val poses = vector.parts.associateWith { liveboardPose(animations[it.name], moving,
        if (it.name in setOf("secret-key", "secret-bin-lid", "secret-bin-cross")) index else 0, if (it.name ==
            "decline-glyph second") SECOND_DECLINE_DELAY else 0) }
    val currentTrace = liveboardPose(if (name == "current") "device-current-trace" else null, moving)
    Canvas(modifier) {
        val factor = minOf(size.width / vector.viewBox[2], size.height / vector.viewBox[VIEW_BOX_HEIGHT_INDEX])
        withTransform({
            translate((size.width - vector.viewBox[2] * factor) / 2, (size.height - vector.viewBox[
                VIEW_BOX_HEIGHT_INDEX] * factor) / 2)
            scale(factor, factor, Offset.Zero)
        }) {
            vector.parts.forEach { part ->
                val pose = poses.getValue(part)
                val pivot = when (part.name) {
                    "secret-copy-sheet" -> Offset(COPY_PIVOT_X, COPY_PIVOT_Y)
                    "secret-bin-lid" -> Offset(SECRET_BIN_PIVOT_X, BIN_PIVOT_Y)
                    "device-bin-lid" -> Offset(DEFAULT_PIVOT, BIN_PIVOT_Y)
                    else -> Offset(DEFAULT_PIVOT, DEFAULT_PIVOT)
                }
                withTransform({ translate(pose.translate.first, pose.translate.second); rotate(pose.rotation, pivot
                    ); scale(pose.scale.first, pose.scale.second, pivot) }) {
                    paths.getValue(part).forEachIndexed { pathIndex, path ->
                        val primitive = part.primitives[pathIndex]
                        val pathLength = PathMeasure().also { it.setPath(path, false) }.length.coerceAtLeast(
                            MIN_TRACE_LENGTH)
                        val normalized = primitive.attributes["pathLength"] == "1"
                        val trace = if (name == "current") currentTrace.trace else pose.trace
                        val offset = trace.first * if (normalized) pathLength else 1f
                        val intervals = if (trace.second.isNotEmpty()) trace.second.map { (it * pathLength)
                            .coerceAtLeast(MIN_TRACE_LENGTH) }
                            else if (part.name == "secret-bin-cross") listOf(BIN_CROSS_DASH_LENGTH,
                                BIN_CROSS_DASH_LENGTH)
                            else if (offset != 0f) listOf(pathLength, pathLength) else emptyList()
                        val effect = intervals.takeIf { it.isNotEmpty() }?.let { PathEffect.dashPathEffect(it
                            .toFloatArray(), offset) }
                        val fillColor = primitive.attributes["fill"]?.takeIf { it != "none" }
                        val fill = vector.filled || fillColor != null
                        val ink = if (fillColor != null && fillColor != "currentColor") liveboardColor(fillColor,
                            color) else color
                        drawPath(path, ink.copy(alpha = ink.alpha * pose.opacity), style = if (fill) Fill else
                            Stroke(vector.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect =
                                effect))
                    }
                }
            }
        }
    }
}

enum class LiveboardControlPalette { Vault, Secret, Notice, Create }
data class LiveboardControlSize(val diameter: Dp = T.liveboardDimensionControl, val icon: Dp = T
    .liveboardDimensionControlIcon,
    val palette: LiveboardControlPalette = LiveboardControlPalette.Vault, val caption: Boolean = true, val gap: Dp =
        7.dp)
data class LiveboardControlOptions(
    val enabled: Boolean = true, val destructive: Boolean = false, val pending: Boolean = false,
    val tag: String = "", val size: LiveboardControlSize = LiveboardControlSize(),
)

@Composable
fun LiveboardControl(label: String, icon: String, onClick: () -> Unit, options: LiveboardControlOptions =
    LiveboardControlOptions(), modifier: Modifier = Modifier) {
    val interaction = rememberLiveboardInteraction(LiveboardSurfaceInteraction(onClick, options.enabled, kind =
        LiveboardInteractionKind.Control))
    val ink = if (options.destructive && !options.pending) T.liveboardColorSecretDelete else if (options.size
        .palette == LiveboardControlPalette.Notice) T.liveboardColorNoticeAction else T.liveboardColorNeutralAction
    val border = if (interaction.focused || interaction.hovered) T.liveboardColorNeutralAction else if (options
        .destructive && !options.pending) T.liveboardColorSecretDeleteBorder else when (options.size.palette) {
        LiveboardControlPalette.Secret, LiveboardControlPalette.Create -> T.liveboardColorSecretBorder
        LiveboardControlPalette.Notice -> T.liveboardColorNoticeBorder
        else -> T.liveboardColorControlBorder
    }
    val disabled = when (options.size.palette) { LiveboardControlPalette.Secret -> T
        .liveboardNumberDisabledSecretOpacity; LiveboardControlPalette.Create -> DISABLED_CREATE_OPACITY; else -> T
        .liveboardNumberDisabledNoticeOpacity }
    Column(modifier.testTag(options.tag).alpha(if (options.enabled) 1f else disabled).semantics { if (!options.size
        .caption) contentDescription = label }
        .liveboardInteract(interaction),
        verticalArrangement = Arrangement.spacedBy(options.size.gap), horizontalAlignment = Alignment
            .CenterHorizontally) {
        androidx.compose.foundation.layout.Box(Modifier.size(options.size.diameter).border(1.dp, border, CircleShape
            ), contentAlignment = Alignment.Center) {
            LiveboardIcon(if (options.pending) "pending" else icon, Modifier.size(options.size.icon), ink,
                moving = options.enabled || options.pending)
        }
        if (options.size.caption) Text(label, style = LiveboardTypography.text(if (options.size.palette in setOf(
            LiveboardControlPalette.Secret, LiveboardControlPalette.Create)) 10.sp else T
            .liveboardFontSizeVaultActionLabel, ink), textAlign = TextAlign.Center)
    }
}

private const val SECOND_DECLINE_DELAY: Int = 130
private const val VIEW_BOX_HEIGHT_INDEX: Int = 3
private const val COPY_PIVOT_X: Float = 15f
private const val COPY_PIVOT_Y: Float = 10f
private const val SECRET_BIN_PIVOT_X: Float = 7f
private const val BIN_PIVOT_Y: Float = 6f
private const val DEFAULT_PIVOT: Float = 12f
private const val MIN_TRACE_LENGTH: Float = .01f
private const val BIN_CROSS_DASH_LENGTH: Float = 20f
private const val DISABLED_CREATE_OPACITY: Float = .27f
