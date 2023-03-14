package com.chatwaifu.log.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Description: ChatMessage Bean
 * Author: Voine
 * Date: 2023/3/13
 */
@Entity
data class ChatMessage(
    var characterName: String,
    var chatMessage: String,
    var sendFromMe: Boolean,
    var promptTokens: Int,
    var completionTokens: Int,
    var timeline: Long,
){
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
