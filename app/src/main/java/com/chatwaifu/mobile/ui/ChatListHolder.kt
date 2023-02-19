package com.chatwaifu.mobile.ui

import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chatwaifu.mobile.R

/**
 * Description: ChatListHolder
 * Author: Voine
 * Date: 2023/2/19
 */
class ChatListHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    var textView: TextView
    init {
        textView = itemView.findViewById(R.id.chat_text)
    }

    fun bind(chat: ListBean) {
        val showText = chat.msg.trim()
        if (chat.isGpt) {
            textView.text = "Wifu: $showText"
            textView.gravity = Gravity.START
        } else {
            textView.text = "Me: $showText"
            textView.gravity = Gravity.END

        }
    }
}