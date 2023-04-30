package com.chatwaifu.log.room

import android.content.Context
import com.chatwaifu.log.IChatLogDbApi

/**
 * Description: ChatLog database manager
 * Author: Voine
 * Date: 2023/3/13
 */
class ChatLogDbManager(private val context: Context) : IChatLogDbApi {
    override fun insertChatLog(
        characterName: String,
        isFromMe: Boolean,
        chatMessage: String,
        completionToken: Int,
        promptTokens: Int
    ) {
        ChatDatabase.getDataBase(context).chatMessageDao().insertMessage(
            ChatMessage(
                characterName,
                chatMessage,
                isFromMe,
                promptTokens,
                completionToken,
                System.currentTimeMillis()
            )
        )
    }

    override fun insertChatLog(chatMessage: ChatMessage) {
        ChatDatabase.getDataBase(context).chatMessageDao().insertMessage(chatMessage)
    }

    override fun getAllChatLog(characterName: String): List<ChatMessage> {
        return ChatDatabase.getDataBase(context).chatMessageDao().loadAllChatMessage(characterName)
    }

    override fun getChatLogWithLimit(characterName: String, limit: Int): List<ChatMessage> {
        return ChatDatabase.getDataBase(context).chatMessageDao().loadChatMessageWithLimit(characterName, limit)
    }

    override fun onLoadMoreChatLog(
        characterName: String,
        lastMessageId: Long,
        limit: Int
    ): List<ChatMessage> {
        return ChatDatabase.getDataBase(context).chatMessageDao()
            .loadChatMessageAfterId(characterName, lastMessageId, limit)
    }
}