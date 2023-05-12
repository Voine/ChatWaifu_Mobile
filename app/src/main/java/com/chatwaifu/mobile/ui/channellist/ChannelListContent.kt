package com.chatwaifu.mobile.ui.channellist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme

/**
 * Description:
 * Author: Voine
 * Date: 2023/4/23
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListContent(
    channelListUiState: ChannelListUiState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    onChannelItemClick: (ChannelListBean) -> Unit = {}
) {
    val scrollState = rememberLazyListState()
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = "Character List",
                onNavIconPressed = onNavIconPressed,
                scrollBehavior = scrollBehavior,
            )
        },
        // Exclude ime and navigation bar padding so this can be added by the UserInput composable
        contentWindowInsets = ScaffoldDefaults
            .contentWindowInsets
            .exclude(WindowInsets.navigationBars)
            .exclude(WindowInsets.ime),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            channelListUiState.messages.forEachIndexed { i, it ->
                Divider(
                    modifier = Modifier
                        .height(1.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                ChannelListItem(
                    avatar = it.avatarDrawable,
                    name = it.characterName,
                    modifier = modifier.clickable { onChannelItemClick.invoke(it) }
                )
            }
            Divider(
                modifier = Modifier
                    .height(1.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        }
    }
}


@Composable
fun ChannelListItem(
    @DrawableRes avatar: Int,
    name: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = avatar),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(42.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .border(3.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clip(CircleShape)
                .align(Alignment.Top),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            modifier = modifier
                .wrapContentWidth()
                .align(Alignment.CenterVertically),
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    loadingText: String = "Loading VITS..."
) {
    Surface(
        modifier = modifier
            .width(200.dp)
            .height(150.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = modifier
                .background(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f))
                .padding(10.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                modifier = modifier.wrapContentWidth(),
                text = loadingText,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}


class ChannelListUiState(
    messages: List<ChannelListBean>
) {
    private val _messages: MutableList<ChannelListBean> = messages.toMutableStateList()
    val messages: List<ChannelListBean> = _messages

    fun addMessage(msg: ChannelListBean) {
        _messages.add(0, msg) // Add to the beginning of the list
    }
}

@Preview
@Composable
fun LoadingIndicatorPreview() {
    ChatWaifu_MobileTheme {
        LoadingIndicator()
    }
}

@Preview
@Composable
fun ChannelListItemPreview() {
    ChatWaifu_MobileTheme {
        ChannelListItem(R.drawable.yuuka_head, "yuuka")
    }
}

@Preview
@Composable
fun ChannelListContentPreview() {
    ChatWaifu_MobileTheme {
        ChannelListContent(
            channelListUiState = ChannelListUiState(exampleUIList)
        )
    }
}

@Preview
@Composable
fun ChannelListContentDarkPreview() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        ChannelListContent(
            channelListUiState = ChannelListUiState(exampleUIList)
        )
    }
}

val exampleUIList = listOf(
    ChannelListBean(
        avatarDrawable = R.drawable.yuuka_head,
        characterName = "yuuka"
    ),
    ChannelListBean(
        avatarDrawable = R.drawable.atri_head,
        characterName = "Atri"
    ),
    ChannelListBean(
        avatarDrawable = R.drawable.kurisu_head,
        characterName = "Kurisu"
    )
)




