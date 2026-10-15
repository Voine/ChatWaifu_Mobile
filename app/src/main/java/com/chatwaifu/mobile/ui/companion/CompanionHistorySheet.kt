package com.chatwaifu.mobile.ui.companion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatwaifu.mobile.R
import androidx.compose.foundation.layout.size

@Composable
fun CompanionHistorySheet(
    state: CompanionUiState,
    onEvent: (CompanionEvent) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val targetHeight = maxHeight * if (state.historyExpanded) 0.96f else 0.60f
        val sheetHeight by animateDpAsState(targetHeight, label = "history sheet height")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.08f))
                .clickable { onEvent(CompanionEvent.DismissHistory) }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight),
            color = Color(0xD414243C),
            contentColor = Color.White.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
            onClick = {},
            interactionSource = remember { MutableInteractionSource() },
        ) {
            Column {
                HistoryHandle(state.historyExpanded, onEvent)
                if (state.history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.companion_history_empty),
                            color = Color.White.copy(alpha = 0.58f),
                            fontFamily = FontFamily.SansSerif,
                        )
                    }
                } else {
                    // 打开时停在最新一条：列表是时间正序，最新的在底部
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = state.history.lastIndex.coerceAtLeast(0),
                    )
                    // 第一条可见 = 滑到顶了，往前翻一页
                    val atTop by remember {
                        derivedStateOf { listState.firstVisibleItemIndex == 0 }
                    }
                    LaunchedEffect(atTop, state.hasOlderHistory, state.history.size) {
                        if (atTop && state.hasOlderHistory) onEvent(CompanionEvent.LoadOlderHistory)
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.loadingOlderHistory) {
                            item(key = "loading_older") { LoadingOlderRow() }
                        }
                        items(state.history, key = { it.id }) { item ->
                            HistoryItem(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHandle(
    expanded: Boolean,
    onEvent: (CompanionEvent) -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(expanded) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                    },
                    onDragEnd = {
                        if ((!expanded && dragDistance < -40f) ||
                            (expanded && dragDistance > 40f)
                        ) {
                            onEvent(CompanionEvent.ToggleHistoryHeight)
                        }
                        dragDistance = 0f
                    },
                )
            }
            .clickable { onEvent(CompanionEvent.ToggleHistoryHeight) }
            .padding(top = 7.dp, start = 16.dp, end = 8.dp, bottom = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.12f)
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.26f), RoundedCornerShape(2.dp))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.companion_history),
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.SansSerif,
                ),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = stringResource(
                    if (expanded) R.string.companion_history_collapse
                    else R.string.companion_history_expand
                ),
                modifier = Modifier.height(20.dp),
                tint = Color.White.copy(alpha = 0.58f),
            )
            IconButton(onClick = { onEvent(CompanionEvent.DismissHistory) }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.companion_history_close),
                    modifier = Modifier.height(19.dp),
                    tint = Color.White.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@Composable
private fun LoadingOlderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Color.White.copy(alpha = 0.58f),
            strokeWidth = 1.5.dp,
        )
        Text(
            stringResource(R.string.companion_history_loading_older),
            modifier = Modifier.padding(start = 8.dp),
            color = Color.White.copy(alpha = 0.58f),
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun HistoryItem(item: CompanionHistoryItem) {
    val isUser = item.author == CompanionAuthor.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.84f),
            color = if (isUser) Color(0x573B6697) else Color.White.copy(alpha = 0.10f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (item.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(bottom = if (item.text.isBlank()) 0.dp else 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.attachments.forEach { attachment ->
                            AttachmentThumb(attachment, size = 72.dp)
                        }
                    }
                }
                if (item.text.isNotBlank()) {
                    Text(
                        item.text,
                        color = Color.White.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = item.time,
                    modifier = Modifier.align(Alignment.End),
                    color = Color.White.copy(alpha = 0.46f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}
