package ui.scenes.registration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.design.generated.OrbitTokens as T
import ui.theme.OrbitTextStyles
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@Composable
internal fun OrbitScale(center: Dp) {
    val rulerCenterY = T.orbitDimensionRulerHeight / 2
    Canvas(Modifier.offset(x = T.orbitDimensionRulerLeft, y = center - rulerCenterY)
        .size(T.orbitDimensionRulerWidth, T.orbitDimensionRulerHeight)) {
        for (tick in -40..40) {
            val angle = (180 + tick) * PI / 180
            val inner = if (tick % 5 == 0) 230f else 234f
            val tail = 40f
            val opacity = cos(abs(tick) / tail * PI / 2).coerceAtLeast(0.0).pow(1.65).toFloat()
            fun point(radius: Float) = Offset((265 + cos(angle) * radius).toFloat().dp.toPx(), (rulerCenterY.value +
                sin(angle) * radius).toFloat().dp.toPx())
            drawLine(T.orbitColorRuler.copy(alpha = opacity), point(inner), point(241f), .9.dp.toPx())
        }
    }
}

@Composable
internal fun OrbitStep(step: String, center: Dp) {
    val tickCenterX = T.orbitDimensionRulerLeft + (265 - (230 + 241) / 2f).dp
    Box(Modifier.offset(x = tickCenterX - T.orbitDimensionStepRadius, y = center - T.orbitDimensionStepRadius)
        .size(T.orbitDimensionStepRadius * 2).background(T.orbitColorStepFill, CircleShape)
        .border(1.dp, T.orbitColorRuler, CircleShape), contentAlignment = Alignment.Center) {
        Text(step, style = OrbitTextStyles.step())
    }
}
