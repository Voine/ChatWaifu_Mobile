package com.chatwaifu.mobile.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chatwaifu.mobile.R

/**
 * Description: ChatListAdapter
 * Author: Voine
 * Date: 2023/2/19
 */
class ChatListAdapter: RecyclerView.Adapter<ChatListHolder>() {

    val items = mutableListOf<ListBean>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatListHolder {
        val item = LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false)
        return ChatListHolder(item)
    }

    override fun onBindViewHolder(holder: ChatListHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

data class ListBean(
    var isGpt: Boolean = false,
    var msg: String = ""
)