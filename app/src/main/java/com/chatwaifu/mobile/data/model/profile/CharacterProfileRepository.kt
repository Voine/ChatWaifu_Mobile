package com.chatwaifu.mobile.data.model.profile

import com.chatwaifu.mobile.data.model.CharacterPackage

/**
 * Description: Persona / Voice 两个轻量 Profile 的唯一读写入口。
 *
 * [CharacterPackage] 只引用 profile 的 **ID**；文本、speaker 这些内容全在这里取，
 * 所以角色包不会被撑成一个什么都装的大对象（推理配置尤其不允许进来，
 * 见 [PersonaProfile] 和 [VoiceProfile] 的 KDoc）。
 *
 * **不继承语义**：解析永远针对传入的那个角色。角色没有人格时返回的是
 * [PersonaProfile.isEmpty] 为真的 profile，而不是 null、更不是上一个角色的 prompt。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
interface CharacterProfileRepository {

    /** 解析角色的人格。永远返回非 null —— 至少是一个 empty profile。 */
    fun personaProfile(character: CharacterPackage): PersonaProfile

    /** 解析角色的语音。永远返回非 null —— 没有声库时 sourceType 是 [VoiceSourceType.NONE]。 */
    fun voiceProfile(character: CharacterPackage): VoiceProfile

    /** 该角色声库里可选的 speaker。空列表表示拿不到列表，UI 应退化成只读。 */
    fun availableSpeakers(character: CharacterPackage): List<VoiceSpeaker>

    /** 该角色是否有内置默认人格可恢复（导入角色没有）。 */
    fun hasDefaultPersona(character: CharacterPackage): Boolean

    fun savePersona(character: CharacterPackage, name: String, prompt: String)

    /** 恢复内置默认人格；导入角色等价于清空。 */
    fun resetPersona(character: CharacterPackage)

    /**
     * 保存语音配置。[speakerId] 会**写回 meta.json** —— 它是 BV2 加载路径的权威来源，
     * profile 侧不另立真相源。
     */
    suspend fun saveVoice(character: CharacterPackage, name: String, speakerId: Int)
}
