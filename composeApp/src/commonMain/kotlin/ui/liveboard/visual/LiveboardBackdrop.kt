package ui.liveboard.visual

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import core.OrbitBackdropBlur
import kotlinx.coroutines.CancellationException

data class LiveboardBackdropRequest(val epoch: Any, val radius: Dp, val active: Boolean, val bounds: IntSize)

/** The caller records a sanitized background only. Neither input fields nor revealed cards enter it. */
@Composable
fun rememberLiveboardBackdrop(request: LiveboardBackdropRequest, blur: OrbitBackdropBlur, layer: GraphicsLayer
    ): ImageBitmap? {
    var image by remember(request.epoch, request.radius) { mutableStateOf<ImageBitmap?>(null) }
    val current by rememberUpdatedState(request)
    val radius = with(LocalDensity.current) { request.radius.toPx() }
    DisposableEffect(request.epoch, request.radius) { onDispose { image = null } }
    LaunchedEffect(request.epoch, request.radius, request.active, request.bounds) {
        image = null
        if (!request.active || request.bounds == IntSize.Zero) return@LaunchedEffect
        withFrameNanos { }
        if (!current.active || current.epoch !== request.epoch) return@LaunchedEffect
        val captured = try { layer.toImageBitmap() } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { null } ?: return@LaunchedEffect
        val blurred = try { blur.blur(captured, radius) } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { null } ?: return@LaunchedEffect
        if (current.active && current.epoch === request.epoch && current.bounds == request.bounds && current
            .radius == request.radius) image = blurred
    }
    return image.takeIf { request.active }
}
