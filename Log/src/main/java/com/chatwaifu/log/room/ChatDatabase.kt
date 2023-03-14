package com.chatwaifu.log.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Description: ChatDataBase
 * Author: Voine
 * Date: 2023/3/13
 */
@Database(version = 1, entities = [ChatMessage::class])
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private var instance: ChatDatabase? = null

        @Synchronized
        fun getDataBase(context: Context): ChatDatabase {
            instance?.let {
                return it
            }
            return Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                "chat_log"
            ).build().apply {
                instance = this
            }
        }
    }
}