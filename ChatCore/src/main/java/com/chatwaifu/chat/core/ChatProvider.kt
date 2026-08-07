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

    /**
     * 把附件预上传到基座侧，换回一个可以在请求里引用的 file id。
     *
     * **默认返回 null**，意思是「这个基座没有 Files API」，调用方继续走 base64 内联。
     * 不抛异常是刻意的：内联本来就是所有基座都支持的那条路，
     * 上传只是省流量/绕体积上限的优化，不该因为某个基座不支持就让整轮对话失败。
     *
     * 为什么需要它：内联会把附件塞进**每一轮**请求体（历史要重发全量），
     * 一张 3MB 的图 base64 后是 4MB，聊十轮就传了 40MB。换成 file id 只传一次。
     * 判断阈值见 [MediaUploadPolicy.shouldUpload]。
     *
     * 调用方（app 侧）负责把返回的 id 连同 provider 和过期时间一起存进附件记录，
     * 下一轮直接复用 —— 每轮都重新上传比内联还差。
     *
     * @param fileName 基座侧显示用的文件名，PDF 一类会被当成元信息读。
     */
    suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): MediaSource.RemoteFileId? = null

    /** 释放连接池等资源。 */
    fun close() {}
}

/**
 * 内联还是预上传的判断依据。
 *
 * 阈值取 1MB：再小的图 base64 之后也就一百多 KB，上传要多一次 RTT，不值；
 * 再大就开始明显撑请求体，而历史是每轮重发的，成本会随轮数累积。
 */
object MediaUploadPolicy {

    const val INLINE_MAX_BYTES = 1L * 1024 * 1024

    fun shouldUpload(byteSize: Long): Boolean = byteSize > INLINE_MAX_BYTES
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
