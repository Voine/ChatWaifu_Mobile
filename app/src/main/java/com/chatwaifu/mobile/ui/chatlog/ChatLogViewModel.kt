package com.chatwaifu.mobile.ui.chatlog

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.chatwaifu.log.IChatLogDbApi
import com.chatwaifu.log.room.ChatLogDbManager
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.ui.common.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Description: ChatLogViewModel
 * Author: Voine
 * Date: 2023/3/16
 */
class ChatLogViewModel : ViewModel() {

    val chatLogData = MutableLiveData<List<Message>>()
    val chatLogLoadingUI = MutableLiveData<Boolean>()
    private var getChatListJob: Job? = null

    private val dbManager: IChatLogDbApi by lazy {
        ChatLogDbManager(ChatWaifuApplication.context)
    }

    fun loadAllChatMessage(chatName: String) {
        chatLogLoadingUI.postValue(true)
        getChatListJob?.cancel()
        getChatListJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val msgList = dbManager.getAllChatLog(chatName)
                val contextResource = ChatWaifuApplication.context.resources
                chatLogData.postValue(msgList.map {
                    Message(
                        author = it.characterName,
                        content = it.chatMessage,
                        isFromMe = it.sendFromMe,
                        timestamp = it.timeline.toString(),
                        authorImage = if (it.sendFromMe) R.drawable.chat_log_person else {
                            when (it.characterName) {
                                contextResource.getString(R.string.chat_log_item_yuuka) -> {
                                    R.drawable.hiyori_head
                                }
                                contextResource.getString(R.string.chat_log_item_atri) -> {
                                    R.drawable.atri_head
                                }
                                contextResource.getString(R.string.chat_log_item_amadeus) -> {
                                    R.drawable.kurisu_head
                                }
                                else -> {
                                    R.drawable.external_default_icon
                                }
                            }
                        }
                    )
                })
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                chatLogLoadingUI.postValue(false)
            }
        }
    }
}