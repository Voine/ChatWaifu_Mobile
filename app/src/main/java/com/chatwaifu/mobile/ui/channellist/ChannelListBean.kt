package com.chatwaifu.mobile.ui.channellist

/**
 * Description: ChatListHolder
 * Author: Voine
 * Date: 2023/2/19
 */
data class ChannelListBean(
    var avatarDrawable: Int,
    var characterName: String,
    var characterTime: String = "",
    var characterPath: String = "",
    var characterVitsPath: String = "",
    var fromExternal: Boolean = false
)