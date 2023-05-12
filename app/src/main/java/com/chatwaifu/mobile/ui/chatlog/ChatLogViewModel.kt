package com.chatwaifu.mobile.ui.chatlog

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.chatwaifu.log.IChatLogDbApi
import com.chatwaifu.log.room.ChatLogDbManager
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.ui.common.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                val msgList = dbManager.getAllChatLog(chatName).reversed()
                chatLogData.postValue(msgList.map {
                    Message(
                        author = if (it.sendFromMe) ChatWaifuApplication.context.resources.getString(
                            R.string.author_me
                        ) else it.characterName,
                        content = it.chatMessage,
                        isFromMe = it.sendFromMe,
                        timestamp = SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.ENGLISH).format(
                            Date(it.timeline)
                        ).toString(),
                        authorImage = if (it.sendFromMe) R.drawable.chat_log_person else {
                            when (it.characterName) {
                                Constant.LOCAL_MODEL_YUUKA -> {
                                    R.drawable.yuuka_head
                                }

                                Constant.LOCAL_MODEL_ATRI -> {
                                    R.drawable.atri_head
                                }

                                Constant.LOCAL_MODEL_AMADEUS -> {
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