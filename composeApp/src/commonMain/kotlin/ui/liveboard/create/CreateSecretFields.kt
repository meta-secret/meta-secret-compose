package ui.liveboard.create

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import core.AppString
import core.appString
import core.metaSecretCore.liveboard.LiveboardDraft
import core.metaSecretCore.liveboard.LiveboardDraftMode
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardTypography
import ui.liveboard.visual.LiveboardIcon
import ui.liveboard.visual.LocalLiveboardViewport

private data class FormField(val value: String, val label: String, val tag: String, val enabled: Boolean,
    val multiline: Boolean = false)

@Composable
private fun Field(field: FormField, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (field.label.isNotEmpty()) Text(field.label, style = LiveboardTypography.label(T.liveboardFontSizeFormLabel))
        val placeholder = appString(if (field.multiline) AppString.lbEnterSecret else AppString.secretName)
        BasicTextField(field.value, onChange, Modifier.fillMaxWidth().testTag(field.tag)
            .semantics { contentDescription = field.label }
            .onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) T.liveboardColorFormFocus else T.liveboardColorFormBorder,
                RoundedCornerShape(T.liveboardDimensionFormRadius))
            .heightIn(min = if (field.multiline) 104.dp else T.liveboardDimensionFormMinHeight, max = if (field
                .multiline) 220.dp else androidx.compose.ui.unit.Dp.Infinity)
            .padding(horizontal = T.liveboardDimensionFormPaddingX, vertical = T.liveboardDimensionFormPaddingY),
            enabled = field.enabled, singleLine = !field.multiline,
            textStyle = LiveboardTypography.text(T.liveboardFontSizeFormValue, T.liveboardColorFormValue),
            cursorBrush = SolidColor(T.liveboardColorDevice), keyboardOptions = KeyboardOptions(imeAction = if (
                field.multiline) ImeAction.Default else ImeAction.Next),
            decorationBox = { input -> Box { if (field.value.isEmpty()) Text(placeholder, style =
                LiveboardTypography.text(T.liveboardFontSizeFormValue, T.liveboardColorPlaceholder)); input() } })
    }
}

@Composable
fun CreateSecretFields(draft: LiveboardDraft, edit: (LiveboardDraft) -> Unit, paste: () -> Unit, enabled: Boolean,
    hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Field(FormField(draft.name, appString(AppString.lbName), "lb-create-name", enabled), { edit(draft.copy(
            name = it)) })
        if (draft.mode == LiveboardDraftMode.Plain) {
            Field(FormField(draft.value, appString(AppString.lbValue), "lb-create-value", enabled, multiline = true
                ), { edit(draft.copy(value = it)) })
        } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appString(AppString.lbSeed), style = LiveboardTypography.label(T.liveboardFontSizeFormLabel),
                    modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.border(1.dp, T.liveboardColorStorageRule.copy(alpha = .33f), RoundedCornerShape(11.dp))
                    .padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(SHORT_SEED_WORDS, MAX_SEED_WORDS).forEach { count -> Box(Modifier.widthIn(min = 38.dp)
                        .heightIn(min = 28.dp).testTag("lb-create-count-$count")
                        .background(if (draft.wordCount == count) T.liveboardColorDevice.copy(alpha = .08f
                            ) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(7.dp))
                        .semantics { selected = draft.wordCount == count }.clickable(enabled = enabled) { edit(draft
                            .copy(wordCount = count)) }.padding(horizontal = 9.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center) {
                        Text(count.toString(), style = LiveboardTypography.text(T.liveboardFontSizeSeedWord,
                            if (draft.wordCount == count) T.liveboardColorFormValue else T.liveboardColorMuted))
                    } }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).border(1.dp, T.liveboardColorFormBorder.copy(alpha =
                .23f), RoundedCornerShape(11.dp))
                .background(T.liveboardColorDevice.copy(alpha = .03f), RoundedCornerShape(11.dp))
                .testTag("lb-create-paste").clickable(enabled = enabled, onClick = paste).padding(horizontal = 11
                    .dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveboardIcon("paste", Modifier.size(19.dp), moving = false)
                Text(appString(AppString.pasteSeedPhrase), style = LiveboardTypography.text(T
                    .liveboardFontSizeFormLabel), modifier = Modifier.weight(1f))
                Text(appString(AppString.lbWordProgress).replace("{filled}", draft.words.take(draft.wordCount)
                    .count { it.isNotBlank() }.toString()).replace("{count}", draft.wordCount.toString()),
                    style = LiveboardTypography.text(T.liveboardFontSizeSeedNumber, T.liveboardColorMuted))
            }
            val columns = if (LocalLiveboardViewport.current.creatorWidth > WIDE_SEED_WIDTH) WIDE_SEED_COLUMNS else 2
            Column(verticalArrangement = Arrangement.spacedBy(T.liveboardDimensionSeedGap)) {
            (0 until draft.wordCount).chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(T
                    .liveboardDimensionSeedGap)) {
                    row.forEach { index ->
                        var focused by remember { mutableStateOf(false) }
                        Row(Modifier.weight(1f).heightIn(min = 43.dp).border(1.dp, if (focused) T
                            .liveboardColorFormFocus else T.liveboardColorFormBorder.copy(alpha = .4f),
                            RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment
                                .CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text((index + 1).toString().padStart(2, '0'), style = LiveboardTypography.text(T
                                .liveboardFontSizeSeedNumber, T.liveboardColorStorageRule))
                            val word = draft.words.getOrElse(index) { "" }
                            BasicTextField(word, { value ->
                                val pasted = value.trim().split(Regex("\\s+"))
                                if (pasted.size in listOf(SHORT_SEED_WORDS, MAX_SEED_WORDS)) edit(draft.copy(
                                    wordCount = pasted.size, words = List(MAX_SEED_WORDS) { pasted.getOrElse(it) {
                                    "" } }))
                                else edit(draft.copy(words = List(MAX_SEED_WORDS) { if (it == index
                                    ) value else draft.words.getOrElse(it) { "" } }))
                            }, Modifier.weight(1f).testTag("lb-create-word-${index + 1}").onFocusChanged { focused =
                                it.isFocused },
                                enabled = enabled, singleLine = true, textStyle = LiveboardTypography.text(T
                                    .liveboardFontSizeSeedWord, T.liveboardColorFormValue),
                                cursorBrush = SolidColor(T.liveboardColorDevice), keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next),
                                decorationBox = { input -> Box { if (word.isEmpty()) Text(appString(AppString
                                    .wordPlaceholder), style = LiveboardTypography.text(T.liveboardFontSizeSeedWord,
                                    T.liveboardColorPlaceholder)); input() } })
                        }
                    }
                }
            }
            }
            Text(hint.ifEmpty { appString(AppString.lbPasteHint) }, style = LiveboardTypography.text(T
                .liveboardFontSizeFoot, T.liveboardColorMuted), modifier = Modifier.testTag("lb-create-paste-hint"))
        }
    }
}

private const val SHORT_SEED_WORDS: Int = 12
private const val MAX_SEED_WORDS: Int = 24
private const val WIDE_SEED_WIDTH: Float = 450f
private const val WIDE_SEED_COLUMNS: Int = 3
