package com.chatwaifu.chat.core

import kotlinx.coroutines.flow.Flow

/**
 * Description: 聊天基座的统一入口。
 *
 * **只有 [chatStream] 一个抽象方法** —— 非流式是 [chat] 扩展函数，把流收完即可，
 * 这样新增一个 provider 不用把同样的映射逻辑写两遍。
 *
 * 实现约定：
 * 1. [chatStream] 返回的是**冷流**，collect 时才发请求；取消 collect 就要断连接。
 * 2. 失败抛 [ChatError] 的子类，不要泄漏 `HttpException` / `IOException`。
 * 3. 流正常结束前必须发且只发一个 [ChatDelta.Completed]。
 * 4. 历史由 [ChatSession] 在客户端维护，provider **不要**自己缓存上下文。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
interface ChatProvider {

    val id: ProviderId

    /** 设置页里显示的名字。 */
    val displayName: String

    val capabilities: ProviderCapabilities

    /** 静态模型清单，见 [ModelInfo]。 */
    val availableModels: List<ModelInfo>

    /** [ChatRequest.model] 为 null 时用的模型。 */
    val defaultModel: String

    fun chatStream(request: ChatRequest): Flow<ChatDelta>

    /** 释放连接池等资源。 */
    fun close() {}
}

/**
 * 非流式调用：把整段流收完，返回最终结果。
 *
 * 对所有 provider 只实现一遍。注意它仍然走的是流式请求 ——
 * 对基座来说没区别，对我们来说少维护一条代码路径。
 */
suspend fun ChatProvider.chat(request: ChatRequest): ChatResult {
    var result: ChatResult? = null
    chatStream(request).collect { delta ->
        if (delta is ChatDelta.Completed) {
            result = ChatResult(delta.message, delta.usage, delta.finishReason)
        }
    }
    return result ?: throw ChatError.Unknown("provider $displayName finished without a Completed event")
}
