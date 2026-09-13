package com.chatwaifu.mobile.ui.setting

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ChatError
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.mobile.data.chat.CloudProviderProfile
import com.chatwaifu.mobile.data.chat.EmbeddedLocalProviderProfile
import com.chatwaifu.mobile.data.chat.LocalNetworkProviderProfile
import com.chatwaifu.mobile.data.chat.ProviderProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModelSettingsStateTest {

    @Test
    fun editingProviderDoesNotChangeCurrentModelUntilExplicitSelection() {
        val state = ModelSettingsState(ProviderId.OPENAI_COMPAT, profiles())

        state.selectCategory(ProviderCategory.LOCAL_NETWORK)
        assertEquals(ProviderId.OPENAI_COMPAT, state.activeProvider)
        assertEquals(ProviderId.LOCAL_NETWORK, state.editorProvider)

        state.updateProfile(
            (state.profileOf() as LocalNetworkProviderProfile).copy(
                endpoint = "http://192.168.1.20:8080/v1/"
            )
        )
        state.selectActiveProvider(ProviderId.LOCAL_NETWORK)
        assertEquals(ProviderId.LOCAL_NETWORK, state.activeProvider)
    }

    @Test
    fun unimplementedEmbeddedProviderCannotBecomeCurrent() {
        val state = ModelSettingsState(ProviderId.OPENAI_COMPAT, profiles())

        state.selectActiveProvider(ProviderId.LOCAL)

        assertEquals(ProviderId.OPENAI_COMPAT, state.activeProvider)
    }

    @Test
    fun localNetworkWithoutEndpointCannotBecomeCurrent() {
        val state = ModelSettingsState(ProviderId.OPENAI_COMPAT, profiles())

        state.selectActiveProvider(ProviderId.LOCAL_NETWORK)

        assertEquals(ProviderId.OPENAI_COMPAT, state.activeProvider)
    }

    @Test
    fun providerDraftsRemainSeparateWhileSwitchingCategories() {
        val state = ModelSettingsState(ProviderId.OPENAI_COMPAT, profiles())
        val cloud = state.profileOf() as CloudProviderProfile
        state.updateProfile(cloud.copy(modelId = "cloud-draft"))
        state.selectCategory(ProviderCategory.LOCAL_NETWORK)
        val local = state.profileOf() as LocalNetworkProviderProfile
        state.updateProfile(local.copy(modelId = "local-draft"))
        state.editProvider(ProviderId.OPENAI_COMPAT)

        assertEquals("cloud-draft", state.profileOf().modelId)
        assertNotEquals(
            state.profileOf(ProviderId.OPENAI_COMPAT).modelId,
            state.profileOf(ProviderId.LOCAL_NETWORK).modelId,
        )
    }

    @Test
    fun connectionFailuresMapToStableUiCategories() {
        assertEquals(ConnectionTestStatus.AUTH_ERROR, mapConnectionFailure(ChatError.Auth()))
        assertEquals(
            ConnectionTestStatus.MODEL_NOT_FOUND,
            mapConnectionFailure(ChatError.ModelNotFound()),
        )
        assertEquals(ConnectionTestStatus.NETWORK_ERROR, mapConnectionFailure(ChatError.Timeout()))
        assertEquals(
            ConnectionTestStatus.ENDPOINT_INCOMPATIBLE,
            mapConnectionFailure(ChatError.ServerError(500)),
        )
        assertEquals(ConnectionTestStatus.UNKNOWN_ERROR, mapConnectionFailure(IllegalStateException()))
    }

    private fun profiles(): Map<ProviderId, ProviderProfile> =
        ProviderId.entries.associateWith { id ->
            when (ChatProviderFactory.descriptor(id).category) {
                ProviderCategory.CLOUD -> CloudProviderProfile(providerId = id)
                ProviderCategory.LOCAL_NETWORK -> LocalNetworkProviderProfile()
                ProviderCategory.EMBEDDED_LOCAL -> EmbeddedLocalProviderProfile()
            }
        }
}
