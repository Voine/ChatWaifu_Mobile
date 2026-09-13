package com.chatwaifu.mobile.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwaifu.mobile.R

private val panelColor = Color(0xC21A2B46)
private val panelBorder = Color(0x4DE7F1FF)
private val accent = Color(0xFF9CC7FF)

@Composable
fun CompanionOverlay(
    state: CompanionUiState,
    onEvent: (CompanionEvent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CharacterHeader(state, onEvent, Modifier.align(Alignment.TopCenter))
        RendererStatus(
            renderer = state.renderer,
            onEvent = onEvent,
            modifier = Modifier.align(Alignment.Center),
        )

        AnimatedVisibility(
            visible = state.overlay == CompanionOverlayState.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
        ) {
            Column(
                modifier = Modifier.navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CurrentUtterance(state, onEvent)
                FloatingActions(onEvent)
            }
        }

        state.error?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp),
                color = panelColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, panelBorder),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = error.message,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif,
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (error.canRetry) {
                            TextButton(onClick = { onEvent(CompanionEvent.RetryResponse) }) {
                                Text(
                                    stringResource(R.string.companion_retry),
                                    fontFamily = FontFamily.SansSerif,
                                )
                            }
                        } else {
                            TextButton(onClick = { onEvent(CompanionEvent.More) }) {
                                Text(
                                    stringResource(R.string.companion_open_existing),
                                    fontFamily = FontFamily.SansSerif,
                                )
                            }
                        }
                    }
                }
            }
        }
        state.notice?.takeIf { state.error == null }?.let { notice ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .clickable { onEvent(CompanionEvent.DismissNotice) },
                color = panelColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, panelBorder),
            ) {
                Text(
                    text = notice,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}

@Composable
private fun RendererStatus(
    renderer: RendererUiState,
    onEvent: (CompanionEvent) -> Unit,
    modifier: Modifier,
) {
    when (renderer) {
        RendererUiState.Ready -> Unit
        RendererUiState.Loading -> Surface(
            modifier = modifier,
            color = panelColor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, panelBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = accent,
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.companion_renderer_loading),
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
        is RendererUiState.Failed -> Surface(
            modifier = modifier.padding(horizontal = 24.dp),
            color = panelColor,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, panelBorder),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.companion_renderer_failed),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
                Text(
                    renderer.reason,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = FontFamily.SansSerif,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEvent(CompanionEvent.RetryRenderer) }) {
                        Text(
                            stringResource(R.string.companion_retry),
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                    Button(onClick = { onEvent(CompanionEvent.More) }) {
                        Text(
                            stringResource(R.string.companion_open_existing),
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterHeader(
    state: CompanionUiState,
    onEvent: (CompanionEvent) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = state.characterName,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = runtimeLabel(state.runtime),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.SansSerif,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onEvent(CompanionEvent.OpenHistory) },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Outlined.History,
                contentDescription = stringResource(R.string.companion_history),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CurrentUtterance(
    state: CompanionUiState,
    onEvent: (CompanionEvent) -> Unit,
) {
    val scroll = rememberScrollState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 172.dp)
            .animateContentSize()
            .clickable { onEvent(CompanionEvent.OpenInput) },
        color = Color(0xB81A2B46),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, panelBorder),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (state.runtime == CompanionRuntimeState.THINKING) {
                        stringResource(R.string.companion_thinking)
                    } else {
                        state.characterName
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(
                    Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.14f))
                )
            }
            Text(
                text = state.utterance.ifBlank {
                    stringResource(R.string.companion_idle_greeting)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.runtime == CompanionRuntimeState.SPEAKING) {
                VoiceActivityIndicator()
            }
            if (scroll.maxValue > 0) {
                Text(
                    text = stringResource(R.string.companion_scroll_for_more),
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
            }
        }
    }
}

@Composable
private fun VoiceActivityIndicator() {
    val transition = rememberInfiniteTransition(label = "voice activity")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice activity alpha",
    )
    Row(
        modifier = Modifier
            .height(18.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(6.dp, 12.dp, 16.dp, 10.dp, 6.dp).forEachIndexed { index, height ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = 3.dp, height = height)
                    .background(
                        accent.copy(alpha = if (index % 2 == 0) pulse else 1f - pulse / 2f),
                        CircleShape,
                    )
            )
        }
    }
}

@Composable
private fun FloatingActions(onEvent: (CompanionEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CompanionAction(
            Icons.Outlined.ChatBubbleOutline,
            stringResource(R.string.companion_chat),
            primary = true,
        ) { onEvent(CompanionEvent.OpenInput) }
        CompanionAction(
            Icons.Outlined.AutoAwesome,
            stringResource(R.string.companion_interact),
            primary = false,
        ) { onEvent(CompanionEvent.Interact) }
        CompanionAction(
            Icons.Outlined.PhotoCamera,
            stringResource(R.string.companion_vision),
            primary = false,
        ) { onEvent(CompanionEvent.Vision) }
        CompanionAction(
            Icons.Outlined.MoreHoriz,
            stringResource(R.string.companion_more),
            primary = false,
        ) { onEvent(CompanionEvent.More) }
    }
}

@Composable
private fun CompanionAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(if (primary) 42.dp else 38.dp),
                shape = CircleShape,
                color = if (primary) Color(0x8A243A59) else Color(0x521A2B46),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = if (primary) 0.24f else 0.13f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(if (primary) 20.dp else 18.dp),
                        tint = Color.White.copy(alpha = if (primary) 0.94f else 0.68f),
                    )
                }
            }
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = Color.White.copy(alpha = if (primary) 0.88f else 0.62f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun runtimeLabel(runtime: CompanionRuntimeState): String = when (runtime) {
    CompanionRuntimeState.IDLE -> stringResource(R.string.companion_status_idle)
    CompanionRuntimeState.THINKING -> stringResource(R.string.companion_status_thinking)
    CompanionRuntimeState.SPEAKING -> stringResource(R.string.companion_status_speaking)
    CompanionRuntimeState.ERROR -> stringResource(R.string.companion_status_error)
}
