package com.chatwaifu.chat.core

/**
 * Description: 会话内可供模型调用的工具。[ChatSession] 拿到 `ToolCall` 时找它执行，
 * 把结果回填成 `ToolResult` 再请求一次，直到模型给出不带工具调用的回复。
 *
 * 和 [ChatOptions.tools] 的关系：那里是「只声明、不执行」的原始入口（调用方自己处理
 * `FinishReason.TOOL_CALL`）；挂了这个之后由会话自己跑完工具循环，调用方看到的仍是
 * 「一轮一个 [ChatDelta.Completed]」。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
interface ToolHandler {

    /** 要声明给模型的工具。每轮请求前取一次，可以随状态变（比如没有可查的记忆时返回空） */
    suspend fun specs(): List<ToolSpec>

    /**
     * 执行一次调用。抛异常会被转成 `isError = true` 的结果交还模型，不会中断对话。
     *
     * @param argumentsJson 模型给的原始参数 JSON，可能不合法，实现方自己兜
     * @return 给模型看的结果文本
     */
    suspend fun call(name: String, argumentsJson: String): String
}
