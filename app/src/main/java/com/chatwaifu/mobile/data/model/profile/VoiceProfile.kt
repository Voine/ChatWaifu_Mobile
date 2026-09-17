package com.chatwaifu.mobile.data.model.profile

/**
 * Description: 角色的语音配置。[CharacterPackage.voiceProfileId] 引用的就是 [id]。
 *
 * **只表达「角色用哪套声音配置」**，不持有 native runtime、`SoundGenerateHelper`
 * 或任何模型实例；真正的加载仍由 `ChatActivityViewModel.loadVitsModel()` →
 * `SoundGenerateHelper.init()` 完成，这一层一行没改。
 *
 * [speakerId] / [language] 的**运行时权威仍是 meta.json**（`CharacterPackage` 从那里读出来
 * 喂给 BV2）。profile 保存 speaker 时会写回 meta.json，而不是在旁边再立一个真相源——
 * 否则「profile 说 2、meta 说 0」时没人知道该信谁。profile 的 JSON 记录只存 [name]。
 *
 * 没有音量 / 语速字段：当前 BV2 wrapper 不暴露这两个参数，
 * 按「不新增看起来有但实际无效的设置」不做占位。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
data class VoiceProfile(
    val id: String,
    val name: String,
    /** `LANGUAGE_ZH/EN/JP/MIX_ZH_EN` 之一。null 表示该角色没有语音。 */
    val language: Int?,
    val speakerId: Int?,
    val sourceType: VoiceSourceType,
    val builtIn: Boolean = false,
    /** 声学模型目录绝对路径，只作引用；null 表示没有语音或资源缺失。 */
    val vitsDir: String? = null,
    /** BERT 编码器目录绝对路径，按语种共享。 */
    val bertDir: String? = null,
) {
    val available: Boolean get() = sourceType != VoiceSourceType.NONE && vitsDir != null

    companion object {
        fun idFor(characterId: String): String = "voice:$characterId"
    }
}

enum class VoiceSourceType {
    /** 内置角色：指向 `_bv2/` 那份共享权重，靠 speaker id 区分 */
    SHARED_BUILT_IN,

    /** 导入角色：自带 `vits/` 目录里的声学模型 */
    BUNDLED_IMPORTED,

    /** 这个角色没有语音（Live2D only） */
    NONE,
}
