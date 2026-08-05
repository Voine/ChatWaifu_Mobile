package com.chatwaifu.mobile.data.model

import android.content.Context
import android.content.SharedPreferences
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Description: [CharacterRepository] 的落地实现。
 *
 * 取代原来的 LocalModelManager：内置模型和导入模型不再是两套路径两套逻辑，
 * 统一由 [ModelStorage] 管布局，[BuiltInModelInstaller] 只负责把 assets 解出来。
 *
 * Author: Voine
 * Date: 2026/8/5
 */
internal class CharacterRepositoryImpl(
    private val context: Context,
    private val storage: ModelStorage,
    private val sp: SharedPreferences,
) : CharacterRepository {

    private val installer by lazy { BuiltInModelInstaller(context, storage, sp) }

    override suspend fun loadCharacters(): List<CharacterModel> = withContext(Dispatchers.IO) {
        installer.ensureInstalled()
        storage.listInstalled()
            .map { storage.toCharacterModel(it) }
            // 内置的排前面，同组按名字排，保证列表顺序稳定
            .sortedWith(compareBy({ it.source.ordinal }, { it.name }))
    }

    override suspend fun getCharacter(name: String): CharacterModel? = withContext(Dispatchers.IO) {
        storage.readMeta(name)?.let { storage.toCharacterModel(it) }
    }

    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        val meta = storage.readMeta(name) ?: return@withContext false
        // 内置模型删了下次启动又会解出来，直接不允许
        if (meta.source == ModelSource.BUILT_IN.name) return@withContext false
        val deleted = storage.delete(name)
        if (deleted) {
            sp.edit().remove(systemPromptKey(name)).apply()
        }
        deleted
    }

    override fun getSystemPrompt(name: String): String? {
        // 内置三个角色沿用原来的 key，用户改过的设定不会因为这次重构丢掉
        legacyBuiltInKey(name)?.let { key ->
            return sp.getString(key, null)?.ifBlank { null } ?: defaultBuiltInPrompt(name)
        }
        return sp.getString(systemPromptKey(name), null)?.ifBlank { null }
    }

    override fun saveSystemPrompt(name: String, prompt: String) {
        val key = legacyBuiltInKey(name) ?: systemPromptKey(name)
        sp.edit().putString(key, prompt).apply()
    }

    override suspend fun updateSpeakerId(name: String, speakerId: Int) {
        withContext(Dispatchers.IO) {
            val meta = storage.readMeta(name) ?: return@withContext
            storage.writeMeta(name, meta.copy(speakerId = speakerId))
        }
    }

    private fun legacyBuiltInKey(name: String): String? = when (name) {
        Constant.LOCAL_MODEL_YUUKA -> Constant.SAVED_YUUKA_SETTING
        Constant.LOCAL_MODEL_AMADEUS -> Constant.SAVED_AMADEUS_SETTING
        Constant.LOCAL_MODEL_ATRI -> Constant.SAVED_ATRI_SETTING
        else -> null
    }

    private fun defaultBuiltInPrompt(name: String): String? = when (name) {
        Constant.LOCAL_MODEL_YUUKA -> context.getString(R.string.default_system_yuuka)
        Constant.LOCAL_MODEL_AMADEUS -> context.getString(R.string.default_system_amadeus)
        Constant.LOCAL_MODEL_ATRI -> context.getString(R.string.default_system_atri)
        else -> null
    }

    /** 导入模型的设定按角色名分开存，不再是所有外部模型共用一份 */
    private fun systemPromptKey(name: String) = Constant.SAVED_SYSTEM_PROMPT_PREFIX + name
}
