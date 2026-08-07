package com.chatwaifu.mobile.ui.chatlog

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatwaifu.log.ChatLogEntry
import com.chatwaifu.log.ChatLogRepository
import com.chatwaifu.log.ChatLogRole
import com.chatwaifu.log.room.RoomChatLogRepository
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.application.ChatWaifuApplication
import com.chatwaifu.mobile.ui.common.Message
import com.chatwaifu.mobile.ui.common.avatarResOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Description: ChatLogViewModel
 *
 * 老实现是 `getAllChatLog(name).reversed()` —— **整表加载进内存**，历史长了会卡，
 * 而且是一次性快照，新消息进来不刷新。现在订阅 [ChatLogRepository.observeChatLog]
 * 的最近 [PAGE_SIZE] 条，写入自动重发。
 *
 * 更老的记录走 [ChatLogRepository.getOlderChatLog] 分页（DAO 已就绪，UI 上滑加载待接）。
 *
 * Author: Voine
 * Date: 2023/3/16
 */
class ChatLogViewModel : ViewModel() {

    val chatLogData = MutableLiveData<List<Message>>()
    val chatLogLoadingUI = MutableLiveData<Boolean>()
    private var getChatListJob: Job? = null

    private val repository: ChatLogRepository by lazy {
        RoomChatLogRepository(ChatWaifuApplication.context)
    }

    fun loadAllChatMessage(chatName: String) {
        getChatListJob?.cancel()
        getChatListJob = viewModelScope.launch {
            repository.observeChatLog(chatName, limit = PAGE_SIZE)
                .onStart { chatLogLoadingUI.postValue(true) }
                .flowOn(Dispatchers.IO)
                .catch { it.printStackTrace() }
                .collect { entries ->
                    // 记录页从新到旧展示，而仓库给的是时间正序
                    chatLogData.postValue(entries.asReversed().map { it.toUiMessage(chatName) })
                    chatLogLoadingUI.postValue(false)
                }
        }
    }

    private fun ChatLogEntry.toUiMessage(chatName: String): Message {
        val isFromMe = role == ChatLogRole.USER
        return Message(
            author = if (isFromMe) {
                ChatWaifuApplication.context.resources.getString(R.string.author_me)
            } else {
                chatName
            },
            content = text,
            isFromMe = isFromMe,
            timestamp = TIME_FORMAT.format(Date(timeline)),
            authorImage = if (isFromMe) R.drawable.chat_log_person else avatarResOf(chatName),
        )
    }

    companion object {
        private const val PAGE_SIZE = 200

        // SimpleDateFormat 非线程安全，这里只在单个 collect 内使用
        private val TIME_FORMAT = SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }
}
