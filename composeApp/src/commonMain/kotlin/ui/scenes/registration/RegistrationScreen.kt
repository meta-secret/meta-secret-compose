package ui.scenes.registration

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.annotation.ExperimentalVoyagerApi
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.transitions.ScreenTransition
import core.OrbitBackdropBlur
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import cafe.adriel.voyager.core.lifecycle.LifecycleEffectOnce
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import ui.design.generated.OrbitTokens as T
import ui.scenes.mainscreen.MainScreen

@OptIn(ExperimentalVoyagerApi::class)
class RegistrationScreen : Screen, ScreenTransition {
    override val key = uniqueScreenKey

    override fun enter(lastEvent: StackEvent): EnterTransition? =
        if (lastEvent == StackEvent.Replace) {
            fadeIn(tween(900)) + scaleIn(tween(900), initialScale = .965f)
        } else null

    override fun exit(lastEvent: StackEvent): ExitTransition? =
        if (lastEvent == StackEvent.Replace) fadeOut(tween(900)) else null

    @Composable
    override fun Content() {
        val vm: RegistrationViewModel = koinViewModel(key = "registration-$key")
        LifecycleEffectOnce { onDispose { vm.stopSession() } }
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val blur: OrbitBackdropBlur = koinInject()
        val state by vm.state.collectAsState()
        val openMain by vm.openMain.collectAsState()
        val navigator = LocalNavigator.current
        val scope = rememberCoroutineScope()
        val layer = rememberGraphicsLayer()
        val radius = with(LocalDensity.current) { T.orbitDimensionQrBlur.toPx() }
        var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        var capturing by remember { mutableStateOf(false) }
        var bounds by remember { mutableStateOf(IntSize.Zero) }
        var cameraFailure by remember { mutableStateOf(false) }
        LaunchedEffect(openMain) {
            if (openMain) {
                navigator?.replaceAll(MainScreen())
            }
        }
        LaunchedEffect(bounds) {
            if (backdrop != null) backdrop = blur.blur(layer.toImageBitmap(), radius)
        }
        Box(Modifier.fillMaxSize().background(T.orbitColorBackgroundEdge).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding().onSizeChanged { bounds = it }) {
            val backdropSemantics = if (backdrop != null) Modifier.clearAndSetSemantics {} else Modifier
            Box(Modifier.fillMaxSize().then(backdropSemantics).drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }) {
                OrbitLayout(if (cameraFailure) state.copy(error = core.AppString.orbitCameraError) else state, { intent ->
                    if (intent == RegistrationIntent.ConfirmEmail || intent == RegistrationIntent.Back) {
                        focus.clearFocus()
                        keyboard?.hide()
                    }
                    vm.handle(intent)
                }, {
                    if (!capturing && backdrop == null) scope.launch {
                        capturing = true
                        cameraFailure = false
                        try { backdrop = blur.blur(layer.toImageBitmap(), radius) }
                        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { cameraFailure = true }
                        finally { capturing = false }
                    }
                })
            }
            backdrop?.let { image -> OrbitQrSheet(image, { backdrop = null }, { vm.handle(RegistrationIntent.QrDecoded(it)) }) }
        }
    }
}
