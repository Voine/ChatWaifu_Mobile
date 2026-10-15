package com.chatwaifu.mobile.ui.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R

private val menuPanel = Color(0xE61A2B46)
private val menuBorder = Color(0x4DE7F1FF)

/**
 * 「更多」菜单。通往二级页面的唯一入口 —— 正式版没有抽屉，Companion 就是根页面。
 *
 * 点空白处关闭；条目点击只发事件，跳转和 renderer 释放都由宿主 Activity 做。
 */
@Composable
fun CompanionMenu(onEvent: (CompanionEvent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66071325))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onEvent(CompanionEvent.DismissMenu) },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // 吃掉面板内部的点击，免得穿透到外层的「点空白关闭」
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
            color = menuPanel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, menuBorder),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuItem(Icons.Outlined.Face, stringResource(R.string.companion_menu_characters)) {
                    onEvent(CompanionEvent.Navigate(CompanionDestination.CHARACTERS))
                }
                MenuItem(Icons.Outlined.History, stringResource(R.string.companion_menu_chat_log)) {
                    onEvent(CompanionEvent.Navigate(CompanionDestination.CHAT_LOG))
                }
                MenuItem(Icons.Outlined.Psychology, stringResource(R.string.companion_menu_memory)) {
                    onEvent(CompanionEvent.Navigate(CompanionDestination.MEMORY))
                }
                MenuItem(Icons.Outlined.Settings, stringResource(R.string.companion_menu_settings)) {
                    onEvent(CompanionEvent.Navigate(CompanionDestination.SETTINGS))
                }
                MenuItem(Icons.Outlined.OpenWith, stringResource(R.string.companion_menu_adjust)) {
                    onEvent(CompanionEvent.StartAdjust)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = Color.White.copy(alpha = 0.86f),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        )
    }
}

/**
 * 调整位置时的浮条。**不铺满全屏**：中间要留给 Live2D 接收拖动和双指缩放，
 * 所以这一层只在顶部放一条提示 + 两个按钮。
 */
@Composable
fun CompanionAdjustBar(onEvent: (CompanionEvent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = menuPanel,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, menuBorder),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.companion_adjust_hint),
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                    ),
                )
                TextButton(onClick = { onEvent(CompanionEvent.ResetPose) }) {
                    Text(
                        stringResource(R.string.companion_adjust_reset),
                        fontFamily = FontFamily.SansSerif,
                    )
                }
                TextButton(onClick = { onEvent(CompanionEvent.FinishAdjust) }) {
                    Text(
                        stringResource(R.string.companion_adjust_done),
                        fontFamily = FontFamily.SansSerif,
                    )
                }
            }
        }
    }
}
