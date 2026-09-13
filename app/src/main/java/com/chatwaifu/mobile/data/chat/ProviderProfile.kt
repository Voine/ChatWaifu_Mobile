package com.chatwaifu.mobile.data.chat

import com.chatwaifu.chat.core.ChatOptions
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId

sealed interface ProviderProfile {
    val providerId: ProviderId
    val modelId: String

    fun toSelection(): InferenceSelection
}

data class CloudProviderProfile(
    override val providerId: ProviderId,
    val endpoint: String = "",
    @Transient
    val apiKey: String = "",
    override val modelId: String = "",
    val streaming: Boolean = true,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
) : ProviderProfile {
    override fun toSelection(): InferenceSelection = remoteSelection(
        credential = apiKey,
        timeoutSeconds = ProviderConfig.DEFAULT_TIMEOUT_SECONDS,
    )

    override fun toString(): String =
        "CloudProviderProfile(providerId=$providerId, endpoint=<configured>, apiKey=******, " +
            "modelId=$modelId, streaming=$streaming, temperature=$temperature, " +
            "maxTokens=$maxTokens)"
}

data class LocalNetworkProviderProfile(
    override val providerId: ProviderId = ProviderId.LOCAL_NETWORK,
    val endpoint: String = "",
    @Transient
    val authToken: String = "",
    override val modelId: String = "",
    val streaming: Boolean = true,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val timeoutSeconds: Long = ProviderConfig.DEFAULT_TIMEOUT_SECONDS,
) : ProviderProfile {
    override fun toSelection(): InferenceSelection = remoteSelection(
        credential = authToken,
        timeoutSeconds = timeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
    )

    override fun toString(): String =
        "LocalNetworkProviderProfile(providerId=$providerId, endpoint=<configured>, " +
            "authToken=******, modelId=$modelId, streaming=$streaming, " +
            "temperature=$temperature, maxTokens=$maxTokens, timeoutSeconds=$timeoutSeconds)"

    companion object {
        const val MIN_TIMEOUT_SECONDS = 1L
        const val MAX_TIMEOUT_SECONDS = 2_147_483L
    }
}

data class EmbeddedLocalProviderProfile(
    override val providerId: ProviderId = ProviderId.LOCAL,
    val modelPath: String = "",
    val runtimeType: String = "",
    val contextLength: Int? = null,
    override val modelId: String = "",
) : ProviderProfile {
    override fun toSelection(): InferenceSelection = InferenceSelection(
        providerId = providerId,
        providerConfig = ProviderConfig(
            model = modelId.ifBlank { null },
            localModelPath = modelPath.ifBlank { null },
        ),
        sessionOptions = ChatOptions(model = modelId.ifBlank { null }),
    )
}

data class InferenceSelection(
    val providerId: ProviderId,
    val providerConfig: ProviderConfig,
    val sessionOptions: ChatOptions,
)

private fun ProviderProfile.remoteSelection(
    credential: String,
    timeoutSeconds: Long,
): InferenceSelection {
    val remote = when (this) {
        is CloudProviderProfile -> RemoteValues(
            endpoint,
            modelId,
            streaming,
            temperature,
            maxTokens,
        )
        is LocalNetworkProviderProfile -> RemoteValues(
            endpoint,
            modelId,
            streaming,
            temperature,
            maxTokens,
        )
        is EmbeddedLocalProviderProfile -> error("embedded profile is not remote")
    }
    return InferenceSelection(
        providerId = providerId,
        providerConfig = ProviderConfig(
            apiKey = credential.ifBlank { null },
            baseUrl = remote.endpoint.ifBlank { null }?.let(ChatProviderSettings::normalizeBaseUrl),
            model = remote.modelId.ifBlank { null },
            timeoutSeconds = timeoutSeconds,
        ),
        sessionOptions = ChatOptions(
            model = remote.modelId.ifBlank { null },
            maxOutputTokens = remote.maxTokens,
            temperature = remote.temperature,
            streamResponse = remote.streaming,
        ),
    )
}

private data class RemoteValues(
    val endpoint: String,
    val modelId: String,
    val streaming: Boolean,
    val temperature: Float?,
    val maxTokens: Int?,
)
