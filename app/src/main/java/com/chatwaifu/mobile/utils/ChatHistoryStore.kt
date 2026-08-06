package com.chatwaifu.mobile.utils

import android.content.Context
import android.util.Log
import com.chatwaifu.chat.core.ChatMessage
import com.chatwaifu.chat.core.TokenUsage
import com.chatwaifu.log.IChatLogDbApi
import com.chatwaifu.log.room.ChatLogDbManager
import com.chatwaifu.log.room.ChatMessage as StoredMessage

/**
 * Description: 聊天记录的落库与恢复。取代原来的 `AssistantMessageManager`。
 *
 * 和老实现的两个区别：
 *
 * 1. **不再依赖 ChatGPT 模块的 DTO**。老实现直接吃 `ChatGPTResponseData`，
 *    从 `choices[0].message.content` 和 `usage` 里掏数据，换基座就得改。
 * 2. **恢复历史时用户消息不再被丢掉**。老的 `getSendAssistantList()` 过滤条件是
 *    `!it.sendFromMe`，也就是只把模型自己说过的话回传给模型 ——
 *    模型看到的是一段自己的独白，多轮指代（"她多大了"）根本接不住。
 *    这里按时间正序把两边都恢复出来，裁剪交给
 *    [com.chatwaifu.chat.core.ContextBudget]。
 *
 * 上下文裁剪也不在这里做了：老实现按 `ChatGPTData.MAX_SEND_LIMIT`（写死的 gpt-3.5
 * 4096 窗口）算预算，那个常量已经随 ChatGPT 模块一起删掉。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class ChatHistoryStore(context: Context) {

    private val dbManager: IChatLogDbApi = ChatLogDbManager(context)
    private var currentCharacterName: String = ""

    /**
     * 读出某个角色的历史，**时间正序**（老的最前面），可以直接喂给
     * [com.chatwaifu.chat.core.ChatSession.restore]。
     */
    fun load(characterName: String): List<ChatMessage> {
        currentCharacterName = characterName
        val stored = dbManager.getChatLogWithLimit(characterName, limit = HISTORY_LIMIT)
            .sortedBy { it.timeline }
        Log.d(TAG, "load ${stored.size} messages for $characterName")
        return stored.mapNotNull { it.toCoreMessage() }
    }

    fun appendUser(text: String) = insert(isFromMe = true, text = text)

    fun appendAssistant(text: String, usage: TokenUsage? = null) = insert(
        isFromMe = false,
        text = text,
        completionTokens = usage?.completionTokens ?: 0,
        promptTokens = usage?.promptTokens ?: 0,
    )

    private fun insert(
        isFromMe: Boolean,
        text: String,
        completionTokens: Int = 0,
        promptTokens: Int = 0,
    ) {
        if (currentCharacterName.isEmpty()) {
            Log.e(TAG, "no character selected, drop message")
            return
        }
        if (text.isBlank()) return
        dbManager.insertChatLog(
            StoredMessage(
                currentCharacterName,
                text,
                isFromMe,
                promptTokens,
                completionTokens,
                System.currentTimeMillis(),
            )
        )
    }

    private fun StoredMessage.toCoreMessage(): ChatMessage? {
        if (chatMessage.isBlank()) return null
        return if (sendFromMe) ChatMessage.user(chatMessage) else ChatMessage.assistant(chatMessage)
    }

    companion object {
        private const val TAG = "ChatHistoryStore"

        /** 读这么多条进内存，真正发出去多少由 ContextBudget 按 token 决定。 */
        private const val HISTORY_LIMIT = 200
    }
}
