package com.chatwaifu.mobile.ui.common


/**
 * Description: Chat Dialog Content Compose UI State
 * 仅在聊天框处使用 state ，是因为聊天框的数据来源不唯一，因此抽一层 state...
 * Author: Voine
 * Date: 2023/5/12
 */
class ChatDialogContentUIState (
    var isInitState: Boolean = false,
    var isFromMe: Boolean = false,
    var chatContent: String = "",
    val errorMsg: String? = null
)