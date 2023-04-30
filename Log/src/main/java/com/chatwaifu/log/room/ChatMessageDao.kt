package com.chatwaifu.log.room

import androidx.room.*

/**
 * Description: ChatMessageDao
 * Author: Voine
 * Date: 2023/3/13
 */
@Dao
interface ChatMessageDao {

    @Insert
    fun insertMessage(msg: ChatMessage): Long

    @Update
    fun updateChatMessage(msg: ChatMessage)

    @Query("select * from ChatMessage where characterName = :characterName limit :limit")
    fun loadChatMessageWithLimit(characterName: String, limit: Int): List<ChatMessage>

    @Query("select * from ChatMessage where characterName = :characterName")
    fun loadAllChatMessage(characterName: String): List<ChatMessage>

    @Query("select * from ChatMessage where id > :id and characterName = :characterName limit :limit")
    fun loadChatMessageAfterId(characterName: String, id: Long, limit: Int): List<ChatMessage>

    @Delete
    fun deleteMessage(msg: ChatMessage)

    @Query("delete from ChatMessage where characterName = :characterName")
    fun deleteMessageByCharacterName(characterName: String)
}