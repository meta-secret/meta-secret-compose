package ui.liveboard.secrets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import models.apiModels.liveboard.LiveboardSecretIdentity
import ui.design.generated.LiveboardTokens as T
import ui.liveboard.visual.LiveboardTypography

/** Called only for a current authorized disclosure; no remembered plaintext or text layout. */
@Composable
fun SecretValue(identity: LiveboardSecretIdentity, value: String, modifier: Modifier = Modifier) {
    val words = value.trim().split(Regex("\\s+"))
    val seed = identity.wordCount in listOf(SHORT_SEED_WORDS, MAX_SEED_WORDS) || identity.type?.lowercase()
        ?.contains("seed") == true ||
        (identity.wordCount == null && identity.type == null && words.size in listOf(SHORT_SEED_WORDS, MAX_SEED_WORDS))
    if (!seed) {
        Text(value, modifier.testTag("lb-value-${identity.passId}"), style = LiveboardTypography.text(T
            .liveboardFontSizeSecretValue, T.liveboardColorSecretValue).copy(lineHeight = T
            .liveboardFontSizeSecretValue * 1.75f))
    } else {
        Column(modifier.fillMaxWidth().testTag("lb-value-${identity.passId}"), verticalArrangement = Arrangement
            .spacedBy(9.dp)) {
            words.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { column, word -> Row(Modifier.weight(1f), horizontalArrangement =
                        Arrangement.spacedBy(7.dp)) {
                        Text((row * 2 + column + 1).toString().padStart(2, '0'), style = LiveboardTypography.text(T
                            .liveboardFontSizeSecretSeedNumber, T.liveboardColorStorageRule), modifier = Modifier
                            .padding(top = 4.dp))
                        Text(word, style = LiveboardTypography.text(T.liveboardFontSizeSecretSeedWord, T
                            .liveboardColorSecretValue))
                    } }
                    if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val SHORT_SEED_WORDS: Int = 12
private const val MAX_SEED_WORDS: Int = 24
