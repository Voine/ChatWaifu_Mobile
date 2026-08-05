package com.chatwaifu.mobile.ui.common

import androidx.annotation.DrawableRes
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant

/**
 * Description: 角色名 → 头像资源。
 *
 * 这个映射原先在 LocalModelManager 和 ChatLogViewModel 里各写了一份 when 块，
 * 内容一样但要改两处。资源 id 是 UI 概念，所以放在 ui 层统一收口，
 * 数据层的 CharacterModel 不携带 drawable。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
@DrawableRes
fun avatarResOf(characterName: String?): Int = when (characterName) {
    Constant.LOCAL_MODEL_YUUKA -> R.drawable.yuuka_head
    Constant.LOCAL_MODEL_AMADEUS -> R.drawable.kurisu_head
    Constant.LOCAL_MODEL_ATRI -> R.drawable.atri_head
    else -> R.drawable.external_default_icon
}
