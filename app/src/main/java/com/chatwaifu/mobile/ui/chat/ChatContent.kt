package com.chatwaifu.mobile.ui.chat

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatwaifu.mobile.ChatActivityViewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.common.InputSelector
import com.chatwaifu.mobile.ui.common.UserInput
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import com.chatwaifu.mobile.ui.theme.Color_55

/**
 * Description: Chat Page
 * Author: Voine
 * Date: 2023/4/28
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContentScaffold(
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    chatTitle: String = "yuuka",
    sendMessageTitle: String = "yuuka",
    sendMessageContent: String = "example content",
    originAndroidView: (Context) -> View,
    originAndroidViewUpdate: (View) -> Unit = {},
    onSendMsgButtonClick: (String) -> Unit = {},
    onErrorOccur: (String) -> Unit = {},
    onTouchStart: () -> Unit = {},
    onTouchEnd: () -> Unit = {},
    onResetModel: () -> Unit = {},
    onRecordStart: () -> Unit = {},
    onRecordEnd: () -> Unit = {},
    chatActivityViewModel: ChatActivityViewModel
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val vitsGenerateStatusLiveData = chatActivityViewModel.generateSoundLiveData.observeAsState()
    val chatStatusUILiveData = chatActivityViewModel.chatStatusLiveData.observeAsState()
    if (vitsGenerateStatusLiveData.value == false) {
        onErrorOccur("sound generate failed....")
    }
    val chatStatusHint = when (chatStatusUILiveData.value) {
        ChatActivityViewModel.ChatStatus.SEND_REQUEST -> {
            stringResource(id = R.string.chat_status_hint_send_request)
        }

        ChatActivityViewModel.ChatStatus.GENERATE_SOUND -> {
            stringResource(id = R.string.chat_status_hint_generate_sound)
        }

        ChatActivityViewModel.ChatStatus.TRANSLATE -> {
            stringResource(id = R.string.chat_status_hint_translate)
        }

        else -> {
            ""
        }
    }

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = chatTitle,
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
                externalActions = {}
            )
        },
        // Exclude ime and navigation bar padding so this can be added by the UserInput composable
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ChatContent(
                originAndroidView,
                originAndroidViewUpdate,
                chatTitle = sendMessageTitle,
                chatContent = sendMessageContent,
                chatStatus = chatStatusHint,
                onSendMsgButtonClick = onSendMsgButtonClick,
                onTouchStart = onTouchStart,
                onTouchEnd = onTouchEnd,
                onRecordStart = onRecordStart,
                onRecordEnd = onRecordEnd,
                onResetModel = onResetModel
            )
        }
    }
}

@Composable
fun ChatContent(
    originAndroidView: (Context) -> View,
    originAndroidViewUpdate: (View) -> Unit = {},
    chatTitle: String = "example title",
    chatContent: String = stringResource(id = R.string.sample_very_long_str),
    chatStatus: String = "example status",
    onSendMsgButtonClick: (String) -> Unit = {},
    onTouchStart: () -> Unit = {},
    onTouchEnd: () -> Unit = {},
    onResetModel: () -> Unit = {},
    onRecordStart: () -> Unit = {},
    onRecordEnd: () -> Unit = {}
) {

    var touchModeEnable by rememberSaveable { mutableStateOf(false) }
    var currentInputSelector by rememberSaveable { mutableStateOf(InputSelector.NONE) }
    Box(modifier = Modifier
        .fillMaxSize()
        .clickable(
            enabled = !touchModeEnable,
            onClick = {
                currentInputSelector = InputSelector.NONE
            }
        ), contentAlignment = Alignment.BottomCenter) {
        AndroidView(
            modifier = Modifier.fillMaxSize(), // Occupy the max size in the Compose UI tree
            factory = { context ->
                // Creates view
                originAndroidView(context)
            },
            update = { view ->
                // View's been inflated or state read in this block has been updated
                // Add logic here if necessary

                // As selectedItem is read here, AndroidView will recompose
                // whenever the state changes
                // Example of Compose -> View communication
                originAndroidViewUpdate(view)
            }
        )
        Column(modifier = Modifier) {
            if (touchModeEnable) {
                TouchIndicator(
                    onDismissClick = {
                        touchModeEnable = false
                        onTouchEnd()
                    },
                    onResetModelClick = onResetModel
                )
            } else {
                Column(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 15.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    Text(
                        text = "$chatTitle:",
                        modifier = Modifier
                            .padding(start = 15.dp, end = 15.dp, top = 15.dp, bottom = 10.dp)
                            .fillMaxWidth(),
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = chatContent,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = chatStatus,
                        modifier = Modifier
                            .padding(start = 15.dp, end = 15.dp, top = 10.dp, bottom = 5.dp)
                            .fillMaxWidth(),
                        fontSize = 10.sp,
                        color = Color_55
                    )
                }
            }

            AnimatedVisibility(visible = !touchModeEnable) {
                UserInput(
                    onMessageSent = onSendMsgButtonClick,
                    currentSelector = currentInputSelector,
                    onTouchButtonClick = {
                        touchModeEnable = true
                        onTouchStart()
                    },
                    onRecordStart = onRecordStart,
                    onRecordEnd = onRecordEnd,
                    selectChangeFunc = {
                        currentInputSelector = it
                    }
                )
            }
        }
    }
}


@Composable
fun TouchIndicator(
    onDismissClick: () -> Unit = {},
    onResetModelClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopEnd,
    ) {

        Column {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(30.dp)
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onDismissClick, modifier = Modifier
                ) {
                    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    Icon(
                        Icons.Outlined.CloseFullscreen,
                        tint = tint,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(5.dp),
                        contentDescription = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(30.dp)
                    .background(
                        MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onResetModelClick, modifier = Modifier
                ) {
                    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    Icon(
                        Icons.Outlined.Autorenew,
                        tint = tint,
                        modifier = Modifier
                            .padding(5.dp),
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ChatContentPreview() {
    ChatWaifu_MobileTheme {
        val context = LocalContext.current
        ChatContent(originAndroidView = {
            View(context).apply {
                setBackgroundColor(resources.getColor(androidx.appcompat.R.color.material_blue_grey_800))
            }
        })
    }
}


@Preview
@Composable
fun ChatContentScaffoldPreview() {
    ChatWaifu_MobileTheme {
        val context = LocalContext.current
        ChatContentScaffold(
            originAndroidView = {
                View(context).apply {
                    setBackgroundColor(resources.getColor(androidx.appcompat.R.color.material_blue_grey_800))
                }
            },
            chatActivityViewModel = viewModel()
        )
    }
}

@Preview
@Composable
fun ChatContentScaffoldPreviewDark() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        val context = LocalContext.current
        ChatContentScaffold(
            originAndroidView = {
                View(context).apply {
                    setBackgroundColor(resources.getColor(androidx.appcompat.R.color.material_blue_grey_800))
                }
            },
            chatActivityViewModel = viewModel()
        )
    }
}

@Preview
@Composable
fun TouchIndicatorPreview() {
    TouchIndicator()
}