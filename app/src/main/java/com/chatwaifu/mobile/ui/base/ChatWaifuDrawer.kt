/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chatwaifu.mobile.ui.base

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

/**
 * drawer scaffold
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWaifuDrawerScaffold(
    drawerState: DrawerState = rememberDrawerState(initialValue = DrawerValue.Closed),
    onItemClicked: (NavigationItemType) -> Unit,
    content: @Composable () -> Unit,
) {
    ChatWaifu_MobileTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(240.dp)) {
                    ChatWaifuDrawerContent(
                        onItemClicked = onItemClicked
                    )
                }
            },
            content = content,
            gesturesEnabled = false
        )
    }
}

/**
 * drawer content
 */
@Composable
fun ChatWaifuDrawerContent(
    onItemClicked: (NavigationItemType) -> Unit
) {
    // Use windowInsetsTopHeight() to add a spacer which pushes the drawer content
    // below the status bar (y-axis)
    var selectItem by rememberSaveable {
        mutableStateOf(NavigationItemType.TYPE_CHANNEL_LIST)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        DrawerHeader()
        DividerItem()
        DrawerItemHeader(stringResource(id = R.string.drawer_function_list))
        DrawerItem(
            stringResource(id = R.string.drawer_item_channel_list),
            selectItem == NavigationItemType.TYPE_CHANNEL_LIST,
            R.drawable.ic_drawer_character_list
        ) {
            selectItem = NavigationItemType.TYPE_CHANNEL_LIST
            onItemClicked(NavigationItemType.TYPE_CHANNEL_LIST)
        }
        DrawerItem(
            stringResource(id = R.string.drawer_item_chat),
            selectItem == NavigationItemType.TYPE_CHAT_LOG,
            R.drawable.ic_drawer_character_log
        ) {
            selectItem = NavigationItemType.TYPE_CHAT_LOG
            onItemClicked(NavigationItemType.TYPE_CHAT_LOG)
        }
        DrawerItem(
            stringResource(id = R.string.drawer_item_setting),
            selectItem == NavigationItemType.TYPE_SETTING,
            R.drawable.ic_drawer_character_setting
        ) {
            selectItem = NavigationItemType.TYPE_SETTING
            onItemClicked(NavigationItemType.TYPE_SETTING)
        }
    }
}

@Composable
private fun DrawerItem(
    text: String,
    selected: Boolean,
    @DrawableRes drawableRes: Int,
    onChatClicked: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.background)

    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(30))
            .background(backgroundColor)
            .clickable(onClick = onChatClicked),
        verticalAlignment = CenterVertically
    ) {
        val iconTint = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Icon(
            painter = painterResource(id = drawableRes),
            tint = iconTint,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            contentDescription = null
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun DrawerHeader() {
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = CenterVertically) {
        ChatWaifuChatIcon(
            modifier = Modifier.size(24.dp)
        )
        Image(
            painter = painterResource(id = R.drawable.ic_chatwaifu),
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .scale(0.9f),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
    }
}

@Composable
private fun DrawerItemHeader(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .padding(horizontal = 28.dp),
        contentAlignment = CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DividerItem(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
@Preview
fun DrawerPreview() {
    ChatWaifu_MobileTheme() {
        Surface {
            Column {
                ChatWaifuDrawerContent {}
            }
        }
    }
}

@Composable
@Preview
fun DrawerPreviewDark() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        Surface {
            Column {
                ChatWaifuDrawerContent {}
            }
        }
    }
}
