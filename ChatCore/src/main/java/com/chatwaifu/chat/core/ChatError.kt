package com.chatwaifu.chat.core

/**
 * Description: 统一的错误模型。
 *
 * 存在的意义是让 UI 能按语义做反应（提示去填 key / 提示稍后重试 / 提示换个说法），
 * 而不是像现在这样把基座返回的原始 JSON 直接 Toast 出来。
 *
 * 各 provider 负责把自家错误映射过来，常见来源：
 * - HTTP 401 / 403 → [Auth]
 * - HTTP 429（读 `Retry-After` 头）→ [RateLimited]
 * - OpenAI/Azure `error.code == "content_filter"`、Gemini `promptFeedback.blockReason` → [ContentFiltered]
 * - Anthropic `stop_reason == "refusal"` → [Refused]
 * - 上下文超长（各家 message 文案不同，靠 code 判断）→ [ContextOverflow]
 *
 * Author: Voine
 * Date: 2026/8/6
 */
sealed class ChatError(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 密钥缺失、错误或已失效。 */
    class Auth(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)

    /** 限流。[retryAfterSeconds] 来自 `Retry-After`，可能为 null。 */
    class RateLimited(
        val retryAfterSeconds: Long? = null,
        message: String? = null,
        cause: Throwable? = null,
    ) : ChatError(message, cause)

    /** 连不上、DNS 失败、连接中断。 */
    class Network(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)

    class Timeout(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)

    /** 上下文超出模型窗口。调用方应该收紧 [ContextBudget] 后重试。 */
    class ContextOverflow(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)

    /** 被平台的内容安全策略拦截。 */
    class ContentFiltered(
        val category: String? = null,
        message: String? = null,
        cause: Throwable? = null,
    ) : ChatError(message, cause)

    /** 模型自己拒答。 */
    class Refused(
        val category: String? = null,
        message: String? = null,
        cause: Throwable? = null,
    ) : ChatError(message, cause)

    class ModelNotFound(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)

    /**
     * 请求里用了当前 provider 不支持的能力（比如给纯文本基座发图片）。
     * 这是**显式报错而不是静默丢弃** —— 悄悄丢掉附件比报错更难排查。
     */
    class CapabilityUnsupported(
        val what: String,
        message: String? = null,
    ) : ChatError(message ?: "current provider does not support $what")

    class ServerError(
        val httpCode: Int,
        message: String? = null,
        cause: Throwable? = null,
    ) : ChatError(message, cause)

    class Unknown(message: String? = null, cause: Throwable? = null) : ChatError(message, cause)
}
