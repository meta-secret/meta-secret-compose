package ui.scenes.registration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import core.AppString
import core.appString
import kotlinx.coroutines.launch
import core.registration.registrationQrName
import qrscanner.CameraLens
import qrscanner.OverlayShape
import qrscanner.QrScanner
import ui.design.generated.OrbitTokens as T
import ui.theme.OrbitTextStyles

@Composable
internal fun OrbitQrSheet(
    backdrop: ImageBitmap,
    onClosed: () -> Unit,
    onDecoded: (String) -> Unit,
    cameraPreview: (@Composable () -> Unit)? = null,
) {
    val expansion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<AppString?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { expansion.animateTo(1f, tween(T.orbitDurationQr, easing = CubicBezierEasing(.24f, .75f, .23f, 1f))) }
    fun close(result: String? = null) {
        if (closing) return
        closing = true // Camera is removed before collapse starts.
        scope.launch {
            expansion.animateTo(0f, tween(T.orbitDurationQr, easing = CubicBezierEasing(.24f, .75f, .23f, 1f)))
            onClosed()
            if (result != null) onDecoded(result)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("orbit-qr-sheet").pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }) {
        val p = expansion.value
        val sheetWidth = (maxWidth - T.orbitDimensionQrSide * 2).coerceAtMost(T.orbitDimensionQrMaxWidth)
        val sheetHeight = (maxHeight - T.orbitDimensionQrTop - T.orbitDimensionQrBottom).coerceAtLeast(160.dp)
        val originX = maxWidth / 2 - 66.dp - T.orbitDimensionControlCircle / 2
        val originY = maxHeight / 2 + T.orbitDimensionActionsTopOffset
        val x = originX + ((maxWidth - sheetWidth) / 2 - originX) * p
        val y = originY + (T.orbitDimensionQrTop - originY) * p
        val width = T.orbitDimensionControlCircle + (sheetWidth - T.orbitDimensionControlCircle) * p
        val height = T.orbitDimensionControlCircle + (sheetHeight - T.orbitDimensionControlCircle) * p
        val shape = RoundedCornerShape(21.dp + 6.dp * p)
        val density = LocalDensity.current
        val drawX = with(density) { x.toPx() }
        val drawY = with(density) { y.toPx() }
        Box(Modifier.offset(x, y).size(width, height).shadow(24.dp, shape)
            .clip(shape).drawBehind {
                // Captured actual step04, already blurred by the platform adapter.
                drawImage(backdrop, topLeft = Offset(-drawX, -drawY), colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(T.orbitNumberGlassSaturation) }))
                drawRect(Brush.verticalGradient(listOf(T.orbitColorGlassTopBorder.copy(alpha = .035f), T.orbitColorGlassFill.copy(alpha = 0f))))
                drawLine(T.orbitColorGlassTopBorder.copy(alpha = .1f), Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                drawLine(T.orbitColorCamera.copy(alpha = .45f), Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 3.dp.toPx())
            }.background(T.orbitColorGlassFill).border(1.dp, Brush.verticalGradient(listOf(T.orbitColorGlassTopBorder, T.orbitColorGlassBorder, T.orbitColorGlassBorder)), shape)) {
            if (p > .92f) {
                val cameraSide = (sheetWidth - T.orbitDimensionQrBodyInset * 2)
                    .coerceAtMost((sheetHeight - 168.dp).coerceAtLeast(100.dp))
                    .coerceAtMost(T.orbitDimensionQrCameraMaxSize)
                val compact = sheetHeight <= 430.dp
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.Center).offset(y = if (compact) (-48).dp else 0.dp).size(cameraSide).clip(RoundedCornerShape(12.dp))
                        .background(T.orbitColorCamera).testTag("orbit-camera")) {
                        if (p == 1f && foreground && !closing) {
                            if (cameraPreview != null) cameraPreview() else OrbitCamera({ raw ->
                                if (registrationQrName(raw) != null) close(registrationQrName(raw)) else cameraError = AppString.orbitInvalidQr
                            }, { cameraError = it })
                        }
                        CameraCorners()
                    }
                    Column(Modifier.align(Alignment.Center).offset(y = cameraSide / 2 + if (compact) 12.dp else 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        cameraError?.let { Text(appString(it), style = OrbitTextStyles.label(), color = T.orbitColorError) }
                        OrbitActionButton(OrbitAction(OrbitGlyph.Back, AppString.orbitBack), { close() })
                    }
                }
            }
        }
    }
}

@Composable
private fun OrbitCamera(onResult: (String) -> Unit, onError: (AppString) -> Unit) {
    QrScanner(modifier = Modifier.fillMaxSize(), flashlightOn = false, cameraLens = CameraLens.Back,
        openImagePicker = false, onCompletion = onResult, onFailure = { onError(AppString.orbitCameraError) },
        overlayShape = OverlayShape.Rectangle, overlayColor = T.orbitColorCamera.copy(alpha = 0f),
        overlayBorderColor = T.orbitColorCamera.copy(alpha = 0f), customOverlay = {}, imagePickerHandler = {},
        permissionDeniedView = {
            LaunchedEffect(Unit) { onError(AppString.orbitCameraDenied) }
        },
    )
}

@Composable
private fun CameraCorners() {
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val length = 24.dp.toPx()
        val stroke = 1.5.dp.toPx()
        listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { point ->
            val sx = if (point.x == 0f) 1 else -1
            val sy = if (point.y == 0f) 1 else -1
            drawLine(T.orbitColorText, point, point + Offset(length * sx, 0f), stroke, StrokeCap.Round)
            drawLine(T.orbitColorText, point, point + Offset(0f, length * sy), stroke, StrokeCap.Round)
        }
    }
}
