package com.chatwaifu.mobile.data.chat

import android.content.Context
import android.content.SharedPreferences
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.data.Constant

/**
 * Description: 聊天基座配置的读写，SharedPreferences ↔ [ProviderConfig]。
 *
 * 这一层是 app 侧和 `ChatCore` 的唯一配置桥梁：ChatCore 不认识 SharedPreferences，
 * app 不关心各 provider 的默认地址。
 *
 * Author: Voine
 * Date: 2026/8/6
 */
class ChatProviderSettings(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences(Constant.SAVED_STORE, Context.MODE_PRIVATE)

    init {
        migrateLegacyIfNeeded()
    }

    var activeProviderId: ProviderId
        get() = ProviderId.fromKey(sp.getString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, null))
            ?: DEFAULT_PROVIDER
        set(value) {
            sp.edit().putString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, value.key).apply()
        }

    fun form(id: ProviderId): ProviderForm = ProviderForm(
        apiKey = sp.getString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_KEY), null).orEmpty(),
        baseUrl = sp.getString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL), null).orEmpty(),
        model = sp.getString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_MODEL), null).orEmpty(),
    )

    fun save(id: ProviderId, form: ProviderForm) {
        sp.edit().apply {
            // 空串按「没配置」存，读的时候会 fallback 到 provider 默认值
            putString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_KEY), form.apiKey.trim())
            putString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL), form.baseUrl.trim())
            putString(keyOf(id, Constant.SAVED_PROVIDER_SUFFIX_MODEL), form.model.trim())
            apply()
        }
    }

    /** 组装出可以直接交给 [ChatProviderFactory] 的配置。 */
    fun config(id: ProviderId = activeProviderId): ProviderConfig {
        val form = form(id)
        return ProviderConfig(
            apiKey = form.apiKey.takeIf { it.isNotBlank() },
            baseUrl = form.baseUrl.takeIf { it.isNotBlank() }?.let { normalizeBaseUrl(it) },
            model = form.model.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 老配置搬到新结构，只做一次。
     *
     * 老的行为等价于「OpenAI 兼容 + 可选代理地址」，所以搬到
     * [ProviderId.OPENAI_COMPAT] 并把它设为当前基座 —— 升级后不用重填、行为不变。
     */
    private fun migrateLegacyIfNeeded() {
        if (sp.getBoolean(Constant.SAVED_PROVIDER_MIGRATED, false)) return

        val legacyKey = sp.getString(Constant.SAVED_CHAT_KEY, null)
        val legacyProxyUrl = sp.getString(Constant.SAVED_USE_CHATGPT_PROXY_URL, null)
            ?.takeIf { sp.getBoolean(Constant.SAVED_USE_CHATGPT_PROXY, false) }

        sp.edit().apply {
            if (!legacyKey.isNullOrBlank()) {
                putString(
                    keyOf(ProviderId.OPENAI_COMPAT, Constant.SAVED_PROVIDER_SUFFIX_KEY),
                    legacyKey,
                )
            }
            if (!legacyProxyUrl.isNullOrBlank()) {
                putString(
                    keyOf(ProviderId.OPENAI_COMPAT, Constant.SAVED_PROVIDER_SUFFIX_BASE_URL),
                    normalizeBaseUrl(legacyProxyUrl),
                )
            }
            if (!legacyKey.isNullOrBlank()) {
                putString(Constant.SAVED_ACTIVE_CHAT_PROVIDER, ProviderId.OPENAI_COMPAT.key)
            }
            putBoolean(Constant.SAVED_PROVIDER_MIGRATED, true)
            apply()
        }
    }

    private fun keyOf(id: ProviderId, suffix: String) =
        Constant.SAVED_PROVIDER_PREFIX + id.key + suffix

    companion object {
        /**
         * 默认选 OpenAI 兼容而不是 Responses：它不强制要 key，接本地服务也是这一条，
         * 新用户第一次进来更容易跑起来。
         */
        val DEFAULT_PROVIDER = ProviderId.OPENAI_COMPAT

        /** Retrofit / OkHttp 要求 baseUrl 以 `/` 结尾，用户手填时经常漏。 */
        fun normalizeBaseUrl(url: String): String =
            url.trim().let { if (it.endsWith("/")) it else "$it/" }
    }
}

/** 设置页里一个基座的可编辑字段。空串表示「用默认值」。 */
data class ProviderForm(
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
)
