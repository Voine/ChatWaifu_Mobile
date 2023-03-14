package com.chatwaifu.mobile.ui.chatlog

import androidx.lifecycle.ViewModel
import com.chatwaifu.log.IChatLogDbApi
import com.chatwaifu.log.room.ChatLogDbManager
import com.chatwaifu.log.room.ChatMessage
import com.chatwaifu.mobile.application.ChatWaifuApplication

/**
 * Description: ChatLogViewModel
 * Author: Voine
 * Date: 2023/3/16
 */
class ChatLogViewModel: ViewModel() {
    private val dbManager: IChatLogDbApi by lazy {
        ChatLogDbManager(ChatWaifuApplication.context)
    }

    fun getChatList(name: String, start: Long = -1L, limit: Int): List<ChatMessage> {
        return dbManager.onLoadMoreChatLog(name, start, limit)
    }
}