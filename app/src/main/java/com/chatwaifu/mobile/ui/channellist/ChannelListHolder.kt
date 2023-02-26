package com.chatwaifu.mobile.ui

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chatwaifu.mobile.R

/**
 * Description: ChatListHolder
 * Author: Voine
 * Date: 2023/2/19
 */
class ChannelListHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    var itemName: TextView
    var avatar: ImageView
    var itemTime: TextView

    init {
        itemName = itemView.findViewById(R.id.item_name)
        avatar = itemView.findViewById(R.id.item_avatar)
        itemTime = itemView.findViewById(R.id.item_time)
    }

    fun bind(chat: ChannelListBean) {
        avatar.setImageResource(chat.avatarDrawable)
        itemName.text = chat.characterName
        itemTime.text = chat.characterTime
    }
}
data class ChannelListBean(
    var avatarDrawable: Int,
    var characterName: String,
    var characterTime: String = "",
    var characterPath: String = "",
    var characterVitsPath: String = ""
)