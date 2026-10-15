package com.chatwaifu.chat.core

/**
 * Description: 给**这一轮**的用户消息附一段上下文备注（现在几点、隔了多久没聊、自动想起的往事）。
 *
 * 和 [MemoryContributor] 是一对，区别在「变化频率」决定了放哪：
 * - [MemoryContributor]：变得很慢（事实、近期回忆）→ system 区，进缓存前缀
 * - 这里：每轮都不同 → 挂在本轮 user 消息上
 *
 * **备注一旦生成就跟着那条消息留在 [ChatSession] 的历史里**，以后每轮原样重发。
 * 这是为缓存考虑的：如果只在「当前这轮」临时拼上、下一轮又摘掉，那上一轮写进缓存的前缀
 * （带备注）和这一轮的前缀（不带）对不上，滚动缓存每轮都会被打穿。留着它只多花几十 token，
 * 还顺带让模型看得到一条带时间戳的对话时间线。
 *
 * 备注**不进 [ChatSession.snapshot]**（那是给 Room / 记忆抽取看的原话），只在组装请求时合进去。
 *
 * Author: Voine
 * Date: 2026/10/15
 */
fun interface TurnContextContributor {

    /**
     * @param userMessage 本轮用户输入（原话）
     * @return 要附在这条消息前面的备注，null / 空白表示不附
     */
    suspend fun turnContext(userMessage: ChatMessage): String?
}
