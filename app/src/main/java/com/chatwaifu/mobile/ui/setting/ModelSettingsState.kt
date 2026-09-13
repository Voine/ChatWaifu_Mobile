package com.chatwaifu.mobile.ui.setting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.data.chat.ProviderProfile

enum class ConnectionTestStatus {
    IDLE,
    TESTING,
    SUCCESS,
    NETWORK_ERROR,
    AUTH_ERROR,
    MODEL_NOT_FOUND,
    ENDPOINT_INCOMPATIBLE,
    UNKNOWN_ERROR,
    UNAVAILABLE,
}

class ModelSettingsState(
    activeProvider: ProviderId,
    profiles: Map<ProviderId, ProviderProfile>,
) {
    var activeProvider by mutableStateOf(activeProvider)
        private set
    var editorProvider by mutableStateOf(activeProvider)
        private set
    var selectedCategory by mutableStateOf(ChatProviderFactory.descriptor(activeProvider).category)
        private set
    var advancedExpanded by mutableStateOf(false)
    var keyVisible by mutableStateOf(false)
    var connectionStatus by mutableStateOf(ConnectionTestStatus.IDLE)
    var connectionDetail by mutableStateOf<String?>(null)

    private val profiles = mutableStateMapOf<ProviderId, ProviderProfile>().apply {
        putAll(profiles)
    }

    fun profileOf(id: ProviderId = editorProvider): ProviderProfile =
        checkNotNull(profiles[id]) { "missing provider profile: $id" }

    fun allProfiles(): Map<ProviderId, ProviderProfile> = profiles.toMap()

    fun selectCategory(category: ProviderCategory) {
        selectedCategory = category
        editorProvider = ChatProviderFactory.descriptors()
            .first { it.category == category }
            .id
        resetTransientState()
    }

    fun editProvider(id: ProviderId) {
        editorProvider = id
        selectedCategory = ChatProviderFactory.descriptor(id).category
        resetTransientState()
    }

    fun canActivate(id: ProviderId): Boolean {
        if (!ChatProviderFactory.descriptor(id).implemented) return false
        val profile = profileOf(id)
        return profile !is com.chatwaifu.mobile.data.chat.LocalNetworkProviderProfile ||
            profile.endpoint.isNotBlank()
    }

    fun selectActiveProvider(id: ProviderId) {
        if (canActivate(id)) activeProvider = id
    }

    fun updateProfile(profile: ProviderProfile) {
        require(profile.providerId == editorProvider)
        profiles[profile.providerId] = profile
        connectionStatus = ConnectionTestStatus.IDLE
        connectionDetail = null
    }

    private fun resetTransientState() {
        advancedExpanded = false
        keyVisible = false
        connectionStatus = ConnectionTestStatus.IDLE
        connectionDetail = null
    }
}
