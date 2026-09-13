package com.chatwaifu.mobile.data.chat

import android.content.Context
import android.content.SharedPreferences
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.data.Constant

/**
 * Provider 配置的唯一持久化入口。SharedPreferences 只存表单值，
 * 调用方通过 [selection] 获取一次性的运行时快照。
 */
class ChatProviderSettings(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    var activeProviderId: ProviderId
        get() = ProviderId.fromKey(sp.getString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, null))
            ?: DEFAULT_PROVIDER
        set(value) {
            sp.edit().putString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, value.key).apply()
        }

    fun profile(id: ProviderId): ProviderProfile {
        val descriptor = ChatProviderFactory.descriptor(id)
        val modelId = string(id, Constant.SAVED_PROVIDER_SUFFIX_MODEL)
        return when (descriptor.category) {
            ProviderCategory.CLOUD -> CloudProviderProfile(
                providerId = id,
                endpoint = string(id, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL),
                apiKey = string(id, Constant.SAVED_PROVIDER_SUFFIX_KEY),
                modelId = modelId,
                streaming = boolean(id, Constant.SAVED_PROVIDER_SUFFIX_STREAMING, true),
                temperature = floatOrNull(id, Constant.SAVED_PROVIDER_SUFFIX_TEMPERATURE),
                maxTokens = intOrNull(id, Constant.SAVED_PROVIDER_SUFFIX_MAX_TOKENS),
            )
            ProviderCategory.LOCAL_NETWORK -> LocalNetworkProviderProfile(
                endpoint = string(id, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL),
                authToken = string(id, Constant.SAVED_PROVIDER_SUFFIX_KEY),
                modelId = modelId,
                streaming = boolean(id, Constant.SAVED_PROVIDER_SUFFIX_STREAMING, true),
                temperature = floatOrNull(id, Constant.SAVED_PROVIDER_SUFFIX_TEMPERATURE),
                maxTokens = intOrNull(id, Constant.SAVED_PROVIDER_SUFFIX_MAX_TOKENS),
                timeoutSeconds = string(id, Constant.SAVED_PROVIDER_SUFFIX_TIMEOUT)
                    .toLongOrNull()
                    ?.coerceIn(
                        LocalNetworkProviderProfile.MIN_TIMEOUT_SECONDS,
                        LocalNetworkProviderProfile.MAX_TIMEOUT_SECONDS,
                    )
                    ?: ProviderConfig.DEFAULT_TIMEOUT_SECONDS,
            )
            ProviderCategory.EMBEDDED_LOCAL -> EmbeddedLocalProviderProfile(
                modelPath = string(id, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL),
                runtimeType = string(id, Constant.SAVED_PROVIDER_SUFFIX_RUNTIME),
                contextLength = intOrNull(id, Constant.SAVED_PROVIDER_SUFFIX_CONTEXT_LENGTH),
                modelId = modelId,
            )
        }
    }

    fun profiles(): Map<ProviderId, ProviderProfile> =
        ProviderId.entries.associateWith(::profile)

    fun saveAll(activeId: ProviderId, profiles: Collection<ProviderProfile>) {
        val editor = sp.edit().putString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, activeId.key)
        profiles.forEach { profile -> editor.write(profile) }
        editor.apply()
    }

    fun selection(id: ProviderId = activeProviderId): InferenceSelection =
        profile(id).toSelection()

    /** 兼容现有调用点；新代码优先持有不可变的 [InferenceSelection]。 */
    fun config(id: ProviderId = activeProviderId): ProviderConfig =
        selection(id).providerConfig

    private fun migrateIfNeeded() {
        if (sp.getInt(Constant.SAVED_PROVIDER_CONFIG_VERSION, 0) >= CONFIG_VERSION) return

        val legacyKey = sp.getString(Constant.SAVED_CHAT_KEY, null)
        val migration = legacyMigrationValues(
            existingKeys = sp.all.keys,
            legacyKey = legacyKey,
            legacyProxyUrl = sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null),
            useLegacyProxy = sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false),
        )

        sp.edit().apply {
            migration.forEach { (key, value) -> putString(key, value) }
            putBoolean(Constant.SAVED_PROVIDER_MIGRATED, true)
            putInt(Constant.SAVED_PROVIDER_CONFIG_VERSION, CONFIG_VERSION)
            commit()
        }
    }

    private fun SharedPreferences.Editor.write(profile: ProviderProfile) {
        fun put(suffix: String, value: String) {
            putString(keyOf(profile.providerId, suffix), value)
        }

        put(Constant.SAVED_PROVIDER_SUFFIX_MODEL, profile.modelId.trim())
        when (profile) {
            is CloudProviderProfile -> {
                put(Constant.SAVED_PROVIDER_SUFFIX_KEY, profile.apiKey.trim())
                put(Constant.SAVED_PROVIDER_SUFFIX_BASE_URL, profile.endpoint.trim())
                writeRemote(profile.providerId, profile.streaming, profile.temperature, profile.maxTokens)
            }
            is LocalNetworkProviderProfile -> {
                put(Constant.SAVED_PROVIDER_SUFFIX_KEY, profile.authToken.trim())
                put(Constant.SAVED_PROVIDER_SUFFIX_BASE_URL, profile.endpoint.trim())
                put(Constant.SAVED_PROVIDER_SUFFIX_TIMEOUT, profile.timeoutSeconds.toString())
                writeRemote(profile.providerId, profile.streaming, profile.temperature, profile.maxTokens)
            }
            is EmbeddedLocalProviderProfile -> {
                put(Constant.SAVED_PROVIDER_SUFFIX_BASE_URL, profile.modelPath.trim())
                put(Constant.SAVED_PROVIDER_SUFFIX_RUNTIME, profile.runtimeType.trim())
                put(
                    Constant.SAVED_PROVIDER_SUFFIX_CONTEXT_LENGTH,
                    profile.contextLength?.toString().orEmpty(),
                )
            }
        }
    }

    private fun SharedPreferences.Editor.writeRemote(
        id: ProviderId,
        streaming: Boolean,
        temperature: Float?,
        maxTokens: Int?,
    ) {
        putBoolean(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_STREAMING), streaming)
        putString(
            keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_TEMPERATURE),
            temperature?.toString().orEmpty(),
        )
        putString(
            keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_MAX_TOKENS),
            maxTokens?.toString().orEmpty(),
        )
    }

    private fun string(id: ProviderId, suffix: String): String =
        sp.getString(keyOf(id, suffix), null).orEmpty()

    private fun boolean(id: ProviderId, suffix: String, fallback: Boolean): Boolean =
        sp.getBoolean(keyOf(id, suffix), fallback)

    private fun floatOrNull(id: ProviderId, suffix: String): Float? =
        string(id, suffix).toFloatOrNull()

    private fun intOrNull(id: ProviderId, suffix: String): Int? =
        string(id, suffix).toIntOrNull()

    private fun keyOf(id: ProviderId, suffix: String) =
        Constant.SAVED_PROVIDER_PREFIX + id.key + suffix

    companion object {
        val DEFAULT_PROVIDER = ProviderId.OPENAI_COMPAT
        const val CONFIG_VERSION = 2

        fun normalizeBaseUrl(url: String): String =
            url.trim().let { if (it.endsWith("/")) it else "$it/" }

        internal fun legacyMigrationValues(
            existingKeys: Set<String>,
            legacyKey: String?,
            legacyProxyUrl: String?,
            useLegacyProxy: Boolean,
        ): Map<String, String> {
            val values = mutableMapOf<String, String>()
            val keyTarget = Constant.SAVED_PROVIDER_PREFIX +
                ProviderId.OPENAI_COMPAT.key +
                Constant.SAVED_PROVIDER_SUFFIX_KEY
            val urlTarget = Constant.SAVED_PROVIDER_PREFIX +
                ProviderId.OPENAI_COMPAT.key +
                Constant.SAVED_PROVIDER_SUFFIX_BASE_URL

            if (!legacyKey.isNullOrBlank() && keyTarget !in existingKeys) {
                values[keyTarget] = legacyKey
            }
            if (useLegacyProxy && !legacyProxyUrl.isNullOrBlank() && urlTarget !in existingKeys) {
                values[urlTarget] = normalizeBaseUrl(legacyProxyUrl)
            }
            if (!legacyKey.isNullOrBlank() &&
                Constant.SAVED_ACTIVE_CHAT_PROVIDER !in existingKeys
            ) {
                values[Constant.SAVED_ACTIVE_CHAT_PROVIDER] = ProviderId.OPENAI_COMPAT.key
            }
            return values
        }
    }
}
