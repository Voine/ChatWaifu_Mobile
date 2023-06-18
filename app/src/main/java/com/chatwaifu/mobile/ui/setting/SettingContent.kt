package com.chatwaifu.mobile.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chatwaifu.mobile.ui.common.ChannelNameBar
import com.chatwaifu.mobile.ui.theme.ChatWaifu_MobileTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.chatwaifu.chatgpt.ChatGPTNetService
import com.chatwaifu.mobile.R

/**
 * Description: Setting Page
 * Author: Voine
 * Date: 2023/4/24
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingContentScaffold(
    settingUIState: SettingUIState,
    modifier: Modifier = Modifier,
    onNavIconPressed: () -> Unit = { },
    onSave: (SettingUIData?) -> Unit = {},
) {
    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState)

    Scaffold(
        topBar = {
            ChannelNameBar(
                channelName = "Setting",
                onNavIconPressed = onNavIconPressed,
                externalActions = {
                    OutlinedButton(
                        onClick = { onSave(settingUIState.convertState2Data()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .padding(horizontal = 15.dp)
                            .height(35.dp),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        content = {
                            Text(text = stringResource(id = R.string.setting_btn_save))
                        }
                    )
                }
            )
        },
        ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            SettingContent(settingUIState)
        }
    }
}

@Composable
fun SettingContent(
    settingUIState: SettingUIState,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 30.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemTitle(stringResource(id = R.string.setting_title_chatgpt))
        SettingEditText(
            initValue = settingUIState.chatGPTAppId,
            hint = stringResource(id = R.string.setting_chatgpt_appid_hint)
        ) {
            settingUIState.chatGPTAppId = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        SettingSwitch(
            switchName = stringResource(id = R.string.setting_title_translate_switch),
            checked = settingUIState.translateSwitch
        ) {
            settingUIState.translateSwitch = it
        }
        AnimatedVisibility(visible = settingUIState.translateSwitch) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ItemTitle(stringResource(id = R.string.setting_title_translate_app_id))
                SettingEditText(initValue = settingUIState.translateAppId) {
                    settingUIState.translateAppId = it
                }
                DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
                ItemTitle(stringResource(id = R.string.setting_title_translate_app_key))
                SettingEditText(initValue = settingUIState.translateAppKey) {
                    settingUIState.translateAppKey = it
                }
            }
        }

        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        ItemTitle(stringResource(id = R.string.setting_title_yuuka_setting))
        SettingEditText(
            initValue = settingUIState.yuukaSetting,
            hint = stringResource(id = R.string.setting_yuuka_hint)
        ) {
            settingUIState.yuukaSetting = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        ItemTitle(stringResource(id = R.string.setting_title_atri_setting))
        SettingEditText(
            initValue = settingUIState.atriSetting,
            hint = stringResource(id = R.string.setting_atri_hint)
        ) {
            settingUIState.atriSetting = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        ItemTitle(stringResource(id = R.string.setting_title_amadeus_setting))
        SettingEditText(
            initValue = settingUIState.amaduesSetting,
            hint = stringResource(id = R.string.setting_amadeus_hint)
        ) {
            settingUIState.amaduesSetting = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        ItemTitle(stringResource(id = R.string.setting_title_external_model_setting))
        SettingEditText(
            initValue = settingUIState.externalSetting,
            hint = stringResource(id = R.string.setting_external_model_hint)
        ) {
            settingUIState.externalSetting = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        ItemTitle(stringResource(id = R.string.setting_title_external_model_speakerid))
        SettingEditText(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            initValue = settingUIState.externalModelSpeakerId.toString(),
            hint = stringResource(id = R.string.setting_external_model_speaker_id),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            singleLine = true
        ) {
            settingUIState.externalModelSpeakerId = try { it.toInt() } catch (e:Exception){0}
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        SettingSwitch(
            stringResource(id = R.string.setting_title_darkmode_switch),
            settingUIState.darkModeSwitch
        ) {
            settingUIState.darkModeSwitch = it
        }
        DividerItem(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        SettingSwitch(
            switchName = stringResource(id = R.string.setting_title_use_gpt_proxy),
            checked = settingUIState.gptProxySwitch
        ) {
            settingUIState.gptProxySwitch = it
        }
        AnimatedVisibility(visible = settingUIState.gptProxySwitch) {
            SettingEditText(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                initValue = settingUIState.gptProxyUrl.toString(),
                hint = stringResource(id = R.string.setting_gpt_proxy_hint),
                singleLine = true
            ) {
                settingUIState.gptProxyUrl = it.trim()
            }
        }

    }
}

@Composable
fun SettingSwitch(switchName: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = switchName,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )

        val icon: (@Composable () -> Unit)? = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = icon,
            modifier = Modifier
                .scale(0.65f)
        )
    }
}


@Composable
fun SettingEditText(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(150.dp),
    initValue: String? = null,
    hint: String = "this is a hint",
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onValueChanged: (String) -> Unit = {}
) {
    var value by rememberSaveable { mutableStateOf(initValue) }
    Box(
        modifier =  modifier,
    ) {
        val showHint = value.isNullOrEmpty()
        BasicTextField(
            value = value ?: "",
            onValueChange = {
                value = it
                onValueChanged(it)
            },
            decorationBox = { innerTextField ->
                // Because the decorationBox is used, the whole Row gets the same behaviour as the
                // internal input field would have otherwise. For example, there is no need to add a
                // Modifier.clickable to the Row anymore to bring the text field into focus when user
                // taps on a larger text field area which includes paddings and the icon areas.
                Box(modifier = Modifier, contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                                RoundedCornerShape(percent = 5)
                            )
                            .padding(5.dp)
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        innerTextField()
                    }
                    if (showHint) {
                        Text(
                            text = hint,
                            style = TextStyle.Default.copy(
                                color = MaterialTheme.colorScheme.onSecondary.copy(
                                    alpha = 0.8f,
                                )
                            ),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            },
            keyboardOptions = keyboardOptions,
            singleLine = singleLine
        )
    }
}

@Composable
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    width: Dp = 80.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    return drawWithContent {
        drawContent()

        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        // Draw scrollbar if scrolling or if the animation is still running and lazy column has content
        if (needDrawScrollbar && firstVisibleElementIndex != null) {
            val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

            drawRect(
                color = Color.Red,
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                alpha = alpha
            )
        }
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
private fun ItemTitle(text: String) {
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
fun previewEditText() {
    ChatWaifu_MobileTheme {
        SettingEditText()
    }
}


@Preview
@Composable
fun SettingContentScaffoldPreview() {
    ChatWaifu_MobileTheme {
        SettingContentScaffold(
            SettingUIState(exampleSettingUi)
        )
    }
}

@Preview
@Composable
fun SettingContentScaffoldPreviewDark() {
    ChatWaifu_MobileTheme(darkTheme = true) {
        SettingContentScaffold(
            SettingUIState(exampleSettingUi)
        )
    }
}


/**
 * Setting UI Render State
 */
class SettingUIState(data: SettingUIData) {
    var chatGPTAppId by mutableStateOf(data.chatGPTAppId)
    var translateSwitch by mutableStateOf(data.translateSwitch)
    var translateAppId by mutableStateOf(data.translateAppId)
    var translateAppKey by mutableStateOf(data.translateAppKey)
    var yuukaSetting by mutableStateOf(data.yuukaSetting)
    var amaduesSetting by mutableStateOf(data.amaduesSetting)
    var atriSetting by mutableStateOf(data.atriSetting)
    var externalSetting by mutableStateOf(data.externalSetting)
    var darkModeSwitch by mutableStateOf(data.darkModeSwitch)
    var externalModelSpeakerId by mutableStateOf(data.externalModelSpeakerId)
    var gptProxyUrl by mutableStateOf(data.gptProxyUrl)
    var gptProxySwitch by mutableStateOf(data.gptProxySwitch)

    fun convertState2Data(): SettingUIData {
        return SettingUIData(
            chatGPTAppId = chatGPTAppId,
            translateSwitch = translateSwitch,
            translateAppId = translateAppId,
            translateAppKey = translateAppKey,
            yuukaSetting = yuukaSetting,
            amaduesSetting = amaduesSetting,
            atriSetting = atriSetting,
            externalSetting = externalSetting,
            darkModeSwitch = darkModeSwitch,
            externalModelSpeakerId = externalModelSpeakerId,
            gptProxyUrl = gptProxyUrl,
            gptProxySwitch = gptProxySwitch
        )
    }
}

/**
 * Setting Data
 */
@Immutable
data class SettingUIData(
    var chatGPTAppId: String = "",
    var translateSwitch: Boolean = true,
    var translateAppId: String = "",
    var translateAppKey: String = "",
    var yuukaSetting: String = "",
    var amaduesSetting: String = "",
    var atriSetting: String = "",
    var externalSetting: String = "",
    var darkModeSwitch: Boolean = false,
    var externalModelSpeakerId: Int = 0,
    var gptProxySwitch: Boolean = false,
    var gptProxyUrl: String? = ChatGPTNetService.CHATGPT_DEAFULT_PROXY_URL
)

val exampleSettingUi = SettingUIData(
    chatGPTAppId = "",
    translateSwitch = true,
    translateAppId = "example translate app id .....",
    translateAppKey = "example translate app key.....",
    yuukaSetting = "example yuuka setting",
    amaduesSetting = "example amadeus setting",
    atriSetting = "example atri setting...",
    externalSetting = "example external setting....",
    darkModeSwitch = false,
    externalModelSpeakerId = 123456
)