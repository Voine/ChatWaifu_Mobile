@file:OptIn(ExperimentalMaterial3Api::class)
package com.chatwaifu.mobile.ui.chatlog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.common.JumpToBottom
import com.chatwaifu.mobile.ui.common.Message
import com.chatwaifu.mobile.ui.common.SymbolAnnotationType
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.channellist.LoadingIndicator
import com.chatwaifu.mobile.ui.common.ConversationContent
import com.chatwaifu.mobile.ui.common.exampleUiMsg2
import com.chatwaifu.mobile.ui.common.exampleUiState
import com.chatwaifu.mobile.ui.common.initialMessages
import com.chatwaifu.mobile.ui.common.messageFormatter
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import kotlinx.coroutines.launch

/**
 * Description: ChatLog Page
 * Author: Voine
 * Date: 2023/4/29
 */
@Composable
fun ChatLogContent(
    channelName: String = Constant.LOCAL_MODEL_YUUKA,
    navigateToProfile: (String) -> Unit = {},
    onNavIconPressed: () -> Unit = { },
    chatLogViewModel: ChatLogViewModel = viewModel(),
    externalModelList: List<String> = emptyList()
) {
    var currentName by rememberSaveable { mutableStateOf(channelName) }
    val showList = chatLogViewModel.chatLogData.observeAsState()
    ChatLogContentScaffold(
        chatChannelName = currentName,
        chatLogUiState = ChatLogUiState(channelName, showList.value ?: emptyList()),
        onChatCharacterClick = {
            if (it != currentName) {
                chatLogViewModel.loadAllChatMessage(it)
                currentName = it
            }
        },
        externalModelList = externalModelList,
        onNavIconPressed = onNavIconPressed,
        navigateToProfile = navigateToProfile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatLogContentScaffold(
    modifier: Modifier = Modifier,
    chatChannelName: String = Constant.LOCAL_MODEL_YUUKA,
    chatLogUiState: ChatLogUiState,
    navigateToProfile: (String) -> Unit = {},
    onChatCharacterClick: (String) -> Unit = {},
    onNavIconPressed: () -> Unit = { },
    externalModelList: List<String> = emptyList(),
) {
    val topBarState = rememberTopAppBarState()
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    Scaffold(
        topBar = {
            ChatLogAppBar(
                chatChannelName,
                onNavIconPressed = onNavIconPressed,
                onChatCharacterClick = onChatCharacterClick,
                externalModelList = externalModelList
            )
        },
    ) { paddingValues ->
        if (chatLogUiState.messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ErrorHint()
            }
        } else {
            Messages(
                messages = chatLogUiState.messages,
                navigateToProfile = navigateToProfile,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                scrollState = scrollState
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatLogAppBar(
    chatName: String,
    onNavIconPressed: () -> Unit = { },
    // modelName list
    externalModelList: List<String> = emptyList(),
    // modelName
    onChatCharacterClick: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val yuukaName = Constant.LOCAL_MODEL_YUUKA
    val atriName = Constant.LOCAL_MODEL_ATRI
    val kurisuName = Constant.LOCAL_MODEL_AMADEUS

    ChannelNameBar(
        channelName = chatName,
        onNavIconPressed = onNavIconPressed,
        externalActions = {
            IconButton(onClick = { menuExpanded = !menuExpanded }) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = "下拉菜单按钮",
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.background)
            ) {
                DropdownMenuItem(
                    text = { Text(text = yuukaName) },
                    leadingIcon = {
                        Image(
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(30.dp),
                            painter = painterResource(id = R.drawable.yuuka_head),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onChatCharacterClick(yuukaName)
                    })

                DropdownMenuItem(
                    text = { Text(text = atriName) },
                    leadingIcon = {
                        Image(
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(30.dp),
                            painter = painterResource(id = R.drawable.atri_head),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onChatCharacterClick(atriName)
                    })
                DropdownMenuItem(
                    text = { Text(text = kurisuName) },
                    leadingIcon = {
                        Image(
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(30.dp),
                            painter = painterResource(id = R.drawable.kurisu_head),
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onChatCharacterClick(kurisuName)
                    })

                externalModelList.forEach {name ->
                    DropdownMenuItem(
                        text = { Text(text = name) },
                        leadingIcon = {
                            Icon(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .size(30.dp),
                                painter = painterResource(id = R.drawable.external_default_icon),
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onChatCharacterClick(name)
                        }
                    )
                }
            }
        }
    )
}

@Composable
fun ErrorHint() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_cant_find_any_log),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(id = R.string.chat_log_empty_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun Messages(
    messages: List<Message>,
    navigateToProfile: (String) -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    Box(modifier = modifier) {

        val authorMe = stringResource(id = R.string.author_me)
        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            modifier = Modifier
                .testTag(ChatTestTag)
                .fillMaxSize()
        ) {
            for (index in messages.indices) {
                val prevAuthor = messages.getOrNull(index - 1)?.author
                val nextAuthor = messages.getOrNull(index + 1)?.author
                val content = messages[index]
                val isFirstMessageByAuthor = prevAuthor != content.author
                val isLastMessageByAuthor = nextAuthor != content.author

                item {
                    Message(
                        onAuthorClick = { name -> navigateToProfile(name) },
                        msg = content,
                        isUserMe = content.author == authorMe,
                        isFirstMessageByAuthor = isFirstMessageByAuthor,
                        isLastMessageByAuthor = isLastMessageByAuthor
                    )
            }
            }
        }
        // Jump to bottom button shows up when user scrolls past a threshold.
        // Convert to pixels:
        val jumpThreshold = with(LocalDensity.current) {
            JumpToBottomThreshold.toPx()
        }

        // Show the button if the first visible item is not the first one or if the offset is
        // greater than the threshold.
        val jumpToBottomButtonEnabled by remember {
            derivedStateOf {
                scrollState.firstVisibleItemIndex != 0 ||
                        scrollState.firstVisibleItemScrollOffset > jumpThreshold
            }
        }

        JumpToBottom(
            // Only show if the scroller is not at the bottom
            enabled = jumpToBottomButtonEnabled,
            onClicked = {
                scope.launch {
                    scrollState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun Message(
    onAuthorClick: (String) -> Unit,
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean
) {
    val borderColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    val spaceBetweenAuthors = if (isLastMessageByAuthor) Modifier.padding(top = 8.dp) else Modifier
    Row(modifier = spaceBetweenAuthors) {
        if (isLastMessageByAuthor) {
            // Avatar
            Image(
                modifier = Modifier
                    .clickable(onClick = { onAuthorClick(msg.author) })
                    .padding(horizontal = 16.dp)
                    .size(42.dp)
                    .border(1.5.dp, borderColor, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .align(Alignment.Top),
                painter = painterResource(id = msg.authorImage),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        } else {
            // Space under avatar
            Spacer(modifier = Modifier.width(74.dp))
        }
        AuthorAndTextMessage(
            msg = msg,
            isUserMe = isUserMe,
            isFirstMessageByAuthor = isFirstMessageByAuthor,
            isLastMessageByAuthor = isLastMessageByAuthor,
            authorClicked = onAuthorClick,
            modifier = Modifier
                .padding(end = 16.dp)
                .weight(1f)
        )
    }
}

@Composable
fun AuthorAndTextMessage(
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    authorClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (isLastMessageByAuthor) {
            AuthorNameTimestamp(msg)
        }
        ChatItemBubble(msg, isUserMe, authorClicked = authorClicked)
        if (isFirstMessageByAuthor) {
            // Last bubble before next author
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            // Between bubbles
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AuthorNameTimestamp(msg: Message) {
    // Combine author and timestamp for a11y.
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.author,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 8.dp) // Space to 1st bubble
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = msg.timestamp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignBy(LastBaseline),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(16.dp)
    ) {
        DayHeaderLine()
        Text(
            text = dayString,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DayHeaderLine()
    }
}

@Composable
private fun RowScope.DayHeaderLine() {
    Divider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
fun ChatItemBubble(
    message: Message,
    isUserMe: Boolean,
    authorClicked: (String) -> Unit
) {

    val backgroundBubbleColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column {
        Surface(
            color = backgroundBubbleColor,
            shape = ChatBubbleShape
        ) {
            ClickableMessage(
                message = message,
                isUserMe = isUserMe,
                authorClicked = authorClicked
            )
        }

        message.image?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = ChatBubbleShape
            ) {
                Image(
                    painter = painterResource(it),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image)
                )
            }
        }
    }
}

@Composable
fun ClickableMessage(
    message: Message,
    isUserMe: Boolean,
    authorClicked: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val styledMessage = messageFormatter(
        text = message.content,
        primary = isUserMe
    )

    ClickableText(
        text = styledMessage,
        style = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
        modifier = Modifier.padding(16.dp),
        onClick = {
            styledMessage
                .getStringAnnotations(start = it, end = it)
                .firstOrNull()
                ?.let { annotation ->
                    when (annotation.tag) {
                        SymbolAnnotationType.LINK.name -> uriHandler.openUri(annotation.item)
                        SymbolAnnotationType.PERSON.name -> authorClicked(annotation.item)
                        else -> Unit
                    }
                }
        }
    )
}

@Preview
@Composable
fun ChatLogContentScaffoldPreview() {
    ChatWaifu_MobileTheme {
        ChatLogContentScaffold(
            chatLogUiState = exampleChatLogUIState,
            externalModelList = listOf("Other Model")
        )
    }
}

@Preview
@Composable
fun DayHeaderPrev() {
    DayHeader("Aug 6")
}

@Preview
@Composable
fun ChatLogContentScaffoldDarkPreview() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        ChatLogContentScaffold(
            chatLogUiState = exampleChatLogUIState,
            externalModelList = listOf("Other Model")
        )
    }
}

@Preview
@Composable
fun DayHeaderDarkPrev() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        DayHeader("Aug 6")
    }
}

@Preview
@Composable
fun ErrorHintPreview() {
    ChatWaifu_MobileTheme {
        ErrorHint()
    }
}

class ChatLogUiState(
    val channelName: String,
    initialMessages: List<Message>
) {
    private val _messages: MutableList<Message> = initialMessages.toMutableStateList()
    val messages: List<Message> = _messages
}

private const val ChatTestTag = "ChatLogTestTag"
private val ChatBubbleShape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
private val JumpToBottomThreshold = 56.dp

val exampleChatLogUIState = ChatLogUiState(
    initialMessages = exampleUiMsg2,
    channelName = "yuuka",
)

