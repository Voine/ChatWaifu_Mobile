package com.chatwaifu.mobile.ui.base

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.databinding.ContentMainBinding
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import kotlinx.coroutines.launch

/**
 * Description: Main Activity View
 * Author: Voine
 * Date: 2023/4/30
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWaifuRootView(
    chatViewModel: ChatActivityViewModel = viewModel(),
    onChannelListClick: ()->Unit = {},
    onChatLogClick: () ->Unit= {},
    onSettingClick: ()->Unit= {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerOpen by chatViewModel.drawerShouldBeOpened.observeAsState(initial = false)
    if (drawerOpen) {
        // Open drawer and reset state in VM.
        LaunchedEffect(Unit) {
            // wrap in try-finally to handle interruption whiles opening drawer
            try {
                drawerState.open()
            } finally {
                chatViewModel.resetOpenDrawerAction()
            }
        }
    }

    // Intercepts back navigation when the drawer is open
    val scope = rememberCoroutineScope()
    if (drawerState.isOpen) {
        BackHandler {
            scope.launch {
                drawerState.close()
            }
        }
    }

    ChatWaifuDrawerScaffold(
        drawerState = drawerState,
        onItemClicked = {navigationItemType ->
            when (navigationItemType) {
                NavigationItemType.TYPE_CHANNEL_LIST -> {
                    scope.launch {
                        drawerState.close()
                    }
                    onChannelListClick()
                }
                NavigationItemType.TYPE_CHAT_LOG ->{
                    scope.launch {
                        drawerState.close()
                    }
                    onChatLogClick()
                }
                NavigationItemType.TYPE_SETTING -> {
                    scope.launch {
                        drawerState.close()
                    }
                    onSettingClick()
                }
            }
        }
    ) {
        AndroidViewBinding(ContentMainBinding::inflate)
    }
}

enum class NavigationItemType{
    TYPE_SETTING,
    TYPE_CHAT_LOG,
    TYPE_CHANNEL_LIST,
}