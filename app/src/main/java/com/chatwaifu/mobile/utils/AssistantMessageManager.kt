package com.chatwaifu.mobile.utils

import android.content.Context
import android.util.Log
import com.chatwaifu.chatgpt.ChatGPTData
import com.chatwaifu.chatgpt.ChatGPTResponseData
import com.chatwaifu.log.IChatLogDbApi
import com.chatwaifu.log.room.ChatLogDbManager
import com.chatwaifu.log.room.ChatMessage
import com.chatwaifu.mobile.ChatActivityViewModel

/**
 * Description: filter chat gpt assistant message
 * Author: Voine
 * Date: 2023/3/14
 */
class AssistantMessageManager(context: Context) {
    companion object {
        private const val TAG = "AssistantMessageManager"
    }
    private val dbManager: IChatLogDbApi
    private val senderTokenLimit = ChatGPTData.MAX_SEND_LIMIT * 0.1
    private val assistantTokenLimit =
        ChatGPTData.MAX_SEND_LIMIT - senderTokenLimit - ChatGPTData.DEFAULT_SYSTEM_ROLE_LIMIT
    private var allAssistList : MutableList<ChatMessage> = mutableListOf()
    private var currentCharacterName: String = ""
    init {
        dbManager = ChatLogDbManager(context)
    }

    fun loadChatListCache(characterName: String) {
        currentCharacterName = characterName
        allAssistList = dbManager.getChatLogWithLimit(characterName, limit = 200).filter {
            !it.sendFromMe
        }.sortedByDescending {
            it.timeline
        }.toMutableList()
        Log.d(TAG, "load chat list assistant cache finish $allAssistList")
    }

    fun insertUserMessage(chatMessage: String) {
        insertAssistantMessage(isFromMe = true, chatMessage)
    }

    fun insertGPTMessage(chatGPTResponseData: ChatGPTResponseData?) {
        val responseText = chatGPTResponseData?.choices?.firstOrNull()?.message?.content ?: return
        val completionToken = chatGPTResponseData.usage?.completion_tokens ?: 0
        val promptTokens = chatGPTResponseData.usage?.prompt_tokens ?: 0
        insertAssistantMessage(
            isFromMe = false,
            chatMessage = responseText,
            completionToken,
            promptTokens
        )
    }

    private fun insertAssistantMessage(
        isFromMe: Boolean, chatMessage: String, completionToken: Int = 0, promptTokens: Int = 0
    ) {
        if (currentCharacterName.isEmpty()) {
            Log.e(TAG, "current character is null")
            return
        }
        val msg = ChatMessage(
            currentCharacterName,
            chatMessage,
            isFromMe,
            promptTokens,
            completionToken,
            System.currentTimeMillis()
        )
        allAssistList.add(0, msg)
        dbManager.insertChatLog(msg)
    }

    fun getSendAssistantList(): List<String> {
        val resultList = mutableListOf<String>()
        var tokenSum = 0
        for (assistMessage in allAssistList) {
            if (tokenSum + assistMessage.completionTokens <= assistantTokenLimit) {
                resultList.add(assistMessage.chatMessage)
                tokenSum += assistMessage.completionTokens
            } else {
                break
            }
        }
        return resultList
    }
}