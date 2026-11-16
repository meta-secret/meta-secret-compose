package ui.scenes.registration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.AppString
import core.appString
import core.metaSecretCore.VaultAvailability
import models.appInternalModels.EmailProvider
import ui.design.generated.OrbitTokens as T
import ui.theme.OrbitTextStyles

@Composable
internal fun OrbitLayout(state: RegistrationUiState, onIntent: (RegistrationIntent) -> Unit, onQr: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.radialGradient(
        listOf(T.orbitColorBackgroundCenter, T.orbitColorBackgroundEdge), radius = 1100f,
    )).testTag("orbit-registration")) {
        val center = maxHeight / 2
        OrbitScale(center)
        OrbitMotion(state, ((maxWidth - T.orbitDimensionPivotRightInset) / maxWidth).coerceIn(0f, 1f)) { frame,
            interactive ->
            val emit: (RegistrationIntent) -> Unit = { if (interactive) onIntent(it) }
            OrbitStep(frame.scene.step, center)
            if (frame.scene == RegistrationScene.Manual) {
                OrbitEmailField(frame, center, emit, interactive)
            } else {
                Column(Modifier.align(Alignment.Center).width((maxWidth - 144.dp).coerceAtMost(560.dp))
                    , horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val heading = if (frame.scene == RegistrationScene.Providers) appString(AppString.orbitBrand
                        ) else frame.identity.email
                    Text(heading, style = OrbitTextStyles.heading(if (frame.scene == RegistrationScene.Providers
                        ) 29f else 23f),
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("orbit-heading"))
                    Text(appString(status(frame)), style = OrbitTextStyles.status(), color = if (frame.error != null
                        ) T.orbitColorError else T.orbitColorSecondary,
                        textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.testTag("orbit-status"))
                }
            }
            val row = Modifier.align(Alignment.TopCenter).offset(y = center + T.orbitDimensionActionsTopOffset)
            when {
                frame.scene == RegistrationScene.Providers -> OrbitActionRow(listOf(
                    OrbitAction(OrbitGlyph.Apple, AppString.orbitApple, !frame.busy, false),
                    OrbitAction(OrbitGlyph.Google, AppString.orbitGoogle, !frame.busy, false),
                    OrbitAction(OrbitGlyph.Enter, AppString.orbitEnter, !frame.busy, false),
                ), { glyph -> when (glyph) {
                    OrbitGlyph.Apple -> emit(RegistrationIntent.Provider(EmailProvider.APPLE))
                    OrbitGlyph.Google -> emit(RegistrationIntent.Provider(EmailProvider.GOOGLE))
                    else -> emit(RegistrationIntent.Manual)
                } }, row)
                frame.scene == RegistrationScene.Manual -> OrbitActionRow(listOf(
                    OrbitAction(OrbitGlyph.Back, AppString.orbitBack), OrbitAction(OrbitGlyph.Ok, AppString.orbitOk),
                ), { emit(if (it == OrbitGlyph.Back) RegistrationIntent.Back else RegistrationIntent.ConfirmEmail)
                    }, row)
                frame.scene == RegistrationScene.Join -> {
                    OrbitJoinActions(frame, { glyph -> when (glyph) {
                        OrbitGlyph.Qr -> if (interactive) onQr()
                        OrbitGlyph.Request -> emit(RegistrationIntent.Request)
                        else -> emit(RegistrationIntent.Back)
                    } }, row)
                    if (frame.phase in setOf(RegistrationPhase.Pending, RegistrationPhase.Requesting,
                        RegistrationPhase.Syncing)) {
                        OrbitLoader(frame.phase, row.offset(y = 3.5.dp))
                    }
                }
                frame.phase == RegistrationPhase.Creating -> {
                    OrbitWithdrawingActions(row)
                    OrbitLoader(frame.phase, row.offset(y = 3.5.dp))
                }
                frame.phase in setOf(RegistrationPhase.Checking, RegistrationPhase.Syncing) ->
                    OrbitLoader(frame.phase, row.offset(y = 3.5.dp))
                frame.phase == RegistrationPhase.CreateError -> Unit
                frame.phase == RegistrationPhase.Error -> OrbitActionRow(listOf(OrbitAction(OrbitGlyph.Back,
                    AppString.orbitBack)), { emit(RegistrationIntent.Back) }, row)
                else -> OrbitActionRow(listOf(
                    OrbitAction(OrbitGlyph.Create, AppString.orbitCreate, frame.identity.availability ==
                        VaultAvailability.AVAILABLE),
                    OrbitAction(OrbitGlyph.Join, AppString.orbitJoin,
                        frame.phase == RegistrationPhase.Idle && frame.identity.availability == VaultAvailability
                            .EXISTS),
                    OrbitAction(OrbitGlyph.Enter, AppString.orbitEnter),
                ), { emit(when (it) {
                    OrbitGlyph.Create -> RegistrationIntent.Create
                    OrbitGlyph.Join -> RegistrationIntent.Join
                    else -> RegistrationIntent.Manual
                }) }, row)
            }
        }
    }
}

@Composable
private fun OrbitEmailField(state: RegistrationUiState, center: Dp, onIntent: (RegistrationIntent) -> Unit,
    interactive: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(interactive) { if (interactive) focus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicTextField(state.identity.email, { onIntent(RegistrationIntent.Email(it)) },
            modifier = Modifier.width((maxWidth - 142.dp).coerceAtLeast(100.dp).coerceAtMost(T
                .orbitDimensionFieldMaxWidth)).height(T.orbitDimensionFieldHeight)
                .testTag("orbit-email-input").focusRequester(focus).border(1.dp, if (state.error != null) T
                    .orbitColorError else T.orbitColorFieldBorder,
                    RoundedCornerShape(T.orbitDimensionFieldRadius)),
            textStyle = OrbitTextStyles.field(), singleLine = true, enabled = interactive, cursorBrush = SolidColor(
                T.orbitColorText),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onIntent(RegistrationIntent.ConfirmEmail) }),
            decorationBox = { field -> Box(Modifier.fillMaxSize().offset(x = 15.dp), contentAlignment = Alignment
                .CenterStart) {
                if (state.identity.email.isEmpty()) Text(appString(AppString.orbitEmailPlaceholder), style =
                    OrbitTextStyles.field(), color = T.orbitColorSecondary)
                field()
            } },
        )
        state.error?.let { error -> Text(appString(error), style = OrbitTextStyles.label(), color = T.orbitColorError,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = center + 28.dp)) }
    }
}

private fun status(state: RegistrationUiState): AppString = state.error ?: when (state.phase) {
    RegistrationPhase.Checking -> AppString.orbitChecking
    RegistrationPhase.Creating -> AppString.orbitCreating
    RegistrationPhase.Requesting, RegistrationPhase.Pending -> AppString.orbitAwaiting
    RegistrationPhase.Syncing -> AppString.orbitSyncing
    RegistrationPhase.Declined -> AppString.orbitDeclined
    else -> when (state.scene) {
        RegistrationScene.Providers -> AppString.orbitTagline
        RegistrationScene.Join -> AppString.orbitJoining
        else -> if (state.identity.availability == VaultAvailability.EXISTS) AppString.orbitTaken else AppString
            .orbitAvailable
    }
}
