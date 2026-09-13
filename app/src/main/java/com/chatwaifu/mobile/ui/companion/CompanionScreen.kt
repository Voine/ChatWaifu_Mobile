package com.chatwaifu.mobile.ui.companion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun CompanionScreen(
    state: CompanionUiState,
    onEvent: (CompanionEvent) -> Unit,
    renderer: @Composable () -> Unit,
) {
    BackHandler(enabled = state.overlay != CompanionOverlayState.NONE) {
        onEvent(
            if (state.overlay == CompanionOverlayState.HISTORY) {
                CompanionEvent.DismissHistory
            } else {
                CompanionEvent.DismissInput
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        renderer()
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x52071325),
                        0.22f to Color.Transparent,
                        0.58f to Color.Transparent,
                        1f to Color(0x99071325),
                    )
                )
        )
        CompanionOverlay(state = state, onEvent = onEvent)
        AnimatedVisibility(
            visible = state.overlay == CompanionOverlayState.INPUT,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
        ) {
            CompanionInput(
                draft = state.draft,
                requestFocus = state.requestInputFocus,
                enabled = true,
                submitEnabled = state.runtime != CompanionRuntimeState.THINKING &&
                    state.runtime != CompanionRuntimeState.SPEAKING,
                onDraftChanged = { onEvent(CompanionEvent.DraftChanged(it)) },
                onSubmit = { onEvent(CompanionEvent.Submit) },
                onDismiss = { onEvent(CompanionEvent.DismissInput) },
                onMicrophone = { onEvent(CompanionEvent.Vision) },
            )
        }
        AnimatedVisibility(
            visible = state.overlay == CompanionOverlayState.HISTORY,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CompanionHistorySheet(state = state, onEvent = onEvent)
        }
    }
}
