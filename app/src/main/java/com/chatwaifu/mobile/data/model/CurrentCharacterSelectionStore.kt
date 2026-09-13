package com.chatwaifu.mobile.data.model

import android.content.SharedPreferences
import com.chatwaifu.mobile.data.Constant

/**
 * 当前角色只保存稳定 [CharacterPackage.id]。
 * 旧 [Constant.SAVED_CHAT_NAME] 仅用于一次性按 storageKey 恢复，绝不写回新选择。
 */
internal class CurrentCharacterSelectionStore(
    private val preferences: SharedPreferences,
) {
    fun selectedId(): String? =
        preferences.getString(Constant.SAVED_CURRENT_CHARACTER_ID, null)?.ifBlank { null }

    fun save(id: String) {
        preferences.edit().putString(Constant.SAVED_CURRENT_CHARACTER_ID, id).apply()
    }

    fun resolve(
        characters: List<CharacterPackage>,
        preferredId: String? = selectedId(),
    ): CharacterPackage? {
        val legacyStorageKey = preferences.getString(Constant.SAVED_CHAT_NAME, null)
        val fallback = resolveCharacter(characters, preferredId, legacyStorageKey)
        fallback?.let { save(it.id) }
        return fallback
    }

    companion object {
        internal fun resolveCharacter(
            characters: List<CharacterPackage>,
            preferredId: String?,
            legacyStorageKey: String?,
        ): CharacterPackage? {
            val available = characters.filter {
                it.availability == CharacterAvailability.AVAILABLE
            }
            return available.firstOrNull { it.id == preferredId }
                ?: available.firstOrNull { it.storageKey == legacyStorageKey }
                ?: available.firstOrNull { it.source == ModelSource.BUILT_IN }
                ?: available.firstOrNull()
        }
    }
}
