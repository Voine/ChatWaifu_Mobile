package com.chatwaifu.log

import com.chatwaifu.log.room.ChatMessage

/**
 * Description: ChatLogApi
 * Author: Voine
 * Date: 2023/3/13
 */
interface IChatLogDbApi {
    fun insertChatLog(
        characterName: String,
        isFromMe: Boolean,
        chatMessage: String,
        completionToken: Int = -1,
        promptTokens: Int = -1
    ) {}

    fun insertChatLog(chatMessage: ChatMessage) {}

    fun getAllChatLog(characterName: String) = emptyList<ChatMessage>()

    fun getChatLogWithLimit(characterName: String, limit: Int) = emptyList<ChatMessage>()

    fun onLoadMoreChatLog(characterName: String, lastMessageId: Long, limit: Int) = emptyList<ChatMessage>()
}