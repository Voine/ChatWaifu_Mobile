package com.chatwaifu.mobile.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R

@Composable
fun CompanionInput(
    draft: String,
    requestFocus: Boolean,
    enabled: Boolean,
    submitEnabled: Boolean,
    voice: VoiceInputUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onMicrophone: () -> Unit,
    onCancelVoice: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xC71A2B46),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0x45E7F1FF)),
            onClick = {},
        ) {
            Column {
                // 录音时的轻量反馈：一行文字 + 取消。不弹新的大型 Dialog，
                // Live2D 仍然是视觉主体
                AnimatedVisibility(voice.listening) {
                    ListeningBar(onCancel = onCancelVoice)
                }
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 5.dp, end = 6.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        keyboard?.hide()
                        onDismiss()
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.companion_close_input),
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.58f),
                    )
                }
                TextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            stringResource(R.string.companion_input_hint),
                            fontFamily = FontFamily.SansSerif,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                    ),
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.10f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
                        disabledContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (draft.isNotBlank() && enabled && submitEnabled) onSubmit()
                    }),
                )
                // 同一个按钮：不在录音时开始，正在录音时停止。
                // 只做一种交互，不再叠一套长按手势
                IconButton(
                    onClick = onMicrophone,
                    enabled = enabled && voice.state != VoiceInputState.PREPARING,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (voice.listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = stringResource(
                            if (voice.listening) R.string.companion_voice_stop
                            else R.string.companion_voice_start
                        ),
                        modifier = Modifier.size(21.dp),
                        tint = if (voice.listening) {
                            VoiceActiveTint
                        } else {
                            Color.White.copy(alpha = if (voice.busy) 0.38f else 0.76f)
                        },
                    )
                }
                if (draft.isNotBlank()) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = Color(0xD98CBFFF).copy(alpha = if (submitEnabled) 1f else 0.45f),
                    ) {
                        IconButton(onClick = onSubmit, enabled = enabled && submitEnabled) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.send),
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF10233D),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

private val VoiceActiveTint = Color(0xFF8CBFFF)

/**
 * 「正在听…」条。刻意不做音频可视化 —— 当前 ASR 链路不回传音量，
 * 画一个假的波形只是欺骗。三个点的呼吸动画表达「在工作」已经够了。
 */
@Composable
private fun ListeningBar(onCancel: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "listening")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(VoiceActiveTint.copy(alpha = pulse), CircleShape)
        )
        Text(
            stringResource(R.string.companion_voice_listening),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.SansSerif,
        )
        TextButton(onClick = onCancel) {
            Text(
                stringResource(R.string.companion_voice_cancel),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}
