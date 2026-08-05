package com.chatwaifu.mobile.ui.channellist

import com.chatwaifu.mobile.data.model.CharacterModel
import com.chatwaifu.mobile.ui.common.avatarResOf

/**
 * Description: ChatListHolder
 *
 * 列表项的 UI 模型。原先它同时充当数据模型，把 characterPath / characterVitsPath /
 * fromExternal 这些磁盘细节和 avatarDrawable 这个资源 id 混在一起；
 * 现在数据侧统一用 [CharacterModel]，这里只留渲染需要的东西。
 *
 * Author: Voine
 * Date: 2023/2/19
 */
data class ChannelListBean(
    val avatarDrawable: Int,
    val characterName: String,
    /** 对应的角色模型，点击时回传给 ViewModel。预览场景为 null */
    val character: CharacterModel? = null,
)

fun CharacterModel.toChannelListBean(): ChannelListBean = ChannelListBean(
    avatarDrawable = avatarResOf(name),
    characterName = name,
    character = this,
)
