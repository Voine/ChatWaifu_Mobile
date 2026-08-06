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
    val errorMsg: String? = null,
    /**
     * 流式输出的中间态。
     *
     * 存在的理由：气泡现在是逐字刷新的，首帧的 [chatContent] 可能还是空串，
     * 而渲染侧「内容为空就弹 response empty」的兜底逻辑会误判。
     */
    val isStreaming: Boolean = false,
)