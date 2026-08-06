package com.chatwaifu.chat.net

import okhttp3.ResponseBody

/**
 * Description: 极简 SSE 解析。
 *
 * 没有引 `okhttp-sse`：SSE 的帧格式就是「`field: value` 行 + 空行分帧」，
 * 手写不到 40 行，比多一个 catalog 条目 + 一层 EventSource 回调桥接更划算。
 * 三家基座的流式接口都是 SSE，所以这一份被所有 provider 共用。
 *
 * 用法（**必须在 IO 线程**，`readUtf8Line` 是阻塞的）：
 * ```
 * body.collectSse { event -> ... }
 * ```
 *
 * Author: Voine
 * Date: 2026/8/6
 */
data class SseEvent(
    /** `event:` 字段。OpenAI Responses / Anthropic 靠它区分事件类型；Chat Completions 不发这个字段。 */
    val event: String?,
    /** `data:` 字段，多行会按 SSE 规范用 `\n` 拼接。 */
    val data: String,
)

/** SSE 里表示流结束的哨兵值，OpenAI Chat Completions 协议在用。 */
const val SSE_DONE = "[DONE]"

/**
 * 逐帧读完整个响应体。[onEvent] 抛异常或调用方取消协程时，会关闭底层连接。
 */
suspend inline fun ResponseBody.collectSse(crossinline onEvent: suspend (SseEvent) -> Unit) {
    source().use { source ->
        var eventName: String? = null
        val data = StringBuilder()

        while (true) {
            val line = source.readUtf8Line() ?: break

            when {
                // 空行 = 一帧结束
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        onEvent(SseEvent(eventName, data.toString()))
                    }
                    eventName = null
                    data.setLength(0)
                }

                // 注释帧，各家用来做心跳保活
                line.startsWith(":") -> Unit

                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()

                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trim())
                }

                // id: / retry: 等字段用不到
                else -> Unit
            }
        }

        // 结尾没有空行的兜底
        if (data.isNotEmpty()) {
            onEvent(SseEvent(eventName, data.toString()))
        }
    }
}
