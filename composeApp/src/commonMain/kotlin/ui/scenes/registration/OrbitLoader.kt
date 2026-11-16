package ui.scenes.registration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import ui.design.generated.OrbitTokens as T
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.ui.MotionDurationScale
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun OrbitLoader(phase: RegistrationPhase, modifier: Modifier = Modifier) {
    val wave by rememberInfiniteTransition(label = "loader").animateFloat(0f, 1f,
        infiniteRepeatable(tween(T.orbitDurationLoaderWave, easing = LinearEasing)), label = "bars")
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if ((phase == RegistrationPhase.Requesting || phase == RegistrationPhase.Pending) &&
            currentCoroutineContext()[MotionDurationScale]?.scaleFactor != 0f) delay(T.orbitDurationJoinFusion.toLong())
        reveal.animateTo(1f, tween(T.orbitDurationLoaderReveal))
    }
    val join = phase in setOf(RegistrationPhase.Requesting, RegistrationPhase.Pending, RegistrationPhase.Syncing)
    Canvas(modifier.size(if (join) 228.dp else 132.dp, 35.dp)) {
        val count = if (join) 28 else 12
        repeat(count) { index ->
            val fraction = index.toFloat() / (count - 1)
            if ((phase == RegistrationPhase.Creating && fraction > reveal.value) ||
                (phase != RegistrationPhase.Creating && kotlin.math.abs(fraction - .5f) > reveal.value / 2)
                    ) return@repeat
            val x = size.width * fraction
            if (join && kotlin.math.abs(x - size.width / 2) < 34.dp.toPx()) return@repeat
            val strength = ((sin((wave + index * .095f) * 2 * PI) + 1) / 2).toFloat()
            val height = (4 + 23 * strength).dp.toPx()
            drawLine(T.orbitColorLoader.copy(alpha = .14f + .7f * strength), Offset(x, (size.height - height) / 2),
                Offset(x, (size.height + height) / 2), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
