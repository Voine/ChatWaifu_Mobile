package com.chatwaifu.mobile.ui.modelmanager

import com.chatwaifu.mobile.data.model.profile.PersonaProfile
import com.chatwaifu.mobile.data.model.profile.VoiceProfile
import com.chatwaifu.mobile.data.model.profile.VoiceSpeaker

/**
 * Description: 角色详情的三级路由和两个 Profile 编辑器状态。
 *
 * 详情页本来就不是独立 Fragment（`ModelManagerContent` 内部按 selectedCharacterId
 * 切分支），所以人格 / 语音也继续走 state 分支，不新建 Fragment 和 nav destination ——
 * 那会让返回键、抽屉手势和角色选择态各多一套需要对齐的生命周期。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
enum class CharacterDetailRoute {
    DETAIL,
    PERSONA,
    VOICE,
}

/**
 * 人格编辑器。[dirty] 由 [PersonaEditorState] 自己算，UI 不需要自己记「改过没有」。
 */
data class PersonaEditorState(
    val profile: PersonaProfile,
    val name: String,
    val prompt: String,
    val hasDefault: Boolean,
    val saving: Boolean = false,
) {
    val dirty: Boolean
        get() = name.trim() != profile.name.trim() || prompt != profile.prompt

    companion object {
        fun of(profile: PersonaProfile, hasDefault: Boolean) = PersonaEditorState(
            profile = profile,
            name = profile.name,
            prompt = profile.prompt,
            hasDefault = hasDefault,
        )
    }
}

/**
 * 语音编辑器。
 *
 * [speakers] 为空表示读不到 `spk2id`（config.json 缺失 / 损坏）——
 * UI 此时只读显示当前 speaker，而不是给一个会写坏 meta.json 的输入框。
 *
 * [previewEnabled] 为假的两种原因分开表达：角色没有语音 / 资源缺失（[voiceAvailable]），
 * 或者它不是当前角色（[isCurrentCharacter]）——后者受 BV2 进程级单模型限制，
 * 见 `ChatActivityViewModel.previewVoice`。
 */
data class VoiceEditorState(
    val profile: VoiceProfile,
    val name: String,
    val speakerId: Int?,
    val speakers: List<VoiceSpeaker>,
    val isCurrentCharacter: Boolean,
    val previewing: Boolean = false,
    val saving: Boolean = false,
) {
    val voiceAvailable: Boolean get() = profile.available
    val previewEnabled: Boolean get() = voiceAvailable && isCurrentCharacter
    val speakerEditable: Boolean get() = voiceAvailable && speakers.size > 1

    val dirty: Boolean
        get() = name.trim() != profile.name.trim() || speakerId != profile.speakerId

    /** 当前 speaker 在声库里的名字；读不到列表时返回 null，UI 只显示数字。 */
    val speakerName: String?
        get() = speakers.firstOrNull { it.id == speakerId }?.name

    companion object {
        fun of(
            profile: VoiceProfile,
            speakers: List<VoiceSpeaker>,
            isCurrentCharacter: Boolean,
        ) = VoiceEditorState(
            profile = profile,
            name = profile.name,
            // 非法 speaker（meta 被改坏 / 换了声库）回落到列表首个，而不是把非法值喂给 BV2
            speakerId = profile.speakerId?.takeIf { id ->
                speakers.isEmpty() || speakers.any { it.id == id }
            } ?: speakers.firstOrNull()?.id ?: profile.speakerId,
            speakers = speakers,
            isCurrentCharacter = isCurrentCharacter,
        )
    }
}
