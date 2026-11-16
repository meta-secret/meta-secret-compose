package ui.liveboard.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import core.metaSecretCore.liveboard.LiveboardCreatePhase
import core.metaSecretCore.liveboard.LiveboardDraftMode
import ui.design.generated.LiveboardMaterials
import ui.design.generated.LiveboardScriptedGeometry
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.LiveboardRenderActions
import ui.liveboard.LiveboardRenderEnvironment
import ui.liveboard.LiveboardRenderState
import ui.liveboard.visual.LiveboardControl
import ui.liveboard.visual.LiveboardControlOptions
import ui.liveboard.visual.LiveboardControlSize
import ui.liveboard.visual.LiveboardControlPalette
import ui.liveboard.visual.LiveboardDots
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LiveboardSurface
import ui.liveboard.visual.LiveboardSurfaceStyle
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LocalLiveboardViewport
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import ui.liveboard.visual.liveboardPose
import ui.liveboard.visual.liveboardScript
import ui.design.generated.LiveboardTimelines
import ui.design.generated.LiveboardDuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.withTransform
import ui.liveboard.visual.LiveboardStyleBindings

@Composable
fun CreateSecretModal(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    val sensitive = state.sensitive
    val completion = state.root.createCompletion?.takeIf { sensitive?.createOpen == false && it === sensitive
        .createCompletion }
    val latest by rememberUpdatedState(completion to actions.create.dismiss)
    var heightPx by remember(state.root.content?.session) { mutableFloatStateOf(INITIAL_CREATE_HEIGHT) }
    val measured = modifier.onSizeChanged { if (sensitive?.createOpen == true) heightPx = it.height.toFloat() }
    LaunchedEffect(completion) {
        val captured = completion ?: return@LaunchedEffect
        val hold = LiveboardTimelines.createSuccessReceipt.timing
        Animatable(0f).animateTo(1f, tween((hold.duration as LiveboardDuration.Fixed).ms, easing = LinearEasing))
        if (latest.first === captured) latest.second()
    }
    if (sensitive?.createOpen == true && completion == null && sensitive.draft != null) {
        CreateSecretEditor(state, actions, environment, measured)
    } else {
        CreateResult(completion != null, with(LocalDensity.current) { heightPx.toDp() }, measured)
    }
}

@Composable
private fun CreateResult(saved: Boolean, previousHeight: Dp, modifier: Modifier = Modifier) {
    val viewport = LocalLiveboardViewport.current
    val mark = LiveboardStyleBindings.color(".create-working.is-done .create-loader::after", "border-left",
        viewport, T.liveboardColorApproved)
    val maximum = (viewport.height - if (viewport.short) SHORT_VIEWPORT_INSET else REGULAR_VIEWPORT_INSET)
        .coerceAtLeast(MIN_CREATE_HEIGHT).dp
    LiveboardSurface(modifier.widthIn(max = T.liveboardDimensionCreateMaxWidth).fillMaxWidth().height(previousHeight
        .coerceIn(210.dp, maximum)).testTag("lb-create"),
        LiveboardSurfaceStyle(LiveboardMaterials.create, if (viewport.compact) 23.dp else 27.dp, T
            .liveboardColorVaultBorder)) {
        Column(Modifier.fillMaxSize().testTag(if (saved) "lb-create-success" else "lb-create-transition"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(23.dp,
                Alignment.CenterVertically)) {
            if (saved) Canvas(Modifier.size(53.dp)) {
                val origin = Offset(size.width / 2, size.height / 2)
                withTransform({ rotate(-GRAIN_ROTATION, origin) }) {
                    val left = origin.x - 13.5.dp.toPx(); val top = origin.y - 7.5.dp.toPx()
                    drawLine(mark, Offset(left, top), Offset(left, top + 15.dp.toPx()), 2.dp.toPx())
                    drawLine(mark, Offset(left, top + 15.dp.toPx()), Offset(left + 27.dp.toPx(), top + 15.dp.toPx()
                        ), 2.dp.toPx())
                }
            }
            Text(appString(if (saved) AppString.lbSaved else AppString.lbPending), style = LiveboardTypography.text(
                T.liveboardFontSizeSeedWord), modifier = Modifier.testTag(if (saved
                ) "lb-create-saved" else "lb-create-blocked"))
        }
    }
}

@Composable
private fun CreateSecretEditor(state: LiveboardRenderState, actions: LiveboardRenderActions,
    environment: LiveboardRenderEnvironment, modifier: Modifier = Modifier) {
    val draft = state.sensitive?.draft ?: return
    val phase = state.activity.create.phase
    val enabled = phase == LiveboardCreatePhase.Idle
    val viewport = LocalLiveboardViewport.current
    val compact = viewport.width <= COMPACT_VIEWPORT_WIDTH
    val short = viewport.short
    val padding = if (short) 22.dp else if (compact) T.liveboardDimensionCreateCompactPaddingX else T
        .liveboardDimensionCreatePadding
    val maxHeight = (viewport.height - if (short) LiveboardScriptedGeometry.create.height
        .shortInset else LiveboardScriptedGeometry.create.height.regularInset).coerceAtLeast(MIN_CREATE_HEIGHT).dp
    val fieldsDuration = (LiveboardTimelines.createFields.timing.duration as LiveboardDuration.Fixed).ms
    val fieldsElapsed = remember { Animatable(0f) }
    LaunchedEffect(draft.mode, draft.wordCount, environment.reducedMotion) {
        fieldsElapsed.snapTo(if (environment.reducedMotion) fieldsDuration.toFloat() else 0f)
        if (!environment.reducedMotion) fieldsElapsed.animateTo(fieldsDuration.toFloat(), tween(fieldsDuration,
            easing = LinearEasing))
    }
    val fieldsPose = liveboardScript(LiveboardTimelines.createFields, fieldsElapsed.value)
    LiveboardSurface(modifier.widthIn(max = T.liveboardDimensionCreateMaxWidth).fillMaxWidth().heightIn(max =
        maxHeight).testTag("lb-create")
        .animateContentSize(tween(if (environment.reducedMotion) 0 else T.liveboardDurationCreateResize, easing =
            CubicBezierEasing(RESIZE_EASING_X1, RESIZE_EASING_Y1, RESIZE_EASING_X2, 1f))),
        LiveboardSurfaceStyle(LiveboardMaterials.create, if (compact) T.liveboardDimensionCreateCompactRadius else T
            .liveboardDimensionCreateRadius, T.liveboardColorVaultBorder)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = if (short) 17
            .dp else if (compact) 23.dp else 27.dp)) {
            CompositionLocalProvider(LocalLiveboardViewport provides viewport.copy(creatorWidth = maxWidth.value)) {
                Column(Modifier.fillMaxWidth().heightIn(max = maxHeight - if (short) 34.dp else 46.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (!short) Text(appString(AppString.lbYourVault), style = LiveboardTypography.label(T
                                .liveboardFontSizeEyebrow), modifier = Modifier.padding(bottom = 9.dp))
                            LiveboardDots(appString(AppString.lbCreate), height = if (short) 15.dp else 16.dp)
                        }
                        val closeLabel = appString(AppString.cancel)
                        Box(Modifier.size(if (short) 30.dp else 36.dp).border(1.dp, T.liveboardColorFormBorder.copy(
                            alpha = .4f), CircleShape)
                            .testTag("lb-create-close").semantics { contentDescription = closeLabel }.clickable(
                                enabled = enabled, onClick = actions.create.dismiss), contentAlignment = Alignment
                                .Center) {
                            LiveboardIcon("close", Modifier.size(18.dp), moving = false)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = if (short) 15.dp else 23.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(LiveboardDraftMode.Plain, LiveboardDraftMode.Seed).forEach { mode ->
                            LiveboardSurface(Modifier.weight(1f).testTag(if (mode == LiveboardDraftMode.Plain
                                ) "lb-create-plain" else "lb-create-seed")
                                .clickable(enabled = enabled) { actions.create.edit(draft.copy(mode = mode)) },
                                LiveboardSurfaceStyle(if (draft.mode == mode) LiveboardMaterials
                                    .modeSelected else LiveboardMaterials.mode, 15.dp,
                                    if (draft.mode == mode) T.liveboardColorVaultBorder else T
                                        .liveboardColorTileBorder, false)) {
                                Row(Modifier.fillMaxWidth().heightIn(min = if (short) 43.dp else if (compact) 63
                                    .dp else 66.dp).padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 13.dp,
                                        Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                    LiveboardIcon(if (mode == LiveboardDraftMode.Plain) "show" else "seed", Modifier
                                        .size(24.dp), moving = false)
                                    Text(appString(if (mode == LiveboardDraftMode.Plain) AppString
                                        .secretCapital else AppString.lbSeed), style = LiveboardTypography.text(T
                                        .liveboardFontSizeSeedWord))
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("lb-create-fields")
                            .graphicsLayer { alpha = fieldsPose.opacity; translationY = fieldsPose.translate.second
                                .dp.toPx() }) {
                            CreateSecretFields(draft, actions.create.edit, actions.create.paste, enabled, state
                                .sensitive?.pasteHint.orEmpty())
                        }
                        if (phase != LiveboardCreatePhase.Idle) CreateWorking(phase, draft.mode, Modifier
                            .matchParentSize())
                    }
                    Row(Modifier.fillMaxWidth().padding(top = if (short) 13.dp else 24.dp)
                        .drawBehind { drawLine(T.liveboardColorStorageRule.copy(alpha = .2f), Offset.Zero, Offset(
                            size.width, 0f), 1.dp.toPx()) }
                        .padding(top = if (short) 12.dp else 20.dp), horizontalArrangement = Arrangement.spacedBy(
                            if (short) 30.dp else 38.dp, Alignment.CenterHorizontally)) {
                        val dimensions = LiveboardControlSize(if (short) 39.dp else T
                            .liveboardDimensionCreateControl, T.liveboardDimensionCreateIcon,
                            LiveboardControlPalette.Create, gap = if (short) 6.dp else 9.dp)
                        LiveboardControl(appString(AppString.cancel), "cancel", actions.create.dismiss,
                            LiveboardControlOptions(enabled, tag = "lb-create-cancel", size = dimensions))
                        LiveboardControl(appString(AppString.orbitCreate), "create", actions.create.submit,
                            LiveboardControlOptions(state.activity.create.canSubmit, tag = "lb-create-submit",
                                size = dimensions))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateWorking(phase: LiveboardCreatePhase, mode: LiveboardDraftMode, modifier: Modifier = Modifier) {
    val poses = (0 until LOADER_DOT_COUNT).map { liveboardPose("create-loader-wave", delayMs = when (it % 3) { 0 ->
        -400; 2 -> -200; else -> 0 }) }
    Column(modifier.background(LiveboardMaterials.create.stops[1].color.copy(alpha = .92f)), verticalArrangement =
        Arrangement.spacedBy(23.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(110.dp, 45.dp)) {
            poses.forEachIndexed { index, pose ->
                val x = size.width / 2 + (index - LOADER_CENTER_INDEX) * 8.dp.toPx()
                val height = 24.dp.toPx() * pose.scale.second
                drawLine(T.liveboardColorFormValue.copy(alpha = pose.opacity), Offset(x, (size.height - height) / 2
                    ), Offset(x, (size.height + height) / 2), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(appString(if (phase == LiveboardCreatePhase.Unknown) AppString.lbUnknown else if (mode ==
            LiveboardDraftMode.Seed) AppString.lbCreatingSeed else AppString.lbCreatingSecret), style =
            LiveboardTypography.text(T.liveboardFontSizeSeedWord))
    }
}

private const val INITIAL_CREATE_HEIGHT: Float = 280f
private const val SHORT_VIEWPORT_INSET: Float = 24f
private const val REGULAR_VIEWPORT_INSET: Float = 32f
private const val MIN_CREATE_HEIGHT: Float = 210f
private const val GRAIN_ROTATION: Float = 45f
private const val COMPACT_VIEWPORT_WIDTH: Float = 600f
private const val RESIZE_EASING_X1: Float = .22f
private const val RESIZE_EASING_Y1: Float = .8f
private const val RESIZE_EASING_X2: Float = .24f
private const val LOADER_DOT_COUNT: Int = 9
private const val LOADER_CENTER_INDEX: Int = 4
