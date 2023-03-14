package com.chatwaifu.mobile.ui.channellist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chatwaifu.mobile.R

/**
 * Description: ChatListAdapter
 * Author: Voine
 * Date: 2023/2/19
 */
class ChannelListAdapter: RecyclerView.Adapter<ChannelListHolder>() {

    val items = mutableListOf<ChannelListBean>()
    var onClick: ((item: ChannelListBean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelListHolder {
        val item = LayoutInflater.from(parent.context).inflate(R.layout.channel_list_item, parent, false)
        return ChannelListHolder(item)
    }

    override fun onBindViewHolder(holder: ChannelListHolder, position: Int) {
        holder.itemView.setOnClickListener {
            onClick?.invoke(items[position])
        }
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}