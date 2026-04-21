package ui.dialogs.addsecret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import core.AppColors
import core.ScreenMetricsProviderInterface
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.addSecret
import kotlinproject.composeapp.generated.resources.close
import kotlinproject.composeapp.generated.resources.icon_paste
import kotlinproject.composeapp.generated.resources.manrope_regular
import kotlinproject.composeapp.generated.resources.manrope_semi_bold
import kotlinproject.composeapp.generated.resources.passwordType
import kotlinproject.composeapp.generated.resources.pasteSeedHint
import kotlinproject.composeapp.generated.resources.pasteSeedPhrase
import kotlinproject.composeapp.generated.resources.secretCapital
import kotlinproject.composeapp.generated.resources.secretName
import kotlinproject.composeapp.generated.resources.seedWordCount
import kotlinproject.composeapp.generated.resources.seedPhraseType
import kotlinproject.composeapp.generated.resources.wordPlaceholder
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.ClassicButton

private enum class SecretInputMode {
    PASSWORD,
    SEED_PHRASE,
}

@Composable
fun AddSecret(
    screenMetricsProvider: ScreenMetricsProviderInterface,
    dialogVisibility: (Boolean) -> Unit,
    onResult: ((Boolean) -> Unit)? = null,
) {
    var secretName by remember { mutableStateOf("") }
    var passwordSecret by remember { mutableStateOf("") }
    var inputMode by remember { mutableStateOf(SecretInputMode.PASSWORD) }
    var seedWordCount by remember { mutableStateOf(12) }
    val seedWords = remember { mutableStateListOf(*Array(24) { "" }) }

    val viewModel: AddSecretViewModel = koinViewModel()
    val isLoading by viewModel.isLoading.collectAsState()
    val addState by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    val finalizeClose = remember {
        {
            secretName = ""
            passwordSecret = ""
            inputMode = SecretInputMode.PASSWORD
            seedWordCount = 12
            for (index in seedWords.indices) {
                seedWords[index] = ""
            }
            viewModel.handle(AddSecretEvents.ResetState)
            dialogVisibility(false)
        }
    }

    val requestClose = remember(transitionState) {
        {
            transitionState.targetState = false
        }
    }

    LaunchedEffect(addState) {
        when (addState) {
            AddSecretState.ADDED_SUCCESSFULLY -> {
                onResult?.invoke(true)
                delay(100)
                requestClose()
            }

            AddSecretState.ADDING_FAILURE -> {
                onResult?.invoke(false)
            }

            else -> {}
        }
    }

    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (!transitionState.currentState && !transitionState.targetState) {
            finalizeClose()
        }
    }

    fun applySeedPhrase(rawText: String) {
        val words = rawText
            .trim()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (words.isEmpty()) {
            return
        }

        inputMode = SecretInputMode.SEED_PHRASE
        if (words.size == 12 || words.size == 24) {
            seedWordCount = words.size
        }

        val maxWords = minOf(words.size, 24)
        for (index in seedWords.indices) {
            seedWords[index] = if (index < maxWords) words[index] else ""
        }
    }

    val isAddEnabled = remember(secretName, passwordSecret, inputMode, seedWordCount, isLoading, seedWords) {
        if (isLoading || secretName.isBlank()) {
            false
        } else {
            when (inputMode) {
                SecretInputMode.PASSWORD -> passwordSecret.isNotBlank()
                SecretInputMode.SEED_PHRASE -> seedWords.take(seedWordCount).all { it.isNotBlank() }
            }
        }
    }

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { requestClose() }
                .background(AppColors.Black30),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 350)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 250)
                )
            ) {
                BoxWithConstraints {
                    val density = LocalDensity.current
                    val imeBottomPx = WindowInsets.ime.getBottom(density)
                    val imeBottom = with(density) { imeBottomPx.toDp() }
                    val minHeight = (screenMetricsProvider.heightFactor() * 294).dp
                    val maxCandidate = maxHeight - imeBottom - 16.dp
                    val maxHeightForDialog = maxOf(minHeight, maxCandidate)

                    Box(
                        modifier = Modifier
                            .heightIn(min = minHeight, max = maxHeightForDialog)
                            .fillMaxWidth()
                            .background(AppColors.PopUp, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp)
                            .clickable(onClick = {}, enabled = false)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 30.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(Res.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .clickable { requestClose() }
                                )
                            }

                            Text(
                                text = stringResource(Res.string.addSecret),
                                fontSize = 24.sp,
                                fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                                color = AppColors.White,
                                modifier = Modifier.align(Alignment.Start)
                            )

                            SecretModeSegmentedControl(
                                selectedMode = inputMode,
                                onModeSelected = { inputMode = it }
                            )

                            TextInput(
                                placeholderText = stringResource(Res.string.secretName),
                                value = secretName,
                                onTextChange = { secretName = it }
                            )

                            AnimatedContent(
                                targetState = inputMode,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(220)) + slideInVertically { it / 10 }) togetherWith
                                        (fadeOut(animationSpec = tween(180)) + slideOutVertically { -it / 10 })
                                },
                                label = "secret-input-mode"
                            ) { mode ->
                                when (mode) {
                                    SecretInputMode.PASSWORD -> {
                                        TextInput(
                                            placeholderText = stringResource(Res.string.secretCapital),
                                            value = passwordSecret,
                                            onTextChange = { passwordSecret = it }
                                        )
                                    }

                                    SecretInputMode.SEED_PHRASE -> {
                                        SeedPhraseEditor(
                                            seedWordCount = seedWordCount,
                                            seedWords = seedWords,
                                            onWordCountChange = { seedWordCount = it },
                                            onWordChange = { index, value ->
                                                val normalized = value.trim()
                                                if (normalized.contains(' ') || normalized.contains('\n')) {
                                                    applySeedPhrase(value)
                                                } else {
                                                    seedWords[index] = normalized
                                                }
                                            },
                                            onPaste = {
                                                val clipboardText = clipboardManager.getText()?.text.orEmpty()
                                                if (clipboardText.isNotBlank()) {
                                                    applySeedPhrase(clipboardText)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            ClassicButton(
                                action = {
                                    focusManager.clearFocus()
                                    val trimmedSecretName = secretName.trim()
                                    val event = when (inputMode) {
                                        SecretInputMode.PASSWORD -> AddSecretEvents.AddSecret(
                                            secretName = trimmedSecretName,
                                            secret = passwordSecret.trim()
                                        )

                                        SecretInputMode.SEED_PHRASE -> AddSecretEvents.AddSecret(
                                            secretName = trimmedSecretName,
                                            secret = seedWords.take(seedWordCount).joinToString(" ") { it.trim() }
                                        )
                                    }
                                    viewModel.handle(event)
                                },
                                text = stringResource(Res.string.addSecret),
                                isEnabled = isAddEnabled,
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Black30)
                        .zIndex(10f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.ActionMain)
                }
            }
        }
    }
}

@Composable
private fun SecretModeSegmentedControl(
    selectedMode: SecretInputMode,
    onModeSelected: (SecretInputMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.White5, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SecretModeChip(
            modifier = Modifier.weight(1f),
            iconEmoji = "🔑",
            text = stringResource(Res.string.passwordType),
            selected = selectedMode == SecretInputMode.PASSWORD,
            onClick = { onModeSelected(SecretInputMode.PASSWORD) }
        )
        SecretModeChip(
            modifier = Modifier.weight(1f),
            iconEmoji = "🌱",
            text = stringResource(Res.string.seedPhraseType),
            selected = selectedMode == SecretInputMode.SEED_PHRASE,
            onClick = { onModeSelected(SecretInputMode.SEED_PHRASE) }
        )
    }
}

@Composable
private fun SecretModeChip(
    modifier: Modifier,
    iconEmoji: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) AppColors.ActionPremium.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) AppColors.ActionPremium else AppColors.White10,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = iconEmoji,
                fontSize = 14.sp,
                color = AppColors.White
            )
            Text(
                text = text,
                fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
                fontSize = 14.sp,
                color = AppColors.White
            )
        }
    }
}

@Composable
private fun SeedPhraseEditor(
    seedWordCount: Int,
    seedWords: List<String>,
    onWordCountChange: (Int) -> Unit,
    onWordChange: (index: Int, value: String) -> Unit,
    onPaste: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.seedWordCount),
                color = AppColors.White50,
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(Res.font.manrope_semi_bold))
            )
            Row(
                modifier = Modifier
                    .background(Color(0xFF1F2544), RoundedCornerShape(16.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SeedCountChip(
                    text = "12",
                    selected = seedWordCount == 12,
                    onClick = { onWordCountChange(12) }
                )
                SeedCountChip(
                    text = "24",
                    selected = seedWordCount == 24,
                    onClick = { onWordCountChange(24) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.White5, RoundedCornerShape(10.dp))
                .border(1.dp, AppColors.White10, RoundedCornerShape(10.dp))
                .clickable(onClick = onPaste)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(Res.drawable.icon_paste),
                    contentDescription = null,
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.pasteSeedPhrase),
                        color = AppColors.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_semi_bold))
                    )
                    Text(
                        text = stringResource(Res.string.pasteSeedHint),
                        color = AppColors.White50,
                        fontSize = 12.sp,
                        fontFamily = FontFamily(Font(Res.font.manrope_regular))
                    )
                }
            }
        }

        SeedWordsGrid(
            wordCount = seedWordCount,
            seedWords = seedWords,
            onWordChange = onWordChange
        )
    }
}

@Composable
private fun SeedCountChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .background(
                color = if (selected) AppColors.ActionPremium else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) AppColors.ActionPremium else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) AppColors.White else AppColors.White50,
            fontFamily = FontFamily(Font(Res.font.manrope_semi_bold)),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SeedWordsGrid(
    wordCount: Int,
    seedWords: List<String>,
    onWordChange: (index: Int, value: String) -> Unit,
) {
    val columns = if (wordCount == 12) 2 else 3
    val rows = if (wordCount == 12) 6 else 8
    val horizontalSpacing = if (wordCount == 12) 8.dp else 6.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    SeedWordInput(
                        modifier = Modifier.weight(1f),
                        index = index,
                        value = seedWords[index],
                        onValueChange = { onWordChange(index, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedWordInput(
    modifier: Modifier,
    index: Int,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(AppColors.White5, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = when {
                    isFocused || value.isNotBlank() -> AppColors.ActionPremium.copy(alpha = 0.7f)
                    else -> AppColors.White10
                },
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${index + 1}.",
            color = AppColors.White50,
            fontSize = 12.sp,
            fontFamily = FontFamily(Font(Res.font.manrope_regular)),
            modifier = Modifier.width(20.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 1.dp)
                .onFocusChanged { isFocused = it.isFocused },
            cursorBrush = SolidColor(AppColors.White),
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = AppColors.White,
                fontFamily = FontFamily(Font(Res.font.manrope_regular))
            ),
            maxLines = 1,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = stringResource(Res.string.wordPlaceholder),
                            fontSize = 12.sp,
                            color = AppColors.White30,
                            fontFamily = FontFamily(Font(Res.font.manrope_regular))
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun TextInput(
    placeholderText: String,
    value: String,
    onTextChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = FocusRequester()

    TextField(
        value = value,
        onValueChange = { newText -> onTextChange(newText) },
        shape = RoundedCornerShape(8.dp),
        placeholder = {
            Text(
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.5f),
                text = placeholderText
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp, max = 200.dp)
            .border(
                width = 2.dp,
                color = if (isFocused) AppColors.ActionPremium else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        maxLines = Int.MAX_VALUE,
        singleLine = false,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AppColors.White5,
            unfocusedContainerColor = AppColors.White5,
            cursorColor = AppColors.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
        )
    )
}
