package com.chatwaifu.mobile.data.model.profile

import android.content.Context
import android.content.SharedPreferences
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import com.chatwaifu.mobile.data.model.CharacterPackage
import com.chatwaifu.mobile.data.model.CharacterRepository
import com.chatwaifu.mobile.data.model.ModelSource

/**
 * Description: [CharacterProfileRepository] 的落地实现。
 *
 * **为什么没有新的存储层**：Persona 的文本和 Voice 的 speaker 早就有权威落点了——
 * 前者是 `CharacterRepository.getSystemPrompt/saveSystemPrompt` 那套按角色分区的老 key，
 * 后者是 meta.json。再造一份 profile 表只会制造两个真相源和一次有损迁移。
 * 所以这里只做两件事：把它们**组装**成 profile 领域对象，
 * 外加存一个纯展示用的自定义名字（`saved_persona_name_<id>` / `saved_voice_name_<id>`）。
 *
 * 迁移因此是天然幂等的：profile ID 由稳定角色 ID 确定性派生
 * （见 [ModelStorage.migrateMetadata]），名字缺失时按 displayName 现算，
 * 存量 persona / speaker 一个字节都不用搬。
 *
 * Author: Voine
 * Date: 2026/9/17
 */
internal class CharacterProfileRepositoryImpl(
    private val context: Context,
    private val characters: CharacterRepository,
    private val sp: SharedPreferences,
) : CharacterProfileRepository {

    override fun personaProfile(character: CharacterPackage): PersonaProfile {
        // 注意取的是传入角色的 storageKey：解析不存在「沿用当前角色」这条路径
        val prompt = characters.getSystemPrompt(character.storageKey).orEmpty()
        return PersonaProfile(
            // meta.json 缺 ID 时现算，不因此判定 profile 损坏
            id = character.personaProfileId ?: PersonaProfile.idFor(character.id),
            name = personaName(character),
            prompt = prompt,
            builtIn = character.source == ModelSource.BUILT_IN,
        )
    }

    override fun voiceProfile(character: CharacterPackage): VoiceProfile {
        val sourceType = when {
            !character.hasVoice -> VoiceSourceType.NONE
            character.source == ModelSource.BUILT_IN -> VoiceSourceType.SHARED_BUILT_IN
            else -> VoiceSourceType.BUNDLED_IMPORTED
        }
        return VoiceProfile(
            id = character.voiceProfileId ?: VoiceProfile.idFor(character.id),
            name = voiceName(character),
            language = character.language.takeIf { sourceType != VoiceSourceType.NONE },
            speakerId = character.speakerId.takeIf { sourceType != VoiceSourceType.NONE },
            sourceType = sourceType,
            builtIn = character.source == ModelSource.BUILT_IN,
            vitsDir = character.vitsDir,
            bertDir = character.bertDir,
        )
    }

    override fun availableSpeakers(character: CharacterPackage): List<VoiceSpeaker> =
        VoiceSpeakerCatalog.speakers(character.vitsDir)

    override fun hasDefaultPersona(character: CharacterPackage): Boolean =
        defaultPrompt(character.storageKey) != null

    override fun savePersona(character: CharacterPackage, name: String, prompt: String) {
        characters.saveSystemPrompt(character.storageKey, prompt)
        putName(personaNameKey(character.id), name, personaDefaultName(character))
    }

    override fun resetPersona(character: CharacterPackage) {
        // 内置角色：写空串让 getSystemPrompt 回落到 R.string.default_system_*；
        // 导入角色：没有内置默认，清空就是它的默认
        characters.saveSystemPrompt(character.storageKey, "")
        sp.edit().remove(personaNameKey(character.id)).apply()
    }

    override suspend fun saveVoice(character: CharacterPackage, name: String, speakerId: Int) {
        if (speakerId != character.speakerId) {
            // meta.json 是 BV2 加载路径的权威来源，speaker 只往那里写
            characters.updateSpeakerId(character.id, speakerId)
        }
        putName(voiceNameKey(character.id), name, voiceDefaultName(character))
    }

    private fun personaName(character: CharacterPackage): String =
        sp.getString(personaNameKey(character.id), null)?.ifBlank { null }
            ?: personaDefaultName(character)

    private fun voiceName(character: CharacterPackage): String =
        sp.getString(voiceNameKey(character.id), null)?.ifBlank { null }
            ?: voiceDefaultName(character)

    private fun personaDefaultName(character: CharacterPackage): String =
        context.getString(R.string.persona_default_name, character.displayName)

    private fun voiceDefaultName(character: CharacterPackage): String =
        if (character.hasVoice) {
            context.getString(R.string.voice_default_name, character.displayName)
        } else {
            context.getString(R.string.voice_none_name)
        }

    /** 名字等于默认值时不落盘，避免 displayName 变化后被一个陈旧的名字盖住 */
    private fun putName(key: String, value: String, default: String) {
        val trimmed = value.trim()
        sp.edit().apply {
            if (trimmed.isBlank() || trimmed == default) remove(key) else putString(key, trimmed)
        }.apply()
    }

    private fun defaultPrompt(storageKey: String): String? = when (storageKey) {
        Constant.LOCAL_MODEL_YUUKA -> context.getString(R.string.default_system_yuuka)
        Constant.LOCAL_MODEL_AMADEUS -> context.getString(R.string.default_system_amadeus)
        Constant.LOCAL_MODEL_ATRI -> context.getString(R.string.default_system_atri)
        else -> null
    }

    private fun personaNameKey(characterId: String) =
        Constant.SAVED_PERSONA_NAME_PREFIX + characterId

    private fun voiceNameKey(characterId: String) =
        Constant.SAVED_VOICE_NAME_PREFIX + characterId
}
