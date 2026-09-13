package com.chatwaifu.mobile.data.model

import android.content.Context
import android.content.SharedPreferences
import com.chatwaifu.mobile.R
import com.chatwaifu.mobile.data.Constant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val selection = CurrentCharacterSelectionStore(sp)
    private val installMutex = Mutex()

    override suspend fun loadCharacters(): List<CharacterPackage> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            installer.ensureInstalled()
            storage.listInstalled()
                .map { storage.toCharacterModel(it) }
                // 内置的排前面，同组按名字排，保证列表顺序稳定
                .sortedWith(compareBy({ it.source.ordinal }, { it.displayName }))
        }
    }

    override suspend fun loadCharactersForDisplay(): List<CharacterPackage> =
        withContext(Dispatchers.IO) {
            installMutex.withLock {
                installer.ensureVisualsInstalled()
                storage.listInstalled()
                    .map { storage.toCharacterModel(it) }
                    .sortedWith(compareBy({ it.source.ordinal }, { it.displayName }))
            }
        }

    override suspend fun getCharacter(id: String): CharacterPackage? =
        loadCharacters().firstOrNull { it.id == id }

    override suspend fun getCurrentCharacter(): CharacterPackage? =
        selection.resolve(loadCharacters())

    override suspend fun getCurrentCharacterForDisplay(): CharacterPackage? =
        selection.resolve(loadCharactersForDisplay())

    override suspend fun setCurrentCharacter(id: String): CharacterPackage? {
        val character = loadCharacters().firstOrNull {
            it.id == id && it.availability == CharacterAvailability.AVAILABLE
        } ?: return null
        selection.save(character.id)
        return character
    }

    override suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val characters = loadCharacters()
        val character = characters.firstOrNull { it.id == id } ?: return@withContext false
        val meta = storage.readMeta(character.storageKey) ?: return@withContext false
        // 内置模型删了下次启动又会解出来，直接不允许
        if (meta.source == ModelSource.BUILT_IN.name) return@withContext false
        if (selection.selectedId() == character.id) {
            val fallback = characters.firstOrNull {
                it.id != character.id &&
                    it.source == ModelSource.BUILT_IN &&
                    it.availability == CharacterAvailability.AVAILABLE
            } ?: return@withContext false
            selection.save(fallback.id)
        }
        val deleted = storage.delete(character.storageKey)
        if (deleted) {
            sp.edit().remove(systemPromptKey(character.storageKey)).apply()
        }
        deleted
    }

    override fun getSystemPrompt(storageKey: String): String? {
        // 内置三个角色沿用原来的 key，用户改过的设定不会因为这次重构丢掉
        legacyBuiltInKey(storageKey)?.let { key ->
            return sp.getString(key, null)?.ifBlank { null }
                ?: defaultBuiltInPrompt(storageKey)
        }
        return sp.getString(systemPromptKey(storageKey), null)?.ifBlank { null }
    }

    override fun saveSystemPrompt(storageKey: String, prompt: String) {
        val key = legacyBuiltInKey(storageKey) ?: systemPromptKey(storageKey)
        sp.edit().putString(key, prompt).apply()
    }

    override suspend fun updateSpeakerId(id: String, speakerId: Int) {
        withContext(Dispatchers.IO) {
            val character = loadCharacters().firstOrNull { it.id == id } ?: return@withContext
            val meta = storage.readMeta(character.storageKey) ?: return@withContext
            storage.writeMeta(character.storageKey, meta.copy(speakerId = speakerId))
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
