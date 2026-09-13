package com.chatwaifu.mobile.data.chat

import com.chatwaifu.chat.ChatProviderFactory
import com.chatwaifu.chat.core.ProviderCategory
import com.chatwaifu.chat.core.ProviderConfig
import com.chatwaifu.chat.core.ProviderId
import com.chatwaifu.chat.core.ProviderSettingField
import com.chatwaifu.mobile.data.Constant
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderProfileTest {

    @Test
    fun cloudProfileBuildsRuntimeSelection() {
        val selection = CloudProviderProfile(
            providerId = ProviderId.OPENAI_COMPAT,
            endpoint = "https://example.test/v1",
            apiKey = "cloud-secret",
            modelId = "cloud-model",
            streaming = false,
            temperature = 0.4f,
            maxTokens = 256,
        ).toSelection()

        assertEquals(ProviderId.OPENAI_COMPAT, selection.providerId)
        assertEquals("https://example.test/v1/", selection.providerConfig.baseUrl)
        assertEquals("cloud-secret", selection.providerConfig.apiKey)
        assertEquals("cloud-model", selection.sessionOptions.model)
        assertEquals(false, selection.sessionOptions.streamResponse)
        assertEquals(0.4f, selection.sessionOptions.temperature)
        assertEquals(256, selection.sessionOptions.maxOutputTokens)
    }

    @Test
    fun localNetworkAndCloudCredentialsRemainIndependent() {
        val profiles = mapOf(
            ProviderId.OPENAI_COMPAT to CloudProviderProfile(
                providerId = ProviderId.OPENAI_COMPAT,
                endpoint = "https://cloud.test/",
                apiKey = "cloud-secret",
                modelId = "cloud-model",
            ),
            ProviderId.LOCAL_NETWORK to LocalNetworkProviderProfile(
                endpoint = "http://192.168.1.20:8080/v1/",
                authToken = "lan-secret",
                modelId = "local-model",
                timeoutSeconds = 12,
            ),
        )

        val cloud = profiles.getValue(ProviderId.OPENAI_COMPAT).toSelection()
        val local = profiles.getValue(ProviderId.LOCAL_NETWORK).toSelection()
        assertEquals("cloud-secret", cloud.providerConfig.apiKey)
        assertEquals("lan-secret", local.providerConfig.apiKey)
        assertEquals("https://cloud.test/", cloud.providerConfig.baseUrl)
        assertEquals("http://192.168.1.20:8080/v1/", local.providerConfig.baseUrl)
        assertEquals(12, local.providerConfig.timeoutSeconds)
    }

    @Test
    fun localNetworkTimeoutIsClampedToOkHttpSupportedRange() {
        val tooSmall = LocalNetworkProviderProfile(timeoutSeconds = -1).toSelection()
        val tooLarge = LocalNetworkProviderProfile(timeoutSeconds = Long.MAX_VALUE).toSelection()

        assertEquals(
            LocalNetworkProviderProfile.MIN_TIMEOUT_SECONDS,
            tooSmall.providerConfig.timeoutSeconds,
        )
        assertEquals(
            LocalNetworkProviderProfile.MAX_TIMEOUT_SECONDS,
            tooLarge.providerConfig.timeoutSeconds,
        )
    }

    @Test
    fun embeddedPlaceholderCanRoundTripWithoutCredentials() {
        val original = EmbeddedLocalProviderProfile(
            modelPath = "/models/waifu.mnn",
            runtimeType = "MNN",
            contextLength = 4096,
            modelId = "waifu-local",
        )
        val restored = Gson().fromJson(Gson().toJson(original), EmbeddedLocalProviderProfile::class.java)

        assertEquals(original, restored)
        assertEquals("/models/waifu.mnn", restored.toSelection().providerConfig.localModelPath)
        assertNull(restored.toSelection().providerConfig.apiKey)
    }

    @Test
    fun credentialsAreExcludedFromDebugAndSerializationOutput() {
        val secret = "never-print-this"
        val endpoint = "https://example.test/v1/"
        val profile = CloudProviderProfile(
            providerId = ProviderId.OPENAI_COMPAT,
            endpoint = endpoint,
            apiKey = secret,
        )
        val config = ProviderConfig(
            apiKey = secret,
            baseUrl = endpoint,
            extraHeaders = mapOf("Authorization" to secret),
        )

        listOf(profile.toString(), config.toString(), Gson().toJson(profile), Gson().toJson(config))
            .forEach { output -> assertFalse(output.contains(secret)) }
    }

    @Test
    fun descriptorsSeparateProviderCategoriesAndSettings() {
        val cloud = ChatProviderFactory.descriptor(ProviderId.OPENAI_RESPONSES)
        val localNetwork = ChatProviderFactory.descriptor(ProviderId.LOCAL_NETWORK)
        val embedded = ChatProviderFactory.descriptor(ProviderId.LOCAL)

        assertEquals(ProviderCategory.CLOUD, cloud.category)
        assertTrue(cloud.capabilities.requiresApiKey)
        assertEquals(ProviderCategory.LOCAL_NETWORK, localNetwork.category)
        assertTrue(localNetwork.capabilities.remote)
        assertFalse(localNetwork.capabilities.embedded)
        assertTrue(ProviderSettingField.CONNECTION_TIMEOUT in localNetwork.settingFields)
        assertEquals(ProviderCategory.EMBEDDED_LOCAL, embedded.category)
        assertTrue(embedded.capabilities.embedded)
        assertFalse(embedded.implemented)
    }

    @Test
    fun legacyMigrationPreservesExistingProviderValuesAndIsRepeatable() {
        val keyTarget = Constant.SAVED_PROVIDER_PREFIX +
            ProviderId.OPENAI_COMPAT.key +
            Constant.SAVED_PROVIDER_SUFFIX_KEY
        val urlTarget = Constant.SAVED_PROVIDER_PREFIX +
            ProviderId.OPENAI_COMPAT.key +
            Constant.SAVED_PROVIDER_SUFFIX_BASE_URL
        val existing = setOf(keyTarget)

        val first = ChatProviderSettings.legacyMigrationValues(
            existingKeys = existing,
            legacyKey = "old-key",
            legacyProxyUrl = "https://proxy.test/v1",
            useLegacyProxy = true,
        )
        val second = ChatProviderSettings.legacyMigrationValues(
            existingKeys = existing + first.keys,
            legacyKey = "old-key",
            legacyProxyUrl = "https://proxy.test/v1",
            useLegacyProxy = true,
        )

        assertFalse(keyTarget in first)
        assertEquals("https://proxy.test/v1/", first[urlTarget])
        assertEquals(ProviderId.OPENAI_COMPAT.key, first[Constant.SAVED_ACTIVE_CHAT_PROVIDER])
        assertTrue(second.isEmpty())
    }
}
