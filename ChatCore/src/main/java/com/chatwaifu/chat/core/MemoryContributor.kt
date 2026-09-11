package com.chatwaifu.chat.core

/**
 * Description: 往 system 区注入长期记忆的钩子。
 *
 * **为什么是接口而不是实现**：记忆要读 Room，而 `ChatCore` 对存储一无所知——
 * 这个边界是这一层能被单独测试、能换 provider 不断上下文的前提，不能为了记忆破掉。
 * 所以这里只留一个函数形状，实现放 app 层（它同时看得见 `Log` 和 `ChatCore`）。
 *
 * **为什么注进 system 区而不是当成一条消息**：
 * - 消息列表的首条必须是 user（Anthropic 硬要求），插伪消息要额外处理和裁剪的关系
 * - 记忆变化很慢，放在 system 区等于放进缓存前缀，绝大多数轮次都能命中
 *
 * 注意这和将来的「检索片段」正好相反：那种东西每轮都不同，必须注在**最后一条 user
 * 消息之前**，否则每轮都会打穿前缀缓存。
 *
 * Author: Voine
 * Date: 2026/9/11
 */
fun interface MemoryContributor {

    /**
     * 返回要注入的记忆块，没有就返回 null。
     *
     * **每轮请求前都会调一次**，实现方自己决定要不要缓存（记忆变化远慢于对话轮次，
     * 通常应该缓存，被抽取改写时再失效）。
     *
     * @param budgetTokens 额度上限（[ContextBudget.memoryTokens]）。超了不会被截断，
     *   而是会挤占工作记忆的份额，所以**由实现方按优先级自行砍**。
     */
    suspend fun memoryBlock(budgetTokens: Int): String?
}
